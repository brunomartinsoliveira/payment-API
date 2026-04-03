package com.drystorm.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "BRL";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "card_holder", length = 100)
    private String cardHolder;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Column(name = "pix_key", length = 100)
    private String pixKey;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "acquirer_txn_id", length = 100)
    private String acquirerTxnId;

    @Column(name = "acquirer_response", columnDefinition = "TEXT")
    private String acquirerResponse;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 5;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PaymentAttempt> attempts = new ArrayList<>();

    // ─── Domain behaviour ──────────────────────────────────────────────────────

    public boolean canRetry() {
        return attemptCount < maxAttempts
                && (status == PaymentStatus.PENDING
                || status == PaymentStatus.PENDING_RETRY
                || status == PaymentStatus.PROCESSING);
    }

    public boolean isTerminal() {
        return status == PaymentStatus.APPROVED
                || status == PaymentStatus.DECLINED
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.CANCELLED;
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }

    public enum PaymentStatus {
        PENDING,         // Recebido, aguardando processamento
        PROCESSING,      // Sendo processado agora
        PENDING_RETRY,   // Falhou temporariamente, aguardando próxima tentativa
        APPROVED,        // Aprovado pelo adquirente ✅
        DECLINED,        // Recusado pelo adquirente (não tenta novamente) ❌
        FAILED,          // Falhou após todas as tentativas ❌
        CANCELLED        // Cancelado manualmente ❌
    }

    public enum PaymentMethod {
        CREDIT_CARD, DEBIT_CARD, PIX
    }
}
