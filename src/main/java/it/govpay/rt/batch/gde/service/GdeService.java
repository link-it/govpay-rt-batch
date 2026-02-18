package it.govpay.rt.batch.gde.service;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.govpay.common.client.gde.HttpDataHolder;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.gde.AbstractGdeService;
import it.govpay.common.gde.GdeEventInfo;
import it.govpay.common.gde.GdeUtils;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.mapper.EventoRtMapper;
import it.govpay.rt.batch.gde.utils.RtGdeUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for sending RT retrieve events to the GDE microservice.
 * <p>
 * Extends {@link AbstractGdeService} from govpay-common for RestTemplate-based
 * async event sending via ConfigurazioneService.
 * <p>
 * Events include:
 * - GET_RECEIPT: Fetching receipt from pagoPA
 * - paSendRTV2: Sending receipt to GovPay via SOAP
 */
@Slf4j
@Service
public class GdeService extends AbstractGdeService {
    private static final String PLACEHOLDER_ORGANIZATION_FISCAL_CODE = "{organizationfiscalcode}";
    private static final String PLACEHOLDER_IUR                      = "{iur}";
    private static final String PLACEHOLDER_IUV                      = "{iuv}";

    private final EventoRtMapper eventoRtMapper;
    private final ConfigurazioneService configurazioneService;
    private final Jaxb2Marshaller jaxb2Marshaller;

    @Value("${govpay.url}")
    private String govpayUrl;

    public GdeService(ObjectMapper objectMapper,
                      @Qualifier("asyncHttpExecutor") Executor asyncHttpExecutor,
                      ConfigurazioneService configurazioneService,
                      EventoRtMapper eventoRtMapper,
                      Jaxb2Marshaller jaxb2Marshaller) {
        super(objectMapper, asyncHttpExecutor, configurazioneService);
        this.eventoRtMapper = eventoRtMapper;
        this.configurazioneService = configurazioneService;
        this.jaxb2Marshaller = jaxb2Marshaller;
    }

    @Override
    protected String getGdeEndpoint() {
        return configurazioneService.getServizioGDE().getUrl() + "/eventi";
    }

    @Override
    protected NuovoEvento convertToGdeEvent(GdeEventInfo eventInfo) {
        throw new UnsupportedOperationException(
                "GdeService usa sendEventAsync(NuovoEvento) direttamente, non il pattern GdeEventInfo");
    }

