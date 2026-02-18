package it.govpay.rt.batch.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.StOutcome;
import it.govpay.rt.batch.client.GovpayClient;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.service.GdeService;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for interacting with Govpay API
 */
@Service
@Slf4j
public class PaForNodeService {

	private final GdeService gdeService;
	private final GovpayClient govpayClient;

	public PaForNodeService(@Autowired(required = false) GdeService gdeService,
							GovpayClient govpayClient) {
		this.gdeService = gdeService;
		this.govpayClient = govpayClient;
	}

	public boolean sendReceipt(RtRetrieveContext rtInfo, PaSendRTV2Request receiptToSend) {
		log.debug("Invio ricevuta recuperata a govpay");

		OffsetDateTime dataStart = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime dataEnd = null;
		PaSendRTV2Response response = null;

		try {
			response = govpayClient.sendReceipt(receiptToSend);
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
			log.debug("Ricevuta risposta da govpay: {}", response.getOutcome());

			if (response.getOutcome().equals(StOutcome.OK)) {
				gdeService.saveSendReceiptOk(rtInfo, receiptToSend, response, dataStart, dataEnd);
				return true;
			} else {
				gdeService.saveSendReceiptKo(rtInfo, receiptToSend,
						new Exception("Outcome KO: " + response.getFault()), dataStart, dataEnd);
				return false;
			}
		} catch (Exception e) {
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
			log.error("Errore durante l'invio della ricevuta a govpay", e);
			gdeService.saveSendReceiptKo(rtInfo, receiptToSend, e, dataStart, dataEnd);
			return false;
		}
	}

}
