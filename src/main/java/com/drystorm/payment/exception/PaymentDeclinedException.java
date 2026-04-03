package com.drystorm.payment.exception;

/** Pagamento recusado pelo banco — NÃO faz retry (resposta definitiva) */
public class PaymentDeclinedException extends RuntimeException {
    private final String declineCode;
    public PaymentDeclinedException(String message, String declineCode) {
        super(message);
        this.declineCode = declineCode;
    }
    public String getDeclineCode() { return declineCode; }
}
