package it.govpay.rt.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GovpayRtBatchApplicationTest {

    @Test
    void applicationClassExists() {
        GovpayRtBatchApplication app = new GovpayRtBatchApplication();
        assertNotNull(app);
    }

    @Test
    void mainMethodExists() {
        assertDoesNotThrow(() -> {
            // Verifica che il metodo main esista e sia accessibile
            GovpayRtBatchApplication.class.getMethod("main", String[].class);
        });
    }
}
