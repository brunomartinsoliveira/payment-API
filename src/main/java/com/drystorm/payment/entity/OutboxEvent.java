package com.drystorm.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox Pattern — garante que a publicação no RabbitMQ seja consistente com
 * a transação do banco de dados.
 *
 * Fluxo:
 *  1. PaymentService salva Payment + OutboxEvent na MESMA transação.
 *  2. OutboxPublisher (scheduled) lê eventos PENDING e publica no RabbitMQ.
 *  3. Após publicação com sucesso → marca como PUBLISHED.
 *  4. Em caso de falha → incrementa attempts e tenta novamente.
 *
 * Isso elimina o dual-write problem: nunca teremos um pagamento salvo sem
 * o evento correspondente sendo eventualmente publicado.
 */
@Entity
@Table(name = "outbox_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public enum OutboxStatus {
        PENDING, PUBLISHED, FAILED
    }

    // ─── Constants para event types ─────────────────────────────────────────
    public static final String AGGREGATE_PAYMENT = "PAYMENT";
    public static final String EVENT_PAYMENT_CREATED = "PAYMENT_CREATED";
    public static final String EVENT_PAYMENT_RETRY   = "PAYMENT_RETRY";
}
