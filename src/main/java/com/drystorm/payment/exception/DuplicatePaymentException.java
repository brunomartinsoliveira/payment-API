package com.drystorm.payment.exception;

public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException(String idempotencyKey) {
        super("Pagamento duplicado para idempotency_key: " + idempotencyKey);
    }
}
