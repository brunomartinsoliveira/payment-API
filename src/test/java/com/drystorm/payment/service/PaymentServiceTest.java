package com.drystorm.payment.service;

import com.drystorm.payment.dto.request.PaymentRequest;
import com.drystorm.payment.dto.response.PaymentResponse;
import com.drystorm.payment.entity.OutboxEvent;
import com.drystorm.payment.entity.Payment;
import com.drystorm.payment.exception.DuplicatePaymentException;
import com.drystorm.payment.exception.PaymentNotFoundException;
import com.drystorm.payment.repository.OutboxEventRepository;
import com.drystorm.payment.repository.PaymentAttemptRepository;
import com.drystorm.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService — testes unitários")
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentAttemptRepository attemptRepository;
    @Mock OutboxEventRepository outboxRepository;
    @Spy  ObjectMapper objectMapper;

    @InjectMocks PaymentService paymentService;

    private PaymentRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new PaymentRequest();
        validRequest.setIdempotencyKey("idem-key-abc-123");
        validRequest.setMerchantId("merchant-001");
        validRequest.setAmount(new BigDecimal("150.00"));
        validRequest.setPaymentMethod(Payment.PaymentMethod.CREDIT_CARD);

        var card = new PaymentRequest.CardDetails();
        card.setHolder("João Silva");
        card.setNumber("4111111111111111");
        card.setExpiry("12/26");
        card.setCvv("123");
        card.setBrand("VISA");
        validRequest.setCard(card);
    }

    @Test
    @DisplayName("create — deve persistir payment e evento outbox na mesma operação")
    void create_persistsPaymentAndOutboxEvent() {
        given(paymentRepository.findByIdempotencyKey(any())).willReturn(Optional.empty());
        given(paymentRepository.save(any())).willAnswer(inv -> {
            Payment p = inv.getArgument(0);
            // Simula o UUID gerado pelo banco
            try { var f = Payment.class.getDeclaredField("id"); f.setAccessible(true); f.set(p, UUID.randomUUID()); }
            catch (Exception ignored) {}
            return p;
        });
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.create(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getMerchantId()).isEqualTo("merchant-001");
        assertThat(response.getAmount()).isEqualByComparingTo("150.00");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getCardLastFour()).isEqualTo("1111");

        then(paymentRepository).should().save(any(Payment.class));
        then(outboxRepository).should().save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("create — deve lançar DuplicatePaymentException para idempotency_key repetida")
    void create_duplicateIdempotencyKey_throwsException() {
        Payment existing = Payment.builder()
                .idempotencyKey("idem-key-abc-123")
                .merchantId("merchant-001")
                .amount(BigDecimal.TEN)
                .build();
        given(paymentRepository.findByIdempotencyKey("idem-key-abc-123"))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.create(validRequest))
                .isInstanceOf(DuplicatePaymentException.class)
                .hasMessageContaining("idem-key-abc-123");

        then(paymentRepository).should(never()).save(any());
        then(outboxRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("findById — deve lançar PaymentNotFoundException para ID inexistente")
    void findById_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        given(paymentRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findById(unknownId))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("cancel — deve lançar IllegalStateException para pagamento aprovado")
    void cancel_terminalPayment_throwsException() {
        UUID id = UUID.randomUUID();
        Payment approved = Payment.builder()
                .status(Payment.PaymentStatus.APPROVED).build();
        given(paymentRepository.findById(id)).willReturn(Optional.of(approved));

        assertThatThrownBy(() -> paymentService.cancel(id))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("manualRetry — deve criar evento outbox e resetar status para PENDING_RETRY")
    void manualRetry_createsOutboxEventAndResetsStatus() {
        UUID id = UUID.randomUUID();
        Payment payment = Payment.builder()
                .status(Payment.PaymentStatus.FAILED)
                .attemptCount(2)
                .maxAttempts(5)
                .build();
        try { var f = Payment.class.getDeclaredField("id"); f.setAccessible(true); f.set(payment, id); }
        catch (Exception ignored) {}

        given(paymentRepository.findById(id)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.manualRetry(id);

        assertThat(response.getStatus()).isEqualTo("PENDING_RETRY");
        then(outboxRepository).should().save(any(OutboxEvent.class));
    }
}
