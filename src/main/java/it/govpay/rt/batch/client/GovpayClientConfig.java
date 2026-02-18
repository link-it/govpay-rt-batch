package it.govpay.rt.batch.client;

import java.util.Map;

import jakarta.xml.bind.Marshaller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;

@Configuration
public class GovpayClientConfig  {
	@Value("${govpay.url}")
	String govpayUrl;

	@Value("${govpay.auth-header.key:GOVHUB-CONSUMER-PRINCIPAL}")
	String principalHeaderName;

	@Value("${govpay.auth-header.value}")
	String principalHeaderValue;

	@Bean
	public Jaxb2Marshaller marshaller() {
		Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
		marshaller.setContextPath("it.gov.pagopa.pagopa_api.pa.pafornode");
		marshaller.setMarshallerProperties(Map.of(
			Marshaller.JAXB_FRAGMENT, Boolean.TRUE
		));
		return marshaller;
	}

	@Bean
	public GovpayClient govpayClient(Jaxb2Marshaller marshaller) {
		GovpayClient client = new GovpayClient();
		client.setDefaultUri(govpayUrl);
		client.setMarshaller(marshaller);
		client.setUnmarshaller(marshaller);
		client.setInterceptors(new ClientInterceptor[] { new AuthorizationHeaderInserter(principalHeaderName, principalHeaderValue) });
		return client;
	}
}
