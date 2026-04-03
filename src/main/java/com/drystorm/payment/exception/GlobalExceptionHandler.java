package com.drystorm.payment.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicatePaymentException ex) {
        return status(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", ex.getMessage());
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    public ResponseEntity<ErrorResponse> handleDeclined(PaymentDeclinedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.builder()
                        .code("PAYMENT_DECLINED")
                        .message(ex.getMessage())
                        .detail(ex.getDeclineCode())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(AcquirerUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAcquirer(AcquirerUnavailableException ex) {
        log.warn("Adquirente indisponível: {}", ex.getMessage());
        return status(HttpStatus.SERVICE_UNAVAILABLE, "ACQUIRER_UNAVAILABLE",
                "Adquirente temporariamente indisponível. O pagamento será reprocessado.");
    }

    // Circuit Breaker aberto — retorna 503 imediatamente
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(CallNotPermittedException ex) {
        log.warn("Circuit breaker OPEN: chamada bloqueada para {}", ex.getCausingCircuitBreakerName());
        return status(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_BREAKER_OPEN",
                "Serviço de pagamento temporariamente indisponível. Tente novamente em instantes.");
    }

    // Rate Limiter excedido
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RequestNotPermitted ex) {
        return status(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                "Muitas requisições. Aguarde antes de tentar novamente.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getAllErrors().stream()
                .filter(e -> e instanceof FieldError)
                .map(e -> (FieldError) e)
                .collect(Collectors.toMap(FieldError::getField, fe ->
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "inválido"));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("VALIDATION_ERROR")
                        .message("Erro de validação nos campos")
                        .fields(fields)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Erro inesperado: {}", ex.getMessage(), ex);
        return status(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Erro interno. Por favor, tente novamente.");
    }

    private ResponseEntity<ErrorResponse> status(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder().code(code).message(message)
                        .timestamp(LocalDateTime.now()).build());
    }

    @Data @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorResponse {
        private String code;
        private String message;
        private String detail;
        private Map<String, String> fields;
        private LocalDateTime timestamp;
    }
}
