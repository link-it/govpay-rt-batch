package it.govpay.rt.batch.unit.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.StOutcome;
import it.govpay.gde.client.model.CategoriaEvento;
import it.govpay.gde.client.model.ComponenteEvento;
import it.govpay.gde.client.model.EsitoEvento;
import it.govpay.gde.client.model.Header;
import it.govpay.gde.client.model.NuovoEvento;
import it.govpay.gde.client.model.RuoloEvento;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.mapper.EventoRtMapper;

@DisplayName("EventoRtMapper")
class EventoRtMapperTest {

    private EventoRtMapper mapper;
    private RtRetrieveContext rtInfo;
    private OffsetDateTime dataStart;
    private OffsetDateTime dataEnd;

    private static final String CLUSTER_ID = "test-cluster";
    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";
    private static final String TIPO_EVENTO = "GET_RECEIPT";
    private static final String TRANSACTION_ID = "txn-123";

    @BeforeEach
    void setUp() {
        mapper = new EventoRtMapper();
        ReflectionTestUtils.setField(mapper, "clusterId", CLUSTER_ID);

        rtInfo = RtRetrieveContext.builder()
                .rtId(1L)
                .taxCode(TAX_CODE)
                .iuv(IUV)
                .iur(IUR)
                .build();

        dataStart = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        dataEnd = OffsetDateTime.of(2024, 1, 15, 10, 0, 5, 0, ZoneOffset.UTC);
    }

    @Nested
    @DisplayName("createEvento")
    class CreateEventoTest {

        @Test
        @DisplayName("should create base event with all fields populated")
        void shouldCreateBaseEventWithAllFields() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            assertNotNull(evento);
            assertEquals(TAX_CODE, evento.getIdDominio());
            assertEquals(CategoriaEvento.INTERFACCIA, evento.getCategoriaEvento());
            assertEquals(CLUSTER_ID, evento.getClusterId());
            assertEquals(dataStart, evento.getDataEvento());
            assertEquals(5L, evento.getDurataEvento()); // 5 seconds difference
            assertEquals(RuoloEvento.CLIENT, evento.getRuolo());
            assertEquals(ComponenteEvento.API_PAGOPA, evento.getComponente());
            assertEquals(TIPO_EVENTO, evento.getTipoEvento());
            assertEquals(TRANSACTION_ID, evento.getTransactionId());
        }

