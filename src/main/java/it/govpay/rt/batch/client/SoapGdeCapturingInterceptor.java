package it.govpay.rt.batch.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;

import it.govpay.gde.client.beans.Header;
import lombok.extern.slf4j.Slf4j;

/**
 * Interceptor SOAP che cattura gli headers HTTP di risposta per il GDE.
 * <p>
 * Gli headers di risposta vengono catturati in afterCompletion (eseguito
 * sia per risposte OK che per errori di trasporto come 401, 500, ecc.).
 */
@Slf4j
public class SoapGdeCapturingInterceptor implements ClientInterceptor {

	private static final ThreadLocal<List<Header>> RESPONSE_HEADERS = new ThreadLocal<>();

	@Override
	public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
		RESPONSE_HEADERS.remove();
		return true;
	}

	@Override
	public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
		return true;
	}

	@Override
	public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
		return true;
	}

	@Override
	public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {
		captureResponseHeaders();
	}

	private void captureResponseHeaders() {
		try {
			TransportContext context = TransportContextHolder.getTransportContext();
			if (context == null) return;

			WebServiceConnection connection = context.getConnection();
			if (connection instanceof HeadersAwareSenderWebServiceConnection httpConnection) {
				List<Header> headers = new ArrayList<>();
				Iterator<String> headerNames = httpConnection.getResponseHeaderNames();
				while (headerNames.hasNext()) {
					String name = headerNames.next();
					Iterator<String> values = httpConnection.getResponseHeaders(name);
					while (values.hasNext()) {
						Header header = new Header();
						header.setNome(name);
						header.setValore(values.next());
						headers.add(header);
					}
				}
				RESPONSE_HEADERS.set(headers);
				log.debug("Catturati {} headers dalla risposta SOAP", headers.size());
			}
		} catch (IOException e) {
			log.debug("Errore durante la cattura degli headers SOAP: {}", e.getMessage());
		}
	}

	public static List<Header> getCapturedResponseHeaders() {
		List<Header> headers = RESPONSE_HEADERS.get();
		return headers != null ? headers : new ArrayList<>();
	}

	public static void clear() {
		RESPONSE_HEADERS.remove();
	}
}
