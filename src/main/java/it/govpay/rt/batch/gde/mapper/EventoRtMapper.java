package it.govpay.rt.batch.gde.mapper;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.StOutcome;
import it.govpay.gde.client.model.*;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.ws.soap.client.SoapFaultClientException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapper for creating GDE events from FDR batch operations.
 * <p>
 * This mapper creates NuovoEvento objects to track RT retrieve operations
 * including successful and failed API calls to pagoPA or successful and faile calls to Govpay
 */
@Slf4j
@Component
public class EventoRtMapper {

    @Value("${govpay.batch.cluster-id}")
    private String clusterId;

    /**
     * Creates DatiPagoPA object from rt retrieve info.
     *
     * @param rtInfo RtRetrieveContext
     * @return DatiPagoPA with FDR-specific data
     */     
    private DatiPagoPA createDatiPagoPA(RtRetrieveContext rtInfo) {
        DatiPagoPA datiPagoPA = new DatiPagoPA();
        datiPagoPA.setIdDominio(rtInfo.getTaxCode());
        return datiPagoPA;
    }   

    /**
     * Creates a base event.
     *
     * @param rtInfo           rt retrieve info
     * @param tipoEvento       Event type (e.g., GET_PUBLISHED_FLOWS, GET_FLOW_DETAILS)
     * @param transactionId    Unique transaction identifier
     * @param dataStart        Event start timestamp
     * @param dataEnd          Event end timestamp
     * @return NuovoEvento with base fields populated
     */
    public NuovoEvento createEvento(RtRetrieveContext rtInfo, String tipoEvento, String transactionId,
                                    OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        NuovoEvento nuovoEvento = new NuovoEvento();

        if (rtInfo != null) {
        	nuovoEvento.setIdDominio(rtInfo.getTaxCode());
        	nuovoEvento.setDatiPagoPA(createDatiPagoPA(rtInfo));
        }

        // Set event metadata
        nuovoEvento.setCategoriaEvento(CategoriaEvento.INTERFACCIA);
        nuovoEvento.setClusterId(clusterId);
        nuovoEvento.setDataEvento(dataStart);
        nuovoEvento.setDurataEvento(dataEnd.toEpochSecond() - dataStart.toEpochSecond());
        nuovoEvento.setRuolo(RuoloEvento.CLIENT);
        nuovoEvento.setComponente(ComponenteEvento.API_PAGOPA);
        nuovoEvento.setTipoEvento(tipoEvento);
        nuovoEvento.setTransactionId(transactionId);

        return nuovoEvento;
    }

    /**
     * Creates an OK event for successful operations.
     *
     * @param rtInfo           rt retrieve info
     * @param tipoEvento       Event type
     * @param transactionId    Transaction ID
     * @param dataStart        Start timestamp
     * @param dataEnd          End timestamp
     * @return NuovoEvento with OK outcome
     */
    public NuovoEvento createEventoOk(RtRetrieveContext rtInfo, String tipoEvento, String transactionId,
                                      OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        NuovoEvento nuovoEvento = createEvento(rtInfo, tipoEvento, transactionId, dataStart, dataEnd);
        nuovoEvento.setEsito(EsitoEvento.OK);
        return nuovoEvento;
    }

    /**
     * Creates a KO/FAIL event for failed operations.
     *
     * @param rtInfo           rt retrieve info
     * @param tipoEvento       Event type
     * @param transactionId    Transaction ID
     * @param dataStart        Start timestamp
     * @param dataEnd          End timestamp
     * @param responseEntity   Response entity (if available)
     * @param exception        Exception (if any)
     * @return NuovoEvento with KO/FAIL outcome
     */
    public NuovoEvento createEventoKo(RtRetrieveContext rtInfo, String tipoEvento, String transactionId,
                                      OffsetDateTime dataStart, OffsetDateTime dataEnd,
                                      ResponseEntity<?> responseEntity, RestClientException exception) {
        NuovoEvento nuovoEvento = createEvento(rtInfo, tipoEvento, transactionId, dataStart, dataEnd);
        extractExceptionInfo(responseEntity, exception, nuovoEvento);
        return nuovoEvento;
    }

    /**
     * Sets request details on the event.
     *
     * @param nuovoEvento      Event to update
     * @param urlOperazione    Operation URL
     * @param httpMethod       HTTP method (GET, POST, etc.)
     * @param headers          HTTP headers
     */
    public void setParametriRichiesta(NuovoEvento nuovoEvento, String urlOperazione,
                                      String httpMethod, List<Header> headers) {
        DettaglioRichiesta dettaglioRichiesta = new DettaglioRichiesta();
        dettaglioRichiesta.setDataOraRichiesta(nuovoEvento.getDataEvento());
        dettaglioRichiesta.setMethod(httpMethod);
        dettaglioRichiesta.setUrl(urlOperazione);
        dettaglioRichiesta.setHeaders(headers);

        nuovoEvento.setParametriRichiesta(dettaglioRichiesta);
    }

    /**
     * Sets response details on the event.
     *
     * @param nuovoEvento      Event to update
     * @param dataEnd          Response timestamp
     * @param responseEntity   Response entity
     * @param exception        Exception (if any)
     */
    public void setParametriRisposta(NuovoEvento nuovoEvento, OffsetDateTime dataEnd,
                                     ResponseEntity<?> responseEntity, RestClientException exception) {
        DettaglioRisposta dettaglioRisposta = new DettaglioRisposta();
        dettaglioRisposta.setDataOraRisposta(dataEnd);

        List<Header> headers = new ArrayList<>();

        if (responseEntity != null) {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(responseEntity.getStatusCode().value()));

            HttpHeaders httpHeaders = responseEntity.getHeaders();
            httpHeaders.forEach((key, value) -> {
                if (!value.isEmpty()) {
                    Header header = new Header();
                    header.setNome(key);
                    header.setValore(value.get(0));
                    headers.add(header);
                }
            });
        } else if (exception instanceof HttpStatusCodeException httpStatusCodeException) {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(httpStatusCodeException.getStatusCode().value()));

