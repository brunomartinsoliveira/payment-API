package com.brunomartins.paymentapi.controller;

import com.brunomartins.paymentapi.dto.PaymentRequest;
import com.brunomartins.paymentapi.dto.PaymentResponse;
import com.brunomartins.paymentapi.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Endpoints para gerenciamento de pagamentos")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Criar um novo pagamento")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pagamento por ID")
    public ResponseEntity<PaymentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.findById(id));
    }

    @GetMapping
    @Operation(summary = "Listar pagamentos por merchant")
    public ResponseEntity<List<PaymentResponse>> findByMerchant(@RequestParam String merchantId) {
        return ResponseEntity.ok(paymentService.findByMerchantId(merchantId));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancelar um pagamento pendente")
    public ResponseEntity<PaymentResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.cancel(id));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Reprocessar um pagamento recusado")
    public ResponseEntity<PaymentResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.retry(id));
    }
}
