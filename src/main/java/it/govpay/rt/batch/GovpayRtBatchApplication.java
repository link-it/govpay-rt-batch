package it.govpay.rt.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for GovPay RT Batch.
 *
 * <p>Estende {@link SpringBootServletInitializer} per il deploy del war (profilo
 * Maven {@code war}) su application server; con il fat jar parte da {@link #main}.</p>
 */
@SpringBootApplication(scanBasePackages = {"it.govpay.rt.batch", "it.govpay.common.client"})
@EntityScan(basePackages = {"it.govpay.rt.batch", "it.govpay.common.client", "it.govpay.common.entity"})
@EnableJpaRepositories(basePackages = {"it.govpay.rt.batch"})
@EnableScheduling
public class GovpayRtBatchApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(GovpayRtBatchApplication.class, args);
    }
}
