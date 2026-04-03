package com.drystorm.payment.dto.request;

import com.drystorm.payment.entity.Payment.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentRequest {

    /**
     * Chave de idempotência — o cliente gera um UUID único por tentativa de pagamento.
     * Se a mesma chave chegar duas vezes, retornamos a resposta da primeira vez.
     */
    @NotBlank(message = "idempotency_key é obrigatório")
    @Size(min = 8, max = 64, message = "idempotency_key deve ter entre 8 e 64 caracteres")
    private String idempotencyKey;

    @NotBlank(message = "merchant_id é obrigatório")
    @Size(max = 50)
    private String merchantId;

    @NotNull(message = "amount é obrigatório")
    @DecimalMin(value = "0.01", message = "amount deve ser maior que zero")
    @Digits(integer = 13, fraction = 2, message = "amount inválido")
    private BigDecimal amount;

    @Size(max = 3)
    private String currency = "BRL";

    @NotNull(message = "payment_method é obrigatório")
    private PaymentMethod paymentMethod;

    @Valid
    private CardDetails card;

    @Valid
    private PixDetails pix;

    @Size(max = 255)
    private String description;

    // ─── Nested DTOs ──────────────────────────────────────────────────────────

    @Data
    public static class CardDetails {
        @NotBlank(message = "card.holder é obrigatório")
        @Size(max = 100)
        private String holder;

        @NotBlank(message = "card.number é obrigatório")
        @Pattern(regexp = "\\d{13,19}", message = "Número do cartão inválido")
        private String number;

        @NotBlank(message = "card.expiry é obrigatório")
        @Pattern(regexp = "^(0[1-9]|1[0-2])/\\d{2}$", message = "Validade no formato MM/AA")
        private String expiry;

        @NotBlank(message = "card.cvv é obrigatório")
        @Pattern(regexp = "\\d{3,4}", message = "CVV inválido")
        private String cvv;

        @Size(max = 20)
        private String brand;
    }

    @Data
    public static class PixDetails {
        @NotBlank(message = "pix.key é obrigatório")
        @Size(max = 100)
        private String key;
    }
}
