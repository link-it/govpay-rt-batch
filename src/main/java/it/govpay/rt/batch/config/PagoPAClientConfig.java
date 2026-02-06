package it.govpay.rt.batch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import it.govpay.rt.client.ApiClient;
import it.govpay.rt.client.api.PaymentReceiptsRestApisApi;

/**
 * Configuration for pagoPA REST API client.
 */
@Configuration
public class PagoPAClientConfig {

    @Bean
    public PaymentReceiptsRestApisApi paymentReceiptsRestApisApi(
            RestTemplate rtApiRestTemplate,
            PagoPAProperties pagoPAProperties) {
        ApiClient apiClient = new ApiClient(rtApiRestTemplate);
        apiClient.setBasePath(pagoPAProperties.getBaseUrl());
        apiClient.setDebugging(pagoPAProperties.isDebugging());
        return new PaymentReceiptsRestApisApi(apiClient);
    }
}