    /**
     * Sends an event to GDE asynchronously using the inherited async executor
     * and RestTemplate from ConfigurazioneService.
     *
     * @param nuovoEvento Event to send
     */
    public void sendEventAsync(NuovoEvento nuovoEvento) {
        if (!isAbilitato()) {
            log.debug("Connettore GDE disabilitato, evento {} non inviato", nuovoEvento.getTipoEvento());
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                getGdeRestTemplate().postForEntity(getGdeEndpoint(), nuovoEvento, Void.class);
                log.debug("Evento {} inviato con successo al GDE", nuovoEvento.getTipoEvento());
            } catch (Exception ex) {
                log.warn("Impossibile inviare evento {} al GDE (il batch continua normalmente): {}",
                        nuovoEvento.getTipoEvento(), ex.getMessage());
                log.debug("Dettaglio errore GDE:", ex);
            } finally {
                HttpDataHolder.clear();
            }
        }, this.asyncExecutor);
    }

    /**
     * Records a successful GET_RECEIPT operation.
     *
     * @param rtInfo          rt retrieve information: Organization tax code, IUR, IUV
     * @param responseEntity  HTTP response
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     * @param pagoPABaseUrl   base URL pagoPA (from ConnettoreService)
     */
    public void saveGetReceiptOk(RtRetrieveContext rtInfo, ResponseEntity<?> responseEntity,
                                 OffsetDateTime dataStart, OffsetDateTime dataEnd, String pagoPABaseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = buildReceiptUrl(pagoPABaseUrl, rtInfo);
        NuovoEvento nuovoEvento = eventoRtMapper.createEventoOk(
                rtInfo, Costanti.OPERATION_GET_RECEIPT, transactionId, dataStart, dataEnd);

        eventoRtMapper.setParametriRichiesta(nuovoEvento, url, "GET", GdeUtils.getCapturedRequestHeadersAsGdeHeaders());
        eventoRtMapper.setParametriRisposta(nuovoEvento, dataEnd, responseEntity, null);

        setResponsePayload(nuovoEvento, responseEntity, null);

        sendEventAsync(nuovoEvento);
    }

    /**
     * Records a failed GET_RECEIPT operation.
     *
     * @param rtInfo          rt retrieve information: Organization tax code, IUR, IUV
     * @param responseEntity  HTTP response
     * @param exception       the exception that occurred
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     * @param pagoPABaseUrl   base URL pagoPA (from ConnettoreService)
     */
    public void saveGetReceiptKo(RtRetrieveContext rtInfo, ResponseEntity<?> responseEntity, RestClientException exception,
                                 OffsetDateTime dataStart, OffsetDateTime dataEnd, String pagoPABaseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = buildReceiptUrl(pagoPABaseUrl, rtInfo);
        NuovoEvento nuovoEvento = eventoRtMapper.createEventoKo(
                rtInfo, Costanti.OPERATION_GET_RECEIPT, transactionId, dataStart, dataEnd, null, exception);

        eventoRtMapper.setParametriRichiesta(nuovoEvento, url, "GET", GdeUtils.getCapturedRequestHeadersAsGdeHeaders());
        eventoRtMapper.setParametriRisposta(nuovoEvento, dataEnd, null, exception);

        setResponsePayload(nuovoEvento, responseEntity, exception);

        sendEventAsync(nuovoEvento);
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

        NuovoEvento nuovoEvento = eventoRtMapper.createEventoOk(
                rtInfo, Costanti.OPERATION_SEND_RECEIPT, transactionId, dataStart, dataEnd);

        eventoRtMapper.setParametriRichiesta(nuovoEvento, govpayUrl, "POST", Collections.emptyList());
        eventoRtMapper.setParametriRispostaSoap(nuovoEvento, dataEnd, response);

        RtGdeUtils.serializzaPayloadSoap(this.jaxb2Marshaller, nuovoEvento, request, response, null);

        sendEventAsync(nuovoEvento);
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

        NuovoEvento nuovoEvento = eventoRtMapper.createEventoKoSoap(
                rtInfo, Costanti.OPERATION_SEND_RECEIPT, transactionId, dataStart, dataEnd, exception);

        eventoRtMapper.setParametriRichiesta(nuovoEvento, govpayUrl, "POST", Collections.emptyList());
        eventoRtMapper.setParametriRispostaSoapKo(nuovoEvento, dataEnd, exception);

        RtGdeUtils.serializzaPayloadSoap(this.jaxb2Marshaller, nuovoEvento, request, null, exception);

        sendEventAsync(nuovoEvento);
    }

    /**
     * Sets the response payload on the event using the common GdeUtils.extractResponsePayload().
     */
    private void setResponsePayload(NuovoEvento nuovoEvento, ResponseEntity<?> responseEntity,
                                     RestClientException exception) {
        if (nuovoEvento.getParametriRisposta() != null) {
            nuovoEvento.getParametriRisposta().setPayload(
                extractResponsePayload(responseEntity, exception));
        }
    }

    /**
     * Builds the URL for receipt operations using GdeUtils.buildUrl().
     */
    private String buildReceiptUrl(String pagoPABaseUrl, RtRetrieveContext rtInfo) {
        return GdeUtils.buildUrl(pagoPABaseUrl, Costanti.PATH_GET_RECEIPT,
            Map.of(
                PLACEHOLDER_ORGANIZATION_FISCAL_CODE, rtInfo.getTaxCode(),
                PLACEHOLDER_IUR, rtInfo.getIur(),
                PLACEHOLDER_IUV, rtInfo.getIuv()
            ),
            null);
    }
}
