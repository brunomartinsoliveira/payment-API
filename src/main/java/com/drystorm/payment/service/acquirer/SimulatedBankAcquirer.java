package com.drystorm.payment.service.acquirer;

import com.drystorm.payment.exception.AcquirerUnavailableException;
import com.drystorm.payment.exception.PaymentDeclinedException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adquirente simulado — imita um banco real com falhas aleatórias, timeouts
 * e recusas para testar os padrões de resiliência.
 *
 * Ordem de aplicação das anotações Resilience4j:
 *   Bulkhead → RateLimiter → CircuitBreaker → Retry → método
 *
 * O CircuitBreaker monitora as chamadas e abre o circuito quando a taxa de
 * falha ultrapassa o threshold configurado no application.yml.
 * O Retry faz até 3 tentativas com Exponential Backoff ANTES de propagar
 * a exceção para o CircuitBreaker contar como falha.
 */
@Service
@Slf4j
public class SimulatedBankAcquirer implements AcquirerGateway {

    private static final String CB_NAME = "bankAcquirer";

    // Cartões que sempre resultam em recusa definitiva (sem retry)
    private static final Set<String> DECLINE_SUFFIXES = Set.of("4000", "5200", "0000");

    @Value("${app.acquirer.failure-rate:0.3}")
    private double failureRate;

    @Value("${app.acquirer.timeout-ms:2000}")
    private long timeoutMs;

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "circuitOpenFallback")
    @Retry(name = CB_NAME)
    @RateLimiter(name = CB_NAME)
    @Bulkhead(name = CB_NAME)
    public AcquirerResult charge(AcquirerRequest request) {
        log.info("[ACQUIRER] Cobrança merchant={} amount={} {}",
                request.merchantId(), request.amount(), request.currency());

        simulateLatency();
        simulateFailures(request);

        String txnId   = "ACQ-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String authCode = String.format("%06d", ThreadLocalRandom.current().nextInt(999_999));

        log.info("[ACQUIRER] ✅ Aprovado txnId={} authCode={}", txnId, authCode);
        return new AcquirerResult(txnId, authCode, "APPROVED", "00", "Transação aprovada");
    }

    /**
     * Fallback chamado pelo Resilience4j quando o CircuitBreaker está OPEN.
     * A assinatura deve ser idêntica ao método original + o tipo da exceção no final.
     */
    @SuppressWarnings("unused")
    private AcquirerResult circuitOpenFallback(AcquirerRequest request,
                                                CallNotPermittedException ex) {
        log.warn("[ACQUIRER] ⚡ Circuit breaker OPEN — chamada bloqueada merchant={}",
                request.merchantId());
        throw new AcquirerUnavailableException(
                "Adquirente indisponível (circuit breaker aberto). Pagamento será reprocessado.");
    }

    // ─── Simulações ───────────────────────────────────────────────────────────

    private void simulateLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(100, 800));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void simulateFailures(AcquirerRequest request) {
        // 1. Cartão na lista de recusa → declinado definitivamente (sem retry)
        if (request.cardNumber() != null &&
                DECLINE_SUFFIXES.stream().anyMatch(s -> request.cardNumber().endsWith(s))) {
            log.warn("[ACQUIRER] ❌ Cartão recusado (lista negra) number=****{}",
                    request.cardNumber());
            throw new PaymentDeclinedException(
                    "Cartão recusado pelo banco emissor", "CARD_DECLINED");
        }

        // 2. Timeout simulado (10%) → AcquirerUnavailableException → retry + CB
        if (ThreadLocalRandom.current().nextDouble() < 0.10) {
            log.warn("[ACQUIRER] ⏱ Timeout simulado merchant={}", request.merchantId());
            throw new AcquirerUnavailableException(
                    "Timeout na comunicação com o adquirente");
        }

        // 3. Falha aleatória configurável → retry + circuit breaker
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            int code = 500 + ThreadLocalRandom.current().nextInt(10);
            log.warn("[ACQUIRER] 💥 Falha aleatória rate={} code={}", failureRate, code);
            throw new AcquirerUnavailableException(
                    "Erro interno no adquirente. Código: " + code);
        }

        // 4. Saldo insuficiente (10%) → declinado definitivamente (sem retry)
        if (ThreadLocalRandom.current().nextDouble() < 0.10) {
            log.warn("[ACQUIRER] ❌ Saldo insuficiente merchant={}", request.merchantId());
            throw new PaymentDeclinedException(
                    "Saldo insuficiente no cartão", "INSUFFICIENT_FUNDS");
        }
    }
}
