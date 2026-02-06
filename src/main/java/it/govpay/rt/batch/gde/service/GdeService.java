package it.govpay.rt.batch.gde.service;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.govpay.gde.client.model.NuovoEvento;
import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.config.PagoPAProperties;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.mapper.EventoRtMapper;
import it.govpay.rt.batch.gde.utils.GdeUtils;
import it.govpay.gde.client.api.EventiApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for sending RT retrieve events to the GDE microservice.
 * <p>
 * This service tracks all FDR batch operations by sending events asynchronously
 * to GDE for monitoring, auditing, and debugging purposes.
 * <p>
 * Events include:
 * - IOrganizationsController_getAllPublishedFlows: Fetching list of published flows
 * - IOrganizationsController_getSinglePublishedFlow: Fetching single flow details
 * - PROCESS_FLOW: Processing flow data (internal operation)
 * - SAVE_FLOW: Saving flow data (internal operation)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "govpay.gde.enabled", havingValue = "true", matchIfMissing = false)
public class GdeService {
    private static final String PLACEHOLDER_ORGANIZATION_FISCAL_CODE = "{organizationfiscalcode}";
    private static final String PLACEHOLDER_IUR                      = "{iur}";
    private static final String PLACEHOLDER_IUV                      = "{iuv}";
	
	private final EventiApi eventiApi;
    private final EventoRtMapper eventoFdrMapper;
    private final ObjectMapper objectMapper;
    private final Jaxb2Marshaller jaxb2Marshaller;
    private final PagoPAProperties pagoPAProperties;
    
    @Value("${govpay.gde.enabled:false}")
    private Boolean gdeEnabled;

    @Value("${govpay.url}")
    private String govpayUrl;
    
