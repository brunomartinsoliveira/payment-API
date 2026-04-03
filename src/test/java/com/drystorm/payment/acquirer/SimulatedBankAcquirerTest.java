package com.drystorm.payment.acquirer;

import com.drystorm.payment.exception.AcquirerUnavailableException;
import com.drystorm.payment.exception.PaymentDeclinedException;
import com.drystorm.payment.service.acquirer.AcquirerGateway;
import com.drystorm.payment.service.acquirer.SimulatedBankAcquirer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulatedBankAcquirer — comportamento de falhas e circuit breaker")
class SimulatedBankAcquirerTest {

    private SimulatedBankAcquirer acquirer;

    @BeforeEach
    void setUp() {
        acquirer = new SimulatedBankAcquirer();
        // Sem falhas aleatórias para tornar o teste determinístico
        ReflectionTestUtils.setField(acquirer, "failureRate", 0.0);
        ReflectionTestUtils.setField(acquirer, "timeoutMs", 2000L);
    }

    @Test
    @DisplayName("Cartão terminando em 4000 deve ser recusado sem retry")
    void charge_declineCard_throwsPaymentDeclinedException() {
        AcquirerGateway.AcquirerRequest req = buildRequest("4000");

        assertThatThrownBy(() -> acquirer.charge(req))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessageContaining("recusado");
    }

    @Test
    @DisplayName("Cartão terminando em 5200 deve ser recusado sem retry")
    void charge_anotherDeclineCard_throwsPaymentDeclinedException() {
        AcquirerGateway.AcquirerRequest req = buildRequest("5200");

        assertThatThrownBy(() -> acquirer.charge(req))
                .isInstanceOf(PaymentDeclinedException.class);
    }

    @Test
    @DisplayName("Com failureRate 1.0 deve sempre lançar AcquirerUnavailableException")
    void charge_alwaysFail_throwsAcquirerUnavailable() {
        ReflectionTestUtils.setField(acquirer, "failureRate", 1.0);
        AcquirerGateway.AcquirerRequest req = buildRequest("1234");

        assertThatThrownBy(() -> acquirer.charge(req))
                .isInstanceOf(AcquirerUnavailableException.class);
    }

    @Test
    @DisplayName("Com failureRate 0.0 e cartão válido deve aprovar")
    void charge_noFailures_returnsApproved() {
        AcquirerGateway.AcquirerRequest req = buildRequest("1234");

        // Pode levantar por saldo insuficiente aleatório — tentamos algumas vezes
        // Em testes de integração reais, usaríamos um seed fixo para o Random
        boolean approved = false;
        for (int i = 0; i < 20; i++) {
            try {
                AcquirerGateway.AcquirerResult result = acquirer.charge(req);
                assertThat(result.status()).isEqualTo("APPROVED");
                assertThat(result.transactionId()).startsWith("ACQ-");
                approved = true;
                break;
            } catch (PaymentDeclinedException ignored) {
                // pode acontecer aleatoriamente (saldo)
            }
        }
        assertThat(approved).as("Deve ter aprovado pelo menos uma das 20 tentativas").isTrue();
    }

    @Test
    @DisplayName("Circuit Breaker deve abrir após múltiplas falhas")
    void circuitBreaker_opensAfterFailures() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .recordExceptions(AcquirerUnavailableException.class)
                .build();

        CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker("test");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Registra 4 falhas (100% → acima do threshold de 50%)
        for (int i = 0; i < 4; i++) {
            try {
                cb.executeSupplier(() -> {
                    throw new AcquirerUnavailableException("falha simulada");
                });
            } catch (AcquirerUnavailableException ignored) {}
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Circuit Breaker deve transicionar para HALF_OPEN após waitDuration")
    void circuitBreaker_transitionsToHalfOpen() throws InterruptedException {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofMillis(100)) // curto para teste
                .recordExceptions(AcquirerUnavailableException.class)
                .build();

        CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker("test2");

        for (int i = 0; i < 4; i++) {
            try {
                cb.executeSupplier(() -> {
                    throw new AcquirerUnavailableException("falha");
                });
            } catch (AcquirerUnavailableException ignored) {}
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(150); // aguarda waitDuration

        // Tenta uma chamada — deve transicionar para HALF_OPEN
        try { cb.executeSupplier(() -> "ok"); } catch (Exception ignored) {}

        assertThat(cb.getState()).isIn(
                CircuitBreaker.State.HALF_OPEN,
                CircuitBreaker.State.CLOSED);
    }

    private AcquirerGateway.AcquirerRequest buildRequest(String lastFour) {
        return new AcquirerGateway.AcquirerRequest(
                "merchant-001",
                new BigDecimal("100.00"),
                "BRL",
                "João Silva",
                lastFour,
                "12/26", "123",
                "VISA",
                null,
                "CREDIT_CARD",
                "idem-" + lastFour
        );
    }
}
