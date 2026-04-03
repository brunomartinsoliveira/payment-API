package com.drystorm.payment.dto.response;

import com.drystorm.payment.entity.Payment;
import com.drystorm.payment.entity.PaymentAttempt;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    private UUID id;
    private String idempotencyKey;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String cardLastFour;
    private String cardBrand;
    private String description;
    private String status;
    private String statusDescription;
    private String acquirerTxnId;
    private String errorMessage;
    private Integer attemptCount;
    private Integer maxAttempts;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime nextRetryAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;

    private List<AttemptSummary> attempts;

    public static PaymentResponse from(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .idempotencyKey(p.getIdempotencyKey())
                .merchantId(p.getMerchantId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .paymentMethod(p.getPaymentMethod().name())
                .cardLastFour(p.getCardLastFour())
                .cardBrand(p.getCardBrand())
                .description(p.getDescription())
                .status(p.getStatus().name())
                .statusDescription(describeStatus(p.getStatus()))
                .acquirerTxnId(p.getAcquirerTxnId())
                .errorMessage(p.getErrorMessage())
                .attemptCount(p.getAttemptCount())
                .maxAttempts(p.getMaxAttempts())
                .nextRetryAt(p.getNextRetryAt())
                .createdAt(p.getCreatedAt())
                .processedAt(p.getProcessedAt())
                .build();
    }

    private static String describeStatus(Payment.PaymentStatus s) {
        return switch (s) {
            case PENDING        -> "Aguardando processamento";
            case PROCESSING     -> "Em processamento";
            case PENDING_RETRY  -> "Aguardando nova tentativa";
            case APPROVED       -> "Aprovado pelo adquirente";
            case DECLINED       -> "Recusado pelo adquirente";
            case FAILED         -> "Falhou após todas as tentativas";
            case CANCELLED      -> "Cancelado";
        };
    }

    // ─── Tentativas ───────────────────────────────────────────────────────────
    @Data @Builder
    public static class AttemptSummary {
        private Integer attemptNumber;
        private String status;
        private String errorCode;
        private String errorMessage;
        private Long durationMs;
        private String circuitBreakerState;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime attemptedAt;

        public static AttemptSummary from(PaymentAttempt a) {
            return AttemptSummary.builder()
                    .attemptNumber(a.getAttemptNumber())
                    .status(a.getStatus().name())
                    .errorCode(a.getErrorCode())
                    .errorMessage(a.getErrorMessage())
                    .durationMs(a.getDurationMs())
                    .circuitBreakerState(a.getCircuitBreakerState())
                    .attemptedAt(a.getAttemptedAt())
                    .build();
        }
    }
}
