package it.govpay.rt.batch.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;

import jakarta.xml.bind.JAXBElement;

import it.gov.pagopa.pagopa_api.pa.pafornode.ObjectFactory;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;

public class GovpayClient extends WebServiceGatewaySupport {

	private static final String HEADER_ON_BEHALF_OF = "GOVPAY-ON-BEHALF-OF";

	private Logger log = LoggerFactory.getLogger(GovpayClient.class);
	private final ObjectFactory objectFactory = new ObjectFactory();

	/**
	 * @param onBehalfOf principal dell'operatore che ha richiesto il recupero
	 *                   puntuale, valorizzato nell'header {@value #HEADER_ON_BEHALF_OF};
	 *                   {@code null} per la scansione automatica (nessun operatore
	 *                   associato) o quando l'id non e' risolvibile.
	 */
	@SuppressWarnings("unchecked")
	public PaSendRTV2Response sendReceipt(PaSendRTV2Request receiptToSend, String onBehalfOf) {
		if (receiptToSend == null)
			return null;
		log.debug("Notifica la ricevuta a govpay: {}", receiptToSend.getReceipt().getReceiptId());
		WebServiceTemplate template = getWebServiceTemplate();
		Object result = template.marshalSendAndReceive(objectFactory.createPaSendRTV2Request(receiptToSend),
				onBehalfOfCallback(onBehalfOf));
		if (result instanceof JAXBElement<?> jaxbElement) {
			return (PaSendRTV2Response) jaxbElement.getValue();
		}
		return (PaSendRTV2Response) result;
	}

	/**
	 * A differenza dell'auth basic ({@link AuthorizationHeaderInserter}, fissa
	 * per l'intero client e registrata una volta sugli interceptor) il principal
	 * cambia a ogni chiamata: non e' un {@code ClientInterceptor} condiviso ma
	 * un callback costruito per la singola invocazione.
	 */
	private static WebServiceMessageCallback onBehalfOfCallback(String onBehalfOf) {
		return message -> {
			new SoapActionCallback("paSendRTV2").doWithMessage(message);
			if (onBehalfOf == null) {
				return;
			}
			TransportContext context = TransportContextHolder.getTransportContext();
			WebServiceConnection connection = context.getConnection();
			if (connection instanceof HeadersAwareSenderWebServiceConnection httpConnection) {
				httpConnection.addRequestHeader(HEADER_ON_BEHALF_OF, onBehalfOf);
			}
		};
	}
}
