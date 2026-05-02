package com.brunomartins.paymentapi;

import com.brunomartins.paymentapi.acquirer.AcquirerResult;
import com.brunomartins.paymentapi.acquirer.SimulatedBankAcquirer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "payment.acquirer.failure-rate=0.0")
class SimulatedBankAcquirerTest {

    @Autowired
    private SimulatedBankAcquirer acquirer;

    @Test
    void deveRecusarCartaoNaListaNegra() {
        AcquirerResult result = acquirer.charge("pagamento-123", "4000");
        assertFalse(result.approved());
        assertNotNull(result.errorMessage());
    }

    @Test
    void deveAprovarCartaoValido() {
        AcquirerResult result = acquirer.charge("pagamento-456", "9999");
        assertTrue(result.approved());
        assertNull(result.errorMessage());
    }

    @Test
    void deveRecusarTodosOsCartoesNaListaNegra() {
        for (String cartao : new String[]{"4000", "5200", "0000"}) {
            AcquirerResult result = acquirer.charge("pagamento-teste", cartao);
            assertFalse(result.approved(), "Cartão " + cartao + " deveria ser recusado");
        }
    }
}