            HttpHeaders httpHeaders = httpStatusCodeException.getResponseHeaders();
            if (httpHeaders != null) {
                httpHeaders.forEach((key, value) -> {
                    if (!value.isEmpty()) {
                        Header header = new Header();
                        header.setNome(key);
                        header.setValore(value.get(0));
                        headers.add(header);
                    }
                });
            }
        } else {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(500));
        }

        dettaglioRisposta.setHeaders(headers);
        nuovoEvento.setParametriRisposta(dettaglioRisposta);
    }

    /**
     * Creates a KO/FAIL event for failed SOAP operations.
     *
     * @param rtInfo           rt retrieve info
     * @param tipoEvento       Event type
     * @param transactionId    Transaction ID
     * @param dataStart        Start timestamp
     * @param dataEnd          End timestamp
     * @param exception        Exception (if any)
     * @return NuovoEvento with KO/FAIL outcome
     */
    public NuovoEvento createEventoKoSoap(RtRetrieveContext rtInfo, String tipoEvento, String transactionId,
                                          OffsetDateTime dataStart, OffsetDateTime dataEnd, Exception exception) {
        NuovoEvento nuovoEvento = createEvento(rtInfo, tipoEvento, transactionId, dataStart, dataEnd);
        extractSoapExceptionInfo(exception, nuovoEvento);
        return nuovoEvento;
    }

    /**
     * Sets response details for successful SOAP operations.
     *
     * @param nuovoEvento      Event to update
     * @param dataEnd          Response timestamp
     * @param response         SOAP response
     */
    public void setParametriRispostaSoap(NuovoEvento nuovoEvento, OffsetDateTime dataEnd, PaSendRTV2Response response) {
        DettaglioRisposta dettaglioRisposta = new DettaglioRisposta();
        dettaglioRisposta.setDataOraRisposta(dataEnd);
        dettaglioRisposta.setHeaders(new ArrayList<>());

        if (response != null && response.getOutcome() == StOutcome.OK) {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(200));
        } else {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(500));
        }

        nuovoEvento.setParametriRisposta(dettaglioRisposta);
    }

    /**
     * Sets response details for failed SOAP operations.
     *
     * @param nuovoEvento      Event to update
     * @param dataEnd          Response timestamp
     * @param exception        Exception
     */
    public void setParametriRispostaSoapKo(NuovoEvento nuovoEvento, OffsetDateTime dataEnd, Exception exception) {
        DettaglioRisposta dettaglioRisposta = new DettaglioRisposta();
        dettaglioRisposta.setDataOraRisposta(dataEnd);
        dettaglioRisposta.setHeaders(new ArrayList<>());

        if (exception instanceof SoapFaultClientException soapFault) {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(500));
        } else {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(500));
        }

        nuovoEvento.setParametriRisposta(dettaglioRisposta);
    }

    /**
     * Extracts error information from SOAP exception and sets outcome (KO or FAIL).
     *
     * @param exception      Exception
     * @param nuovoEvento    Event to update
     */
    private void extractSoapExceptionInfo(Exception exception, NuovoEvento nuovoEvento) {
        if (exception instanceof SoapFaultClientException soapFault) {
            nuovoEvento.setDettaglioEsito(soapFault.getFaultStringOrReason());
            nuovoEvento.setSottotipoEsito(soapFault.getFaultCode() != null ? soapFault.getFaultCode().toString() : "SOAP_FAULT");
            nuovoEvento.setEsito(EsitoEvento.KO);
        } else if (exception != null) {
            nuovoEvento.setDettaglioEsito(exception.getMessage());
            nuovoEvento.setSottotipoEsito("500");
            nuovoEvento.setEsito(EsitoEvento.FAIL);
        }
    }

    /**
     * Extracts error information from exception and sets outcome (KO or FAIL).
     *
     * @param responseEntity Response entity
     * @param exception      Exception
     * @param nuovoEvento    Event to update
     */
    private void extractExceptionInfo(ResponseEntity<?> responseEntity, RestClientException exception,
                                      NuovoEvento nuovoEvento) {
        if (exception != null) {
            if (exception instanceof HttpStatusCodeException httpStatusCodeException) {
                nuovoEvento.setDettaglioEsito(httpStatusCodeException.getResponseBodyAsString());
                nuovoEvento.setSottotipoEsito(httpStatusCodeException.getStatusCode().value() + "");

                if (httpStatusCodeException.getStatusCode().is5xxServerError()) {
                    nuovoEvento.setEsito(EsitoEvento.FAIL);
                } else {
                    nuovoEvento.setEsito(EsitoEvento.KO);
                }
            } else {
                nuovoEvento.setDettaglioEsito(exception.getMessage());
                nuovoEvento.setSottotipoEsito("500");
                nuovoEvento.setEsito(EsitoEvento.FAIL);
            }
        } else if (responseEntity != null) {
            nuovoEvento.setDettaglioEsito(HttpStatus.valueOf(responseEntity.getStatusCode().value()).getReasonPhrase());
            nuovoEvento.setSottotipoEsito("" + responseEntity.getStatusCode().value());

            if (responseEntity.getStatusCode().is5xxServerError()) {
                nuovoEvento.setEsito(EsitoEvento.FAIL);
            } else {
                nuovoEvento.setEsito(EsitoEvento.KO);
            }
        }
    }
}
