package com.drystorm.payment.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID id) {
        super("Pagamento não encontrado: " + id);
    }
}
