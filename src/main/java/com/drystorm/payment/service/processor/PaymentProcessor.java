package com.drystorm.payment.service.processor;

import com.drystorm.payment.config.RabbitMQConfig;
import com.drystorm.payment.dto.response.PaymentResponse;
import com.drystorm.payment.entity.Payment;
import com.drystorm.payment.entity.PaymentAttempt;
import com.drystorm.payment.exception.AcquirerUnavailableException;
import com.drystorm.payment.exception.PaymentDeclinedException;
import com.drystorm.payment.repository.PaymentAttemptRepository;
import com.drystorm.payment.repository.PaymentRepository;
import com.drystorm.payment.service.acquirer.AcquirerGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumidor RabbitMQ — processa pagamentos da fila payments.process.
 *
 * Fluxo de sucesso:
 *   1. Recebe mensagem → chama adquirente (Circuit Breaker protege)
 *   2. Payment → APPROVED → ACK
 *
 * Fluxo de falha temporária (AcquirerUnavailableException):
 *   1. Registra tentativa com estado do CB
 *   2. Republica na fila de retry TTL adequada (Exponential Backoff)
 *   3. ACK (remove da fila original)
 *
 * Fluxo de recusa definitiva (PaymentDeclinedException):
 *   1. Payment → DECLINED → ACK (sem retry)
 *
 * Esgotamento de tentativas:
 *   1. Payment → FAILED → NACK → DLQ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessor {

    private final PaymentRepository        paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final AcquirerGateway          acquirerGateway;
    private final CircuitBreakerRegistry   cbRegistry;
    private final RabbitTemplate           rabbitTemplate;
    private final ObjectMapper             objectMapper;

    @RabbitListener(
        queues = RabbitMQConfig.QUEUE_PROCESS,
        containerFactory = "rabbitListenerContainerFactory"
    )
    @Transactional
    public void process(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();

        // ── Deserializar payload ──────────────────────────────────────────────
        PaymentResponse payload;
        try {
            payload = objectMapper.readValue(message.getBody(), PaymentResponse.class);
        } catch (Exception e) {
            log.error("[PROCESSOR] Mensagem inválida — descartando: {}", e.getMessage());
            channel.basicNack(tag, false, false);
            return;
        }

        UUID paymentId = payload.getId();
        Payment payment = paymentRepository.findById(paymentId).orElse(null);

        if (payment == null) {
            log.warn("[PROCESSOR] Pagamento não encontrado id={} — descartando", paymentId);
            channel.basicAck(tag, false);
            return;
        }

        // ── Idempotência: ignora se já está em estado terminal ────────────────
        if (payment.isTerminal()) {
            log.info("[PROCESSOR] Já processado id={} status={} — ignorando",
                    paymentId, payment.getStatus());
            channel.basicAck(tag, false);
            return;
        }

        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        payment.incrementAttempt();
        paymentRepository.save(payment);

        long start   = System.currentTimeMillis();
        String cbState = circuitBreakerState();

        try {
            // ── Chama o adquirente (protegido por CB, Retry, RateLimiter, Bulkhead) ─
            AcquirerGateway.AcquirerResult result = acquirerGateway.charge(buildRequest(payment));
            long ms = System.currentTimeMillis() - start;

            // ✅ Aprovado
            payment.setStatus(Payment.PaymentStatus.APPROVED);
            payment.setAcquirerTxnId(result.transactionId());
            payment.setAcquirerResponse(result.responseMessage());
            payment.setProcessedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            recordAttempt(payment, PaymentAttempt.AttemptStatus.SUCCESS,
                    result.transactionId(), null, null, ms, cbState);

            log.info("[PROCESSOR] ✅ APROVADO id={} txnId={} tentativa={}/{}",
                    paymentId, result.transactionId(),
                    payment.getAttemptCount(), payment.getMaxAttempts());

            channel.basicAck(tag, false);

        } catch (PaymentDeclinedException ex) {
            long ms = System.currentTimeMillis() - start;

            // ❌ Recusa definitiva — sem retry
            payment.setStatus(Payment.PaymentStatus.DECLINED);
            payment.setErrorMessage(ex.getMessage());
            paymentRepository.save(payment);

            recordAttempt(payment, PaymentAttempt.AttemptStatus.DECLINED,
                    null, ex.getDeclineCode(), ex.getMessage(), ms, cbState);

            log.warn("[PROCESSOR] ❌ RECUSADO id={} code={}", paymentId, ex.getDeclineCode());
            channel.basicAck(tag, false);

        } catch (AcquirerUnavailableException ex) {
            long ms = System.currentTimeMillis() - start;

            recordAttempt(payment, PaymentAttempt.AttemptStatus.FAILED,
                    null, "ACQUIRER_UNAVAILABLE", ex.getMessage(), ms, cbState);

            if (!payment.canRetry()) {
                // 💀 Esgotou tentativas → FAILED → DLQ
                payment.setStatus(Payment.PaymentStatus.FAILED);
                payment.setErrorMessage("Falhou após " + payment.getAttemptCount() + " tentativas. " +
                        "Último erro: " + ex.getMessage());
                paymentRepository.save(payment);

                log.error("[PROCESSOR] 💀 FALHOU id={} após {} tentativas",
                        paymentId, payment.getAttemptCount());

                channel.basicNack(tag, false, false); // DLQ
                return;
            }

            // ⏳ Agenda retry com Exponential Backoff
            scheduleRetry(payment, ex.getMessage(), tag, channel);

        } catch (Exception ex) {
            log.error("[PROCESSOR] Erro inesperado id={}: {}", paymentId, ex.getMessage(), ex);
            payment.setStatus(Payment.PaymentStatus.PENDING_RETRY);
            paymentRepository.save(payment);
            channel.basicNack(tag, false, false);
        }
    }

    // ── Retry com Exponential Backoff ─────────────────────────────────────────

    private void scheduleRetry(Payment payment, String errorMsg,
                                long tag, Channel channel) throws IOException {
        int idx = Math.min(payment.getAttemptCount() - 1,
                           RabbitMQConfig.RETRY_ROUTING_KEYS.length - 1);

        String retryRK = RabbitMQConfig.RETRY_ROUTING_KEYS[idx];
        long   delayMs = RabbitMQConfig.RETRY_DELAYS_MS[idx];

        payment.setStatus(Payment.PaymentStatus.PENDING_RETRY);
        payment.setNextRetryAt(LocalDateTime.now().plusNanos(delayMs * 1_000_000L));
        payment.setErrorMessage(errorMsg);
        paymentRepository.save(payment);

        try {
            String json = objectMapper.writeValueAsString(PaymentResponse.from(payment));
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, retryRK, json);
        } catch (Exception e) {
            log.error("[PROCESSOR] Falha ao publicar retry id={}: {}", payment.getId(), e.getMessage());
        }

        log.warn("[PROCESSOR] ⏳ Retry agendado id={} tentativa={}/{} fila={} delay={}ms",
                payment.getId(), payment.getAttemptCount(),
                payment.getMaxAttempts(), retryRK, delayMs);

        channel.basicAck(tag, false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void recordAttempt(Payment payment, PaymentAttempt.AttemptStatus status,
                                String txnId, String errCode, String errMsg,
                                long durationMs, String cbState) {
        attemptRepository.save(PaymentAttempt.builder()
                .payment(payment)
                .attemptNumber(payment.getAttemptCount())
                .status(status)
                .acquirerTxnId(txnId)
                .errorCode(errCode)
                .errorMessage(errMsg)
                .durationMs(durationMs)
                .circuitBreakerState(cbState)
                .build());
    }

    private String circuitBreakerState() {
        try {
            return cbRegistry.circuitBreaker("bankAcquirer").getState().name();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private AcquirerGateway.AcquirerRequest buildRequest(Payment payment) {
        return new AcquirerGateway.AcquirerRequest(
                payment.getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getCardHolder(),
                payment.getCardLastFour(),
                null, null,              // expiry/CVV não persistidos (PCI compliance)
                payment.getCardBrand(),
                payment.getPixKey(),
                payment.getPaymentMethod().name(),
                payment.getIdempotencyKey()
        );
    }
}
