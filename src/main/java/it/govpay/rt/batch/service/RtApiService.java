package it.govpay.rt.batch.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.service.GdeService;
import it.govpay.rt.batch.service.mapper.CtReceiptV2Converter;
import it.govpay.rt.client.api.PaymentReceiptsRestApisApi;
import it.govpay.rt.client.model.CtReceiptModelResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for interacting with pagoPA RT API
 */
@Service
@Slf4j
public class RtApiService {

	private final GdeService gdeService;
	private final PaymentReceiptsRestApisApi paymentRtRestApi;

	@Value("${nodeforpa.intermediary_id}")
	String intermediaryId;

	@Value("${nodeforpa.station_id}")
	String stationId;

	public RtApiService(PaymentReceiptsRestApisApi paymentRtRestApi,
						@Autowired(required = false) GdeService gdeService) {
		this.paymentRtRestApi = paymentRtRestApi;
		this.gdeService = gdeService;
	}

	public PaSendRTV2Request retrieveReceipt(RtRetrieveContext rtInfo, CompletableFuture<HttpStatusCode> statusCodeFuture) throws RestClientException {
		log.debug("Recupero ricevuta per l'organizzazione {} con iur {} e iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
		OffsetDateTime dataStart = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime dataEnd = null;
		
		ResponseEntity<CtReceiptModelResponse> response = null;
		try {
			response = paymentRtRestApi.getOrganizationReceiptIuvIurWithHttpInfo(rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv(), null);
			statusCodeFuture.complete(response.getStatusCode());
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
		} catch (RestClientException e) {
			log.error("Failed http call to retrieve missing receipt", e);
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
			gdeService.saveGetReceiptKo(rtInfo, response, e, dataStart, dataEnd);
			throw e;
		}
		
		PaSendRTV2Request ret = null;
		if (response.getStatusCode().equals(HttpStatus.OK)) {
			log.debug("Recuperata ricevuta per l'organizzazione {} con iur {} e iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
			ret = CtReceiptV2Converter.toPaSendRTV2Request(intermediaryId, stationId, rtInfo.getTaxCode(), response.getBody());
			gdeService.saveGetReceiptOk(rtInfo, response, dataStart, dataEnd);
		} else
		if (response.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
			log.error("Receipt not found: taxCode: {} - iur: {} - iuv: {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
			gdeService.saveGetReceiptKo(rtInfo, response, new RestClientException("Receipt not found"), dataStart, dataEnd);
		} else
		if (response.getStatusCode().equals(HttpStatus.TOO_MANY_REQUESTS)) {
			log.warn("Rate limit reached");
			throw new RestClientException("Rate limit reached");
		} else
		if (response.getStatusCode().is5xxServerError()) {
			log.error("Server error to retrieve missing receipt with status code {}", response.getStatusCode());
			throw new RestClientException("Server error to retrieve missing receipt");
		} else {
			log.error("Fail to retrieve missing receipt with status code {}", response.getStatusCode());
			throw new RestClientException("Fail to retrieve missing receipt");
		}
		return ret;
	}

}
