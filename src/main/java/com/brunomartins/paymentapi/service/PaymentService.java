package com.brunomartins.paymentapi.service;

import com.brunomartins.paymentapi.config.RabbitMQConfig;
import com.brunomartins.paymentapi.dto.PaymentRequest;
import com.brunomartins.paymentapi.dto.PaymentResponse;
import com.brunomartins.paymentapi.entity.Payment;
import com.brunomartins.paymentapi.entity.PaymentStatus;
import com.brunomartins.paymentapi.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;

    public PaymentService(PaymentRepository paymentRepository, RabbitTemplate rabbitTemplate) {
        this.paymentRepository = paymentRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public PaymentResponse create(PaymentRequest request) {
        Payment payment = new Payment();
        payment.setMerchantId(request.merchantId());
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setCardLastDigits(request.cardLastDigits());

        Payment saved = paymentRepository.save(payment);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                saved.getId().toString()
        );

        log.info("Pagamento {} criado e enviado para processamento", saved.getId());
        return PaymentResponse.from(saved);
    }

    public PaymentResponse findById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado: " + id));
        return PaymentResponse.from(payment);
    }

    public List<PaymentResponse> findByMerchantId(String merchantId) {
        return paymentRepository.findByMerchantId(merchantId)
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    public PaymentResponse cancel(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado: " + id));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Apenas pagamentos PENDING podem ser cancelados");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        Payment saved = paymentRepository.save(payment);

        log.info("Pagamento {} cancelado", id);
        return PaymentResponse.from(saved);
    }

    public PaymentResponse retry(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado: " + id));

        if (payment.getStatus() != PaymentStatus.DECLINED) {
            throw new IllegalStateException("Apenas pagamentos DECLINED podem ser reprocessados");
        }

        payment.setStatus(PaymentStatus.PENDING);
        payment.setErrorMessage(null);
        Payment saved = paymentRepository.save(payment);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                saved.getId().toString()
        );

        log.info("Pagamento {} reenviado para processamento", saved.getId());
        return PaymentResponse.from(saved);
    }
}
