package it.govpay.rt.batch.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.client.core.SoapActionCallback;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;

public class GovpayClient extends WebServiceGatewaySupport {

	private Logger log = LoggerFactory.getLogger(GovpayClient.class);

	public PaSendRTV2Response sendReceipt(PaSendRTV2Request receiptToSend) {
		if (receiptToSend == null)
			return null;
		log.debug("Notifica la ricevuta a govpay: {}", receiptToSend.getReceipt().getReceiptId());
		WebServiceTemplate template = getWebServiceTemplate();
        return (PaSendRTV2Response)template.marshalSendAndReceive(receiptToSend, new SoapActionCallback("paSendRTV2"));
	}
}