        @Test
        @DisplayName("should populate datiPagoPA with taxCode")
        void shouldPopulateDatiPagoPA() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            assertNotNull(evento.getDatiPagoPA());
            assertEquals(TAX_CODE, evento.getDatiPagoPA().getIdDominio());
        }

        @Test
        @DisplayName("should handle null rtInfo")
        void shouldHandleNullRtInfo() {
            NuovoEvento evento = mapper.createEvento(null, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            assertNotNull(evento);
            assertNull(evento.getIdDominio());
            assertNull(evento.getDatiPagoPA());
        }
    }

    @Nested
    @DisplayName("createEventoOk")
    class CreateEventoOkTest {

        @Test
        @DisplayName("should create event with OK esito")
        void shouldCreateEventWithOkEsito() {
            NuovoEvento evento = mapper.createEventoOk(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            assertNotNull(evento);
            assertEquals(EsitoEvento.OK, evento.getEsito());
        }
    }

    @Nested
    @DisplayName("createEventoKo")
    class CreateEventoKoTest {

        @Test
        @DisplayName("should set KO esito for 4xx client errors")
        void shouldSetKoEsitoFor4xxErrors() {
            HttpClientErrorException exception = HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, "error".getBytes(), null);

            NuovoEvento evento = mapper.createEventoKo(rtInfo, TIPO_EVENTO, TRANSACTION_ID,
                    dataStart, dataEnd, null, exception);

            assertEquals(EsitoEvento.KO, evento.getEsito());
            assertEquals("400", evento.getSottotipoEsito());
        }

        @Test
        @DisplayName("should set FAIL esito for 5xx server errors")
        void shouldSetFailEsitoFor5xxErrors() {
            HttpServerErrorException exception = HttpServerErrorException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, "error".getBytes(), null);

            NuovoEvento evento = mapper.createEventoKo(rtInfo, TIPO_EVENTO, TRANSACTION_ID,
                    dataStart, dataEnd, null, exception);

            assertEquals(EsitoEvento.FAIL, evento.getEsito());
            assertEquals("500", evento.getSottotipoEsito());
        }

        @Test
        @DisplayName("should set FAIL esito for non-HTTP exceptions")
        void shouldSetFailEsitoForNonHttpExceptions() {
            RestClientException exception = new RestClientException("Connection refused");

            NuovoEvento evento = mapper.createEventoKo(rtInfo, TIPO_EVENTO, TRANSACTION_ID,
                    dataStart, dataEnd, null, exception);

            assertEquals(EsitoEvento.FAIL, evento.getEsito());
            assertEquals("500", evento.getSottotipoEsito());
            assertEquals("Connection refused", evento.getDettaglioEsito());
        }
    }

    @Nested
    @DisplayName("setParametriRichiesta")
    class SetParametriRichiestaTest {

        @Test
        @DisplayName("should set request parameters")
        void shouldSetRequestParameters() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
            Header header = new Header();
            header.setNome("Content-Type");
            header.setValore("application/json");

            mapper.setParametriRichiesta(evento, "http://api.test/endpoint", "GET", Collections.singletonList(header));

            assertNotNull(evento.getParametriRichiesta());
            assertEquals("http://api.test/endpoint", evento.getParametriRichiesta().getUrl());
            assertEquals("GET", evento.getParametriRichiesta().getMethod());
            assertEquals(1, evento.getParametriRichiesta().getHeaders().size());
        }
    }

    @Nested
    @DisplayName("setParametriRisposta")
    class SetParametriRispostaTest {

        @Test
        @DisplayName("should set response status from ResponseEntity")
        void shouldSetResponseStatusFromResponseEntity() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
            ResponseEntity<String> response = ResponseEntity.ok("body");

            mapper.setParametriRisposta(evento, dataEnd, response, null);

            assertNotNull(evento.getParametriRisposta());
            assertEquals(BigDecimal.valueOf(200), evento.getParametriRisposta().getStatus());
        }

        @Test
        @DisplayName("should set response status from exception")
        void shouldSetResponseStatusFromException() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
            HttpClientErrorException exception = HttpClientErrorException.create(
                    HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null);

            mapper.setParametriRisposta(evento, dataEnd, null, exception);

            assertNotNull(evento.getParametriRisposta());
            assertEquals(BigDecimal.valueOf(404), evento.getParametriRisposta().getStatus());
        }

        @Test
        @DisplayName("should set status 500 for non-HTTP exceptions")
        void shouldSetStatus500ForNonHttpExceptions() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            mapper.setParametriRisposta(evento, dataEnd, null, null);

            assertNotNull(evento.getParametriRisposta());
            assertEquals(BigDecimal.valueOf(500), evento.getParametriRisposta().getStatus());
        }
    }

    @Nested
    @DisplayName("SOAP methods")
    class SoapMethodsTest {

        @Test
        @DisplayName("setParametriRispostaSoap should set status 200 for OK response")
        void shouldSetStatus200ForOkSoapResponse() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(StOutcome.OK);

            mapper.setParametriRispostaSoap(evento, dataEnd, response);

            assertEquals(BigDecimal.valueOf(200), evento.getParametriRisposta().getStatus());
        }

        @Test
        @DisplayName("setParametriRispostaSoap should set status 500 for KO response")
        void shouldSetStatus500ForKoSoapResponse() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(StOutcome.KO);

            mapper.setParametriRispostaSoap(evento, dataEnd, response);

            assertEquals(BigDecimal.valueOf(500), evento.getParametriRisposta().getStatus());
        }

        @Test
        @DisplayName("createEventoKoSoap should set FAIL esito for generic exceptions")
        void shouldSetFailEsitoForGenericSoapException() {
            Exception exception = new RuntimeException("SOAP error");

            NuovoEvento evento = mapper.createEventoKoSoap(rtInfo, TIPO_EVENTO, TRANSACTION_ID,
                    dataStart, dataEnd, exception);

            assertEquals(EsitoEvento.FAIL, evento.getEsito());
            assertEquals("500", evento.getSottotipoEsito());
            assertEquals("SOAP error", evento.getDettaglioEsito());
        }
    }
}
