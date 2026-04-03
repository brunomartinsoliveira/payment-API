package com.drystorm.payment.exception;

/** Adquirente indisponível — dispara Circuit Breaker e retry */
public class AcquirerUnavailableException extends RuntimeException {
    public AcquirerUnavailableException(String message) { super(message); }
    public AcquirerUnavailableException(String message, Throwable cause) { super(message, cause); }
}
