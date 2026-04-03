package com.drystorm.payment.service;

import com.drystorm.payment.dto.request.PaymentRequest;
import com.drystorm.payment.dto.response.PaymentResponse;
import com.drystorm.payment.entity.OutboxEvent;
import com.drystorm.payment.entity.Payment;
import com.drystorm.payment.entity.PaymentAttempt;
import com.drystorm.payment.exception.DuplicatePaymentException;
import com.drystorm.payment.exception.PaymentNotFoundException;
import com.drystorm.payment.repository.OutboxEventRepository;
import com.drystorm.payment.repository.PaymentAttemptRepository;
import com.drystorm.payment.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Cria um novo pagamento e registra o evento no Outbox — TUDO na mesma transação.
     *
     * O Outbox Pattern garante que, mesmo se o RabbitMQ estiver fora,
     * o evento será publicado eventualmente pelo OutboxPublisher.
     */
    @Transactional
    public PaymentResponse create(PaymentRequest req) {
        // 1. Idempotência: se a chave já existe, retorna o resultado anterior
        paymentRepository.findByIdempotencyKey(req.getIdempotencyKey())
                .ifPresent(existing -> {
                    log.info("Pagamento duplicado detectado idempotencyKey={}", req.getIdempotencyKey());
                    throw new DuplicatePaymentException(req.getIdempotencyKey());
                });

        // 2. Persistir o pagamento
        Payment payment = buildPayment(req);
        paymentRepository.save(payment);

        // 3. Persistir o evento no Outbox (MESMA transação — Outbox Pattern)
        OutboxEvent event = buildOutboxEvent(payment, OutboxEvent.EVENT_PAYMENT_CREATED,
                "payments.process");
        outboxRepository.save(event);

        log.info("Pagamento criado id={} merchant={} amount={} — evento no outbox id={}",
                payment.getId(), payment.getMerchantId(), payment.getAmount(), event.getId());

        return PaymentResponse.from(payment);
    }

    /**
     * Busca um pagamento por ID, incluindo histórico de tentativas.
     */
    @Transactional(readOnly = true)
    public PaymentResponse findById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        List<PaymentAttempt> attempts = attemptRepository
                .findByPaymentIdOrderByAttemptNumberAsc(id);

        PaymentResponse response = PaymentResponse.from(payment);
        response.setAttempts(attempts.stream()
                .map(PaymentResponse.AttemptSummary::from)
                .collect(Collectors.toList()));

        return response;
    }

    /**
     * Lista pagamentos de um merchant.
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> findByMerchant(String merchantId) {
        return paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)
                .stream().map(PaymentResponse::from).collect(Collectors.toList());
    }

    /**
     * Cancela um pagamento que ainda não foi processado.
     */
    @Transactional
    public PaymentResponse cancel(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        if (payment.isTerminal()) {
            throw new IllegalStateException(
                    "Pagamento já está no estado terminal: " + payment.getStatus());
        }

        payment.setStatus(Payment.PaymentStatus.CANCELLED);
        paymentRepository.save(payment);

        log.info("Pagamento cancelado id={}", id);
        return PaymentResponse.from(payment);
    }

    /**
     * Força uma nova tentativa manual via endpoint admin.
     */
    @Transactional
    public PaymentResponse manualRetry(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        if (!payment.canRetry()) {
            throw new IllegalStateException(
                    "Pagamento não pode ser reprocessado. Status: " + payment.getStatus());
        }

        payment.setStatus(Payment.PaymentStatus.PENDING_RETRY);
        payment.setNextRetryAt(null);
        paymentRepository.save(payment);

        // Cria novo evento no outbox para reprocessamento
        OutboxEvent event = buildOutboxEvent(payment, OutboxEvent.EVENT_PAYMENT_RETRY,
                "payments.process");
        outboxRepository.save(event);

        log.info("Retry manual agendado para pagamento id={}", id);
        return PaymentResponse.from(payment);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Payment buildPayment(PaymentRequest req) {
        Payment.PaymentBuilder builder = Payment.builder()
                .idempotencyKey(req.getIdempotencyKey())
                .merchantId(req.getMerchantId())
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : "BRL")
                .paymentMethod(req.getPaymentMethod())
                .description(req.getDescription());

        if (req.getCard() != null) {
            String num = req.getCard().getNumber();
            builder.cardHolder(req.getCard().getHolder())
                   .cardLastFour(num.substring(Math.max(0, num.length() - 4)))
                   .cardBrand(req.getCard().getBrand());
        }

        if (req.getPix() != null) {
            builder.pixKey(req.getPix().getKey());
        }

        return builder.build();
    }

    private OutboxEvent buildOutboxEvent(Payment payment, String eventType, String routingKey) {
        try {
            String payload = objectMapper.writeValueAsString(PaymentResponse.from(payment));
            return OutboxEvent.builder()
                    .aggregateType(OutboxEvent.AGGREGATE_PAYMENT)
                    .aggregateId(payment.getId())
                    .eventType(eventType)
                    .payload(payload)
                    .routingKey(routingKey)
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar evento do outbox", e);
        }
    }
}
