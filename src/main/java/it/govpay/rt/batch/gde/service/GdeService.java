package it.govpay.rt.batch.gde.service;

import java.time.OffsetDateTime;
import java.util.List;
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

import tools.jackson.databind.ObjectMapper;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.govpay.common.client.gde.HttpDataHolder;
import it.govpay.common.configurazione.model.GdeInterfaccia;
import it.govpay.common.configurazione.model.Giornale;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.gde.AbstractGdeService;
import it.govpay.common.gde.GdeEventInfo;
import it.govpay.common.gde.GdeUtils;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.client.SoapGdeCapturingInterceptor;
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

    @Override
    protected GdeInterfaccia getConfigurazioneComponente(ComponenteEvento componente, Giornale giornale) {
        if (componente == null || giornale == null) {
            return null;
        }
        return switch (componente) {
            case API_PAGOPA -> giornale.getApiPagoPA();
            case API_ENTE -> giornale.getApiEnte();
            case API_PAGAMENTO -> giornale.getApiPagamento();
            case API_RAGIONERIA -> giornale.getApiRagioneria();
            case API_BACKOFFICE -> giornale.getApiBackoffice();
            case API_PENDENZE -> giornale.getApiPendenze();
            case API_BACKEND_IO -> giornale.getApiBackendIO();
            case API_MAGGIOLI_JPPA -> giornale.getApiMaggioliJPPA();
            default -> null;
        };
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

        appendOperatore(nuovoEvento, rtInfo);
        sendEventAsync(nuovoEvento);
    }

    /**
     * Records a GET_RECEIPT that returned 200 OK ma con una voce priva sia di
     * IBAN sia di MBDAttachment: non e' un errore di trasporto (nessuna
     * {@link RestClientException} disponibile), quindi non riusa
     * {@link #saveGetReceiptKo}, che richiede un'eccezione tipizzata.
     * La RT non viene inviata a paForNode in questo caso: vedi {@code RtApiService}.
     *
     * @param rtInfo          rt retrieve information: Organization tax code, IUR, IUV
     * @param responseEntity  HTTP response (200, contenuto incompleto)
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     * @param pagoPABaseUrl   base URL pagoPA (from ConnettoreService)
     */
    public void saveGetReceiptMbtAllegatoMancante(RtRetrieveContext rtInfo, ResponseEntity<?> responseEntity,
                                                  OffsetDateTime dataStart, OffsetDateTime dataEnd, String pagoPABaseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = buildReceiptUrl(pagoPABaseUrl, rtInfo);
        NuovoEvento nuovoEvento = eventoRtMapper.createEvento(
                rtInfo, Costanti.OPERATION_GET_RECEIPT, transactionId, dataStart, dataEnd);
        nuovoEvento.setEsito(EsitoEvento.KO);
        nuovoEvento.setSottotipoEsito("MBD_ATTACHMENT_MANCANTE");
        nuovoEvento.setDettaglioEsito("Voce priva sia di IBAN sia di MBDAttachment: marca da bollo telematica "
                + "con allegato mancante su pagoPA (bug noto, govpay#843). RT non inviata a paForNode.");

        eventoRtMapper.setParametriRichiesta(nuovoEvento, url, "GET", GdeUtils.getCapturedRequestHeadersAsGdeHeaders());
        eventoRtMapper.setParametriRisposta(nuovoEvento, dataEnd, responseEntity, null);

        setResponsePayload(nuovoEvento, responseEntity, null);

        appendOperatore(nuovoEvento, rtInfo);
        sendEventAsync(nuovoEvento);
    }

    /**
     * Records un tentativo di GET_RECEIPT che non e' mai partito perche' il
     * dominio/stazione/intermediario/connettore non sono censiti o configurati.
     * Nessuna chiamata HTTP tentata: niente {@code responseEntity}
     * ne' {@code pagoPABaseUrl} (e' proprio quello che non si e'
     * risolto), quindi niente parametri di risposta da registrare.
     *
     * @param rtInfo          rt retrieve information: Organization tax code, IUR, IUV
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     * @param dettaglio       messaggio dell'IllegalStateException di RtApiService
     */
    public void saveGetReceiptConfigurazioneMancante(RtRetrieveContext rtInfo, OffsetDateTime dataStart,
                                                      OffsetDateTime dataEnd, String dettaglio) {
        String transactionId = UUID.randomUUID().toString();
        NuovoEvento nuovoEvento = eventoRtMapper.createEvento(
                rtInfo, Costanti.OPERATION_GET_RECEIPT, transactionId, dataStart, dataEnd);
        nuovoEvento.setEsito(EsitoEvento.KO);
        nuovoEvento.setSottotipoEsito("CONFIGURAZIONE_MANCANTE");
        nuovoEvento.setDettaglioEsito(dettaglio);

        eventoRtMapper.setParametriRichiesta(nuovoEvento, Costanti.PATH_GET_RECEIPT, "GET",
                GdeUtils.getCapturedRequestHeadersAsGdeHeaders());

        appendOperatore(nuovoEvento, rtInfo);
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

        appendOperatore(nuovoEvento, rtInfo);
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
        List<Header> soapRequestHeaders = buildSoapRequestHeaders();
        List<Header> soapResponseHeaders = SoapGdeCapturingInterceptor.getCapturedResponseHeaders();

        NuovoEvento nuovoEvento = eventoRtMapper.createEventoOk(
                rtInfo, Costanti.OPERATION_SEND_RECEIPT, transactionId, dataStart, dataEnd);

        eventoRtMapper.setParametriRichiesta(nuovoEvento, govpayUrl, "POST", soapRequestHeaders);
        eventoRtMapper.setParametriRispostaSoap(nuovoEvento, dataEnd, response, soapResponseHeaders);

        RtGdeUtils.serializzaPayloadSoap(this.jaxb2Marshaller, nuovoEvento, request, response, null);

        appendOperatore(nuovoEvento, rtInfo);
        sendEventAsync(nuovoEvento);
    }

    /**
     * Records a SEND_RECEIPT (paSendRTV2) that govpay ha rifiutato con
     * {@code PAA_RECEIPT_DUPLICATA}: la ricevuta e' gia' presente lato govpay,
     * quindi e' l'esito normale di una richiesta ripetuta, non un errore.
     * Esito {@code OK} (non {@code KO}), distinto da {@link #saveSendReceiptOk}
     * solo su {@code sottotipoEsito}/{@code dettaglioEsito}
     * cosi' resta riconoscibile nel giornale eventi.
     *
     * @param rtInfo          rt retrieve information: Organization tax code, IUR, IUV
     * @param request         SOAP request inviata
     * @param response        SOAP response con il fault PAA_RECEIPT_DUPLICATA
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     */
    public void saveSendReceiptDuplicata(RtRetrieveContext rtInfo, PaSendRTV2Request request, PaSendRTV2Response response,
                                         OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        String transactionId = UUID.randomUUID().toString();
        List<Header> soapRequestHeaders = buildSoapRequestHeaders();
        List<Header> soapResponseHeaders = SoapGdeCapturingInterceptor.getCapturedResponseHeaders();

        NuovoEvento nuovoEvento = eventoRtMapper.createEventoOk(
                rtInfo, Costanti.OPERATION_SEND_RECEIPT, transactionId, dataStart, dataEnd);
        nuovoEvento.setSottotipoEsito("PAA_RECEIPT_DUPLICATA");
        nuovoEvento.setDettaglioEsito("Ricevuta gia' presente lato govpay: richiesta ripetuta, non un errore.");

        eventoRtMapper.setParametriRichiesta(nuovoEvento, govpayUrl, "POST", soapRequestHeaders);
        // Non setParametriRispostaSoap: quel metodo traduce qualunque outcome SOAP diverso da
        // OK in status 500, incoerente con l'esito OK di questo evento.
        eventoRtMapper.setParametriRispostaSoapIdempotente(nuovoEvento, dataEnd, response, soapResponseHeaders);

        RtGdeUtils.serializzaPayloadSoap(this.jaxb2Marshaller, nuovoEvento, request, response, null);

        appendOperatore(nuovoEvento, rtInfo);
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
        List<Header> soapRequestHeaders = buildSoapRequestHeaders();
        List<Header> soapResponseHeaders = SoapGdeCapturingInterceptor.getCapturedResponseHeaders();

        NuovoEvento nuovoEvento = eventoRtMapper.createEventoKoSoap(
                rtInfo, Costanti.OPERATION_SEND_RECEIPT, transactionId, dataStart, dataEnd, exception);

        eventoRtMapper.setParametriRichiesta(nuovoEvento, govpayUrl, "POST", soapRequestHeaders);
        eventoRtMapper.setParametriRispostaSoapKo(nuovoEvento, dataEnd, exception, soapResponseHeaders);

        RtGdeUtils.serializzaPayloadSoap(this.jaxb2Marshaller, nuovoEvento, request, null, exception);

        appendOperatore(nuovoEvento, rtInfo);
        sendEventAsync(nuovoEvento);
    }

    /**
     * {@code NuovoEvento} (govpay-common) non espone un campo strutturato per l'operatore
     * che ha richiesto il recupero puntuale, anche se {@code rt_recuperi.id_operatore} esiste
     * apposta per questo. In attesa di un'evoluzione coordinata del contratto GDE, 
     * l'informazione si annota in coda al  {@code dettaglioEsito} come testo libero —
     * non interrogabile, ma non persa. Nessun effetto per la scansione automatica:
     * {@code idOperatore} e' sempre null li'.
     */
    private static void appendOperatore(NuovoEvento nuovoEvento, RtRetrieveContext rtInfo) {
        if (rtInfo == null || rtInfo.getIdOperatore() == null) {
            return;
        }
        String nota = "Recupero richiesto da operatore #" + rtInfo.getIdOperatore() + ".";
        String dettaglio = nuovoEvento.getDettaglioEsito();
        nuovoEvento.setDettaglioEsito(
                dettaglio != null && !dettaglio.isBlank() ? dettaglio + " " + nota : nota);
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
     * Builds standard SOAP request headers (Content-Type, SOAPAction).
     */
    private List<Header> buildSoapRequestHeaders() {
        List<Header> headers = new java.util.ArrayList<>();
        Header contentType = new Header();
        contentType.setNome("Content-Type");
        contentType.setValore("text/xml; charset=utf-8");
        headers.add(contentType);
        Header soapAction = new Header();
        soapAction.setNome("SOAPAction");
        soapAction.setValore(Costanti.OPERATION_SEND_RECEIPT);
        headers.add(soapAction);
        return headers;
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
