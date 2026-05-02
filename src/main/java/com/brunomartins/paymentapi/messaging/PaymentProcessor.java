package com.brunomartins.paymentapi.messaging;

import com.brunomartins.paymentapi.acquirer.AcquirerResult;
import com.brunomartins.paymentapi.acquirer.SimulatedBankAcquirer;
import com.brunomartins.paymentapi.config.RabbitMQConfig;
import com.brunomartins.paymentapi.entity.Payment;
import com.brunomartins.paymentapi.entity.PaymentStatus;
import com.brunomartins.paymentapi.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentRepository paymentRepository;
    private final SimulatedBankAcquirer acquirer;

    public PaymentProcessor(PaymentRepository paymentRepository, SimulatedBankAcquirer acquirer) {
        this.paymentRepository = paymentRepository;
        this.acquirer = acquirer;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void process(String paymentId) {
        log.info("Processando pagamento: {}", paymentId);

        Payment payment = paymentRepository.findById(UUID.fromString(paymentId)).orElse(null);

        if (payment == null) {
            log.error("Pagamento não encontrado: {}", paymentId);
            return;
        }

        try {
            AcquirerResult result = acquirer.charge(paymentId, payment.getCardLastDigits());

            if (result.approved()) {
                payment.setStatus(PaymentStatus.APPROVED);
            } else {
                payment.setStatus(PaymentStatus.DECLINED);
                payment.setErrorMessage(result.errorMessage());
            }

        } catch (Exception e) {
            log.error("Erro ao processar pagamento {}: {}", paymentId, e.getMessage());
            payment.setStatus(PaymentStatus.DECLINED);
            payment.setErrorMessage("Falha no processamento: " + e.getMessage());
        }

        paymentRepository.save(payment);
        log.info("Pagamento {} finalizado com status: {}", paymentId, payment.getStatus());
    }
}