    /**
     * Sends an event to GDE asynchronously.
     * <p>
     * If GDE is disabled or the event fails to send, the error is logged
     * but does not interrupt the batch processing.
     *
     * @param nuovoEvento Event to send
     */
    public void inviaEvento(NuovoEvento nuovoEvento) {
        if (Boolean.FALSE.equals(gdeEnabled)) {
            log.debug("GDE disabilitato, salto evento: {}", nuovoEvento.getTipoEvento());
            return;
        }

        if (eventiApi == null) {
            log.debug("EventiApi non configurato, salto evento: {}", nuovoEvento.getTipoEvento());
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                eventiApi.addEvento(nuovoEvento);
                log.debug("Evento {} inviato con successo al GDE", nuovoEvento.getTipoEvento());
            } catch (Exception ex) {
                // Log come warning per non interrompere il batch
                // L'invio eventi GDE è best-effort: se fallisce, il batch continua
                log.warn("Impossibile inviare evento {} al GDE (il batch continua normalmente): {}",
                        nuovoEvento.getTipoEvento(), ex.getMessage());
                log.debug("Dettaglio errore GDE:", ex);
            }
        });
    }

    /**
     * Records a successful GET_RECEIPT operation.
     *
     * @param rtInfo          rt retrieve information: Organization tax code, IUR, IUV
     * @param responseEntity  HTTP response
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     */
    public void saveGetReceiptOk(RtRetrieveContext rtInfo, ResponseEntity<?> responseEntity,
                                 OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        String transactionId = UUID.randomUUID().toString();
        String url = pagoPAProperties.getBaseUrl() + Costanti.PATH_GET_RECEIPT
                .replace(PLACEHOLDER_ORGANIZATION_FISCAL_CODE, rtInfo.getTaxCode())
                .replace(PLACEHOLDER_IUR, rtInfo.getIur())
                .replace(PLACEHOLDER_IUV, rtInfo.getIuv());
        NuovoEvento nuovoEvento = eventoFdrMapper.createEventoOk(
                rtInfo, Costanti.PATH_GET_RECEIPT, transactionId, dataStart, dataEnd);

        eventoFdrMapper.setParametriRichiesta(nuovoEvento, url, "GET", GdeUtils.getCapturedRequestHeaders());
        eventoFdrMapper.setParametriRisposta(nuovoEvento, dataEnd, responseEntity, null);

        GdeUtils.serializzaPayload(this.objectMapper, nuovoEvento, responseEntity, null);

        inviaEvento(nuovoEvento);
    }

    /**
     * Records a failed GET_RECEIPT operation.
     *
     * @param rtInfo          rt retrieve information: Organization tax code, IUR, IUV
     * @param responseEntity  HTTP response
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     */
    public void saveGetReceiptKo(RtRetrieveContext rtInfo, ResponseEntity<?> responseEntity, RestClientException exception,
                                 OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        String transactionId = UUID.randomUUID().toString();
        String url = pagoPAProperties.getBaseUrl() + Costanti.PATH_GET_RECEIPT
                .replace(PLACEHOLDER_ORGANIZATION_FISCAL_CODE, rtInfo.getTaxCode())
                .replace(PLACEHOLDER_IUR, rtInfo.getIur())
                .replace(PLACEHOLDER_IUV, rtInfo.getIuv());
        NuovoEvento nuovoEvento = eventoFdrMapper.createEventoKo(
                rtInfo, Costanti.PATH_GET_RECEIPT, transactionId, dataStart, dataEnd, null, exception);

        eventoFdrMapper.setParametriRichiesta(nuovoEvento, url, "GET", GdeUtils.getCapturedRequestHeaders());
        eventoFdrMapper.setParametriRisposta(nuovoEvento, dataEnd, null, exception);

        GdeUtils.serializzaPayload(this.objectMapper, nuovoEvento, responseEntity, exception);

        inviaEvento(nuovoEvento);
    }

    /**
     * Records a successful SEND_RECEIPT (paSendRTV2) operation to GovPay.
     *
     * @param rtInfo          rt retrieve information: Organization tax code, IUR, IUV
     * @param request         SOAP request sent
     * @param response        SOAP response received
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     */
    public void saveSendReceiptOk(RtRetrieveContext rtInfo, PaSendRTV2Request request, PaSendRTV2Response response,
                                  OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        String transactionId = UUID.randomUUID().toString();

        NuovoEvento nuovoEvento = eventoFdrMapper.createEventoOk(
                rtInfo, Costanti.OPERATION_SEND_RECEIPT, transactionId, dataStart, dataEnd);

        eventoFdrMapper.setParametriRichiesta(nuovoEvento, govpayUrl, "POST", Collections.emptyList());
        eventoFdrMapper.setParametriRispostaSoap(nuovoEvento, dataEnd, response);

        GdeUtils.serializzaPayloadSoap(this.jaxb2Marshaller, nuovoEvento, request, response, null);

        inviaEvento(nuovoEvento);
    }

    /**
     * Records a failed SEND_RECEIPT (paSendRTV2) operation to GovPay.
     *
     * @param rtInfo          rt retrieve information: Organization tax code, IUR, IUV
     * @param request         SOAP request sent
     * @param exception       Exception occurred
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     */
    public void saveSendReceiptKo(RtRetrieveContext rtInfo, PaSendRTV2Request request, Exception exception,
                                  OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        String transactionId = UUID.randomUUID().toString();

        NuovoEvento nuovoEvento = eventoFdrMapper.createEventoKoSoap(
                rtInfo, Costanti.OPERATION_SEND_RECEIPT, transactionId, dataStart, dataEnd, exception);

        eventoFdrMapper.setParametriRichiesta(nuovoEvento, govpayUrl, "POST", Collections.emptyList());
        eventoFdrMapper.setParametriRispostaSoapKo(nuovoEvento, dataEnd, exception);

        GdeUtils.serializzaPayloadSoap(this.jaxb2Marshaller, nuovoEvento, request, null, exception);

        inviaEvento(nuovoEvento);
    }
}
