package com.drystorm.payment.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/circuit-breaker")
@RequiredArgsConstructor
@Tag(name = "Circuit Breaker", description = "Monitoramento do estado do Circuit Breaker")
public class CircuitBreakerController {

    private final CircuitBreakerRegistry registry;

    @GetMapping
    @Operation(summary = "Status do Circuit Breaker",
               description = "Retorna estado atual (CLOSED/OPEN/HALF_OPEN), métricas e configurações.")
    public ResponseEntity<Map<String, Object>> status() {
        CircuitBreaker cb = registry.circuitBreaker("bankAcquirer");
        CircuitBreaker.Metrics m = cb.getMetrics();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("name", cb.getName());
        response.put("state", cb.getState().name());
        response.put("stateDescription", describeState(cb.getState()));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("failureRate", String.format("%.1f%%", m.getFailureRate()));
        metrics.put("slowCallRate", String.format("%.1f%%", m.getSlowCallRate()));
        metrics.put("numberOfCalls", m.getNumberOfBufferedCalls());
        metrics.put("numberOfSuccessful", m.getNumberOfSuccessfulCalls());
        metrics.put("numberOfFailed", m.getNumberOfFailedCalls());
        metrics.put("numberOfSlowCalls", m.getNumberOfSlowCalls());
        metrics.put("numberOfNotPermitted", m.getNumberOfNotPermittedCalls());
        response.put("metrics", metrics);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("failureRateThreshold", cb.getCircuitBreakerConfig().getFailureRateThreshold() + "%");
        config.put("slidingWindowSize", cb.getCircuitBreakerConfig().getSlidingWindowSize());
        config.put("waitDurationInOpenState",
                cb.getCircuitBreakerConfig().getWaitDurationInOpenState().toSeconds() + "s");
        config.put("permittedCallsInHalfOpen",
                cb.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState());
        response.put("config", config);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset")
    @Operation(summary = "Resetar Circuit Breaker",
               description = "Força o estado para CLOSED e zera os contadores (uso em testes).")
    public ResponseEntity<Map<String, String>> reset() {
        CircuitBreaker cb = registry.circuitBreaker("bankAcquirer");
        cb.reset();
        return ResponseEntity.ok(Map.of(
                "message", "Circuit Breaker resetado para CLOSED",
                "state", cb.getState().name()
        ));
    }

    @PostMapping("/open")
    @Operation(summary = "Forçar Circuit Breaker OPEN (testes)")
    public ResponseEntity<Map<String, String>> forceOpen() {
        CircuitBreaker cb = registry.circuitBreaker("bankAcquirer");
        cb.transitionToOpenState();
        return ResponseEntity.ok(Map.of(
                "message", "Circuit Breaker forçado para OPEN",
                "state", cb.getState().name()
        ));
    }

    private String describeState(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED     -> "Operando normalmente — chamadas passam livremente";
            case OPEN       -> "Circuito aberto — chamadas bloqueadas, aguardando recuperação";
            case HALF_OPEN  -> "Testando recuperação — chamadas limitadas para verificar disponibilidade";
            case DISABLED   -> "Desabilitado — todas as chamadas passam sem monitoramento";
            case FORCED_OPEN-> "Forçado aberto — todas as chamadas bloqueadas manualmente";
            case METRICS_ONLY -> "Apenas métricas — sem proteção ativa";
        };
    }
}
