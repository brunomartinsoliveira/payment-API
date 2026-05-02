package com.brunomartins.paymentapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(

        @NotBlank(message = "merchantId é obrigatório")
        String merchantId,

        @NotNull(message = "amount é obrigatório")
        @DecimalMin(value = "0.01", message = "amount deve ser maior que zero")
        BigDecimal amount,

        @NotBlank(message = "currency é obrigatória")
        String currency,

        @NotBlank(message = "paymentMethod é obrigatório")
        String paymentMethod,

        String cardLastDigits
) {}
