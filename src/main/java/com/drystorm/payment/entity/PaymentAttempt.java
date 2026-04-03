package com.drystorm.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_attempts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Column(name = "acquirer_txn_id", length = 100)
    private String acquirerTxnId;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "circuit_breaker_state", length = 20)
    private String circuitBreakerState;

    @Column(name = "attempted_at", nullable = false)
    @Builder.Default
    private LocalDateTime attemptedAt = LocalDateTime.now();

    public enum AttemptStatus {
        SUCCESS,     // Aprovado
        DECLINED,    // Recusado (resposta definitiva do banco)
        FAILED,      // Erro de comunicação/timeout
        CB_OPEN,     // Circuit breaker estava aberto — chamada bloqueada
        TIMEOUT      // Timeout específico da chamada
    }
}
