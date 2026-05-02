package com.brunomartins.paymentapi.acquirer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.Set;

@Component
public class SimulatedBankAcquirer {

    private static final Logger log = LoggerFactory.getLogger(SimulatedBankAcquirer.class);
    private static final Set<String> DECLINED_CARDS = Set.of("4000", "5200", "0000");

    @Value("${payment.acquirer.failure-rate:0.3}")
    private double failureRate;

    private final Random random = new Random();

    public AcquirerResult charge(String paymentId, String cardLastDigits) {
        if (cardLastDigits != null && DECLINED_CARDS.contains(cardLastDigits)) {
            log.info("Pagamento {} recusado - cartão na lista de recusa", paymentId);
            return AcquirerResult.ofDeclined("Cartão recusado pelo banco emissor");
        }

        if (random.nextDouble() < failureRate) {
            log.warn("Pagamento {} - falha no adquirente (simulado)", paymentId);
            throw new RuntimeException("Falha temporária no adquirente");
        }

        log.info("Pagamento {} aprovado", paymentId);
        return AcquirerResult.ofApproved();
    }
}
