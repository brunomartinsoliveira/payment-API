package com.drystorm.payment.service.acquirer;

import java.math.BigDecimal;

/** Contrato para qualquer adquirente externo (banco, processador de cartão, etc.) */
public interface AcquirerGateway {

    /**
     * Envia uma cobrança para o adquirente.
     *
     * @return resultado da transação com ID gerado pelo adquirente
     * @throws com.drystorm.payment.exception.AcquirerUnavailableException se indisponível (retry)
     * @throws com.drystorm.payment.exception.PaymentDeclinedException se recusado (sem retry)
     */
    AcquirerResult charge(AcquirerRequest request);

    record AcquirerRequest(
            String merchantId,
            BigDecimal amount,
            String currency,
            String cardHolder,
            String cardNumber,   // Apenas últimos 4 dígitos — PCI compliance
            String cardExpiry,
            String cardCvv,
            String cardBrand,
            String pixKey,
            String paymentMethod,
            String idempotencyKey
    ) {}

    record AcquirerResult(
            String transactionId,
            String authorizationCode,
            String status,           // APPROVED, DECLINED
            String responseCode,
            String responseMessage
    ) {}
}
