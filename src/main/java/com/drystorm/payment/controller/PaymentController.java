package com.drystorm.payment.controller;

import com.drystorm.payment.dto.request.PaymentRequest;
import com.drystorm.payment.dto.response.PaymentResponse;
import com.drystorm.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Gateway de Pagamentos com Circuit Breaker e Exponential Backoff")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Cria um novo pagamento. O header Idempotency-Key é obrigatório
     * e garante que a mesma requisição não seja processada duas vezes.
     */
    @PostMapping
    @Operation(summary = "Criar pagamento",
               description = "Recebe um pagamento, persiste e envia para processamento assíncrono via RabbitMQ. " +
                             "Utiliza Outbox Pattern para garantir consistência.")
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response = paymentService.create(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar pagamento",
               description = "Retorna o status atual do pagamento e o histórico completo de tentativas.")
    public ResponseEntity<PaymentResponse> findById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(paymentService.findById(id));
    }

    @GetMapping
    @Operation(summary = "Listar pagamentos por merchant")
    public ResponseEntity<List<PaymentResponse>> findByMerchant(
            @Parameter(description = "ID do merchant") @RequestParam String merchantId) {

        return ResponseEntity.ok(paymentService.findByMerchant(merchantId));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancelar pagamento",
               description = "Cancela um pagamento que ainda não foi processado.")
    public ResponseEntity<PaymentResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.cancel(id));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Forçar nova tentativa",
               description = "Força o reprocessamento manual de um pagamento que falhou.")
    public ResponseEntity<PaymentResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(paymentService.manualRetry(id));
    }
}
