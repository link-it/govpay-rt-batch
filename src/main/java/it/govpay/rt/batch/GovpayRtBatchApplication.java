package it.govpay.rt.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for GovPay RT Batch
 */
@SpringBootApplication(scanBasePackages = {"it.govpay.rt.batch", "it.govpay.common.client"})
@EntityScan(basePackages = {"it.govpay.rt.batch", "it.govpay.common.client", "it.govpay.common.entity"})
@EnableJpaRepositories(basePackages = {"it.govpay.rt.batch"})
@EnableScheduling
public class GovpayRtBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovpayRtBatchApplication.class, args);
    }
}
