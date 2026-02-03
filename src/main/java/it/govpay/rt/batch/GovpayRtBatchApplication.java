package it.govpay.rt.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for GovPay RT Batch
 */
@SpringBootApplication
@EnableScheduling
public class GovpayRtBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovpayRtBatchApplication.class, args);
    }
}
