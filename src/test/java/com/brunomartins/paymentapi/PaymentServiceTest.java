package com.brunomartins.paymentapi;

import com.brunomartins.paymentapi.dto.PaymentRequest;
import com.brunomartins.paymentapi.dto.PaymentResponse;
import com.brunomartins.paymentapi.entity.Payment;
import com.brunomartins.paymentapi.entity.PaymentStatus;
import com.brunomartins.paymentapi.repository.PaymentRepository;
import com.brunomartins.paymentapi.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PaymentService paymentService;

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setMerchantId("merchant-123");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("BRL");
        payment.setPaymentMethod("CARD");
        payment.setCardLastDigits("1234");
        payment.setStatus(PaymentStatus.PENDING);
    }

    @Test
    void deveCriarPagamentoComSucesso() {
        PaymentRequest request = new PaymentRequest(
                "merchant-123", new BigDecimal("100.00"), "BRL", "CARD", "1234"
        );
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentResponse response = paymentService.create(request);

        assertNotNull(response);
        assertEquals("merchant-123", response.merchantId());
        verify(rabbitTemplate, times(1)).convertAndSend(any(), any(), any(String.class));
    }

    @Test
    void deveBuscarPagamentoPorId() {
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.findById(payment.getId());

        assertNotNull(response);
        assertEquals(payment.getId(), response.id());
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoNaoEncontrado() {
        UUID idInexistente = UUID.randomUUID();
        when(paymentRepository.findById(idInexistente)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> paymentService.findById(idInexistente));
    }

    @Test
    void deveCancelarPagamentoPendente() {
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.cancel(payment.getId());

        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    void deveLancarExcecaoAoCancelarPagamentoNaoPendente() {
        payment.setStatus(PaymentStatus.APPROVED);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThrows(IllegalStateException.class, () -> paymentService.cancel(payment.getId()));
    }

    @Test
    void deveReprocessarPagamentoDeclined() {
        payment.setStatus(PaymentStatus.DECLINED);
        payment.setErrorMessage("Falha no processamento");
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.retry(payment.getId());

        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(rabbitTemplate, times(1)).convertAndSend(any(), any(), any(String.class));
    }

    @Test
    void deveLancarExcecaoAoReprocessarPagamentoNaoDeclined() {
        payment.setStatus(PaymentStatus.APPROVED);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThrows(IllegalStateException.class, () -> paymentService.retry(payment.getId()));
    }
}
