package it.govpay.rt.batch.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.StOutcome;
import it.govpay.common.client.model.Connettore;
import it.govpay.common.configurazione.model.GdeInterfaccia;
import it.govpay.common.configurazione.model.Giornale;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.gde.GdeEventInfo;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.mapper.EventoRtMapper;
import it.govpay.rt.batch.gde.service.GdeService;

@ExtendWith(MockitoExtension.class)
@DisplayName("GdeService")
class GdeServiceTest {

    @Mock
    private ConfigurazioneService configurazioneService;

    @Mock
    private EventoRtMapper eventoRtMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Jaxb2Marshaller jaxb2Marshaller;

    @Mock
    private RestTemplate gdeRestTemplate;

    // Use synchronous executor for predictable test execution
    private final Executor syncExecutor = Runnable::run;

    private GdeService gdeService;
    private RtRetrieveContext rtInfo;
    private OffsetDateTime dataStart;
    private OffsetDateTime dataEnd;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";
    private static final String PAGOPA_BASE_URL = "https://api.pagopa.it";
    private static final String GOVPAY_URL = "https://govpay.example.com/pa";
    private static final String GDE_ENDPOINT = "http://gde-service/api/v1/eventi";

    @BeforeEach
    void setUp() {
        gdeService = new GdeService(objectMapper, syncExecutor, configurazioneService,
                eventoRtMapper, jaxb2Marshaller);
        ReflectionTestUtils.setField(gdeService, "govpayUrl", GOVPAY_URL);

        rtInfo = RtRetrieveContext.builder()
                .rtId(1L)
                .taxCode(TAX_CODE)
                .iuv(IUV)
                .iur(IUR)
                .build();

        dataStart = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        dataEnd = OffsetDateTime.of(2024, 1, 15, 10, 0, 5, 0, ZoneOffset.UTC);
    }

    private void setupGdeEnabled() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(gdeRestTemplate);
        Connettore gdeConnettore = new Connettore();
        gdeConnettore.setUrl("http://gde-service/api/v1");
        when(configurazioneService.getServizioGDE()).thenReturn(gdeConnettore);
    }

    @Nested
    @DisplayName("sendEventAsync")
    class SendEventAsyncTest {

        @Test
        @DisplayName("should send event when GDE is enabled")
        void shouldSendEventWhenGdeEnabled() {
            setupGdeEnabled();
            NuovoEvento evento = new NuovoEvento();
            evento.setTipoEvento("TEST");

            gdeService.sendEventAsync(evento);

            verify(gdeRestTemplate).postForEntity(eq(GDE_ENDPOINT), eq(evento), eq(Void.class));
        }

        @Test
        @DisplayName("should not send event when GDE is disabled")
        void shouldNotSendEventWhenGdeDisabled() {
            when(configurazioneService.isServizioGDEAbilitato()).thenReturn(false);
            NuovoEvento evento = new NuovoEvento();
            evento.setTipoEvento("TEST");

            gdeService.sendEventAsync(evento);

            verifyNoInteractions(gdeRestTemplate);
        }

        @Test
        @DisplayName("should handle API exception gracefully")
        void shouldHandleApiExceptionGracefully() {
            setupGdeEnabled();
            NuovoEvento evento = new NuovoEvento();
            evento.setTipoEvento("TEST");
            when(gdeRestTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                    .thenThrow(new RuntimeException("API Error"));

            // Should not throw - errors are logged but not propagated
            assertDoesNotThrow(() -> gdeService.sendEventAsync(evento));
        }
    }

    @Nested
    @DisplayName("saveGetReceiptOk")
    class SaveGetReceiptOkTest {

        @Test
        @DisplayName("should create and send OK event for successful GET receipt")
        void shouldCreateAndSendOkEventForSuccessfulGetReceipt() {
            setupGdeEnabled();
            ResponseEntity<String> response = ResponseEntity.ok("receipt data");
            NuovoEvento mockEvento = new NuovoEvento();
            mockEvento.setEsito(EsitoEvento.OK);

            when(eventoRtMapper.createEventoOk(eq(rtInfo), anyString(), anyString(), eq(dataStart), eq(dataEnd)))
                    .thenReturn(mockEvento);

            gdeService.saveGetReceiptOk(rtInfo, response, dataStart, dataEnd, PAGOPA_BASE_URL);

            verify(eventoRtMapper).createEventoOk(eq(rtInfo), anyString(), anyString(), eq(dataStart), eq(dataEnd));
            verify(eventoRtMapper).setParametriRichiesta(eq(mockEvento), contains(TAX_CODE), eq("GET"), anyList());
            verify(eventoRtMapper).setParametriRisposta(eq(mockEvento), eq(dataEnd), eq(response), isNull());
            verify(gdeRestTemplate).postForEntity(eq(GDE_ENDPOINT), eq(mockEvento), eq(Void.class));
        }
    }

    @Nested
    @DisplayName("saveGetReceiptKo")
    class SaveGetReceiptKoTest {

        @Test
        @DisplayName("should create and send KO event for failed GET receipt")
        void shouldCreateAndSendKoEventForFailedGetReceipt() {
            setupGdeEnabled();
            ResponseEntity<String> response = ResponseEntity.badRequest().body("error");
            RestClientException exception = new RestClientException("API Error");
            NuovoEvento mockEvento = new NuovoEvento();
            mockEvento.setEsito(EsitoEvento.KO);

            when(eventoRtMapper.createEventoKo(eq(rtInfo), anyString(), anyString(),
                    eq(dataStart), eq(dataEnd), isNull(), any()))
                    .thenReturn(mockEvento);

            gdeService.saveGetReceiptKo(rtInfo, response, exception, dataStart, dataEnd, PAGOPA_BASE_URL);

            verify(eventoRtMapper).createEventoKo(eq(rtInfo), anyString(), anyString(),
                    eq(dataStart), eq(dataEnd), isNull(), eq(exception));
            verify(gdeRestTemplate).postForEntity(eq(GDE_ENDPOINT), eq(mockEvento), eq(Void.class));
        }
    }

    @Nested
    @DisplayName("saveSendReceiptOk")
    class SaveSendReceiptOkTest {

        @Test
        @DisplayName("should create and send OK event for successful SOAP call")
        void shouldCreateAndSendOkEventForSuccessfulSoapCall() {
            setupGdeEnabled();
            PaSendRTV2Request request = new PaSendRTV2Request();
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(StOutcome.OK);

            NuovoEvento mockEvento = new NuovoEvento();
            mockEvento.setEsito(EsitoEvento.OK);

            when(eventoRtMapper.createEventoOk(eq(rtInfo), anyString(), anyString(), eq(dataStart), eq(dataEnd)))
                    .thenReturn(mockEvento);

            gdeService.saveSendReceiptOk(rtInfo, request, response, dataStart, dataEnd);

            verify(eventoRtMapper).createEventoOk(eq(rtInfo), anyString(), anyString(), eq(dataStart), eq(dataEnd));
            verify(eventoRtMapper).setParametriRichiesta(eq(mockEvento), eq(GOVPAY_URL), eq("POST"), anyList());
            verify(eventoRtMapper).setParametriRispostaSoap(eq(mockEvento), eq(dataEnd), eq(response), anyList());
            verify(gdeRestTemplate).postForEntity(eq(GDE_ENDPOINT), eq(mockEvento), eq(Void.class));
        }
    }

    @Nested
    @DisplayName("convertToGdeEvent")
    class ConvertToGdeEventTest {

        @Test
        @DisplayName("should throw UnsupportedOperationException")
        void shouldThrowUnsupportedOperationException() {
            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> ReflectionTestUtils.invokeMethod(gdeService, "convertToGdeEvent",
                            (GdeEventInfo) null));
            assertTrue(ex.getMessage().contains("sendEventAsync"));
        }
    }

    @Nested
    @DisplayName("setResponsePayload")
    class SetResponsePayloadTest {

        @Test
        @DisplayName("should invoke setPayload when ParametriRisposta is present")
        void shouldSetPayloadWhenParametriRispostaPresent() {
            setupGdeEnabled();
            ResponseEntity<String> response = ResponseEntity.ok("receipt data");
            DettaglioRisposta parametriRisposta = spy(new DettaglioRisposta());
            NuovoEvento mockEvento = new NuovoEvento();
            mockEvento.setParametriRisposta(parametriRisposta);
            mockEvento.setEsito(EsitoEvento.OK);

            when(eventoRtMapper.createEventoOk(eq(rtInfo), anyString(), anyString(), eq(dataStart), eq(dataEnd)))
                    .thenReturn(mockEvento);

            gdeService.saveGetReceiptOk(rtInfo, response, dataStart, dataEnd, PAGOPA_BASE_URL);

            verify(parametriRisposta).setPayload(any());
            verify(gdeRestTemplate).postForEntity(eq(GDE_ENDPOINT), eq(mockEvento), eq(Void.class));
        }
    }

    @Nested
    @DisplayName("getConfigurazioneComponente")
    class GetConfigurazioneComponenteTest {

        private GdeInterfaccia invoke(ComponenteEvento componente, Giornale giornale) {
            return ReflectionTestUtils.invokeMethod(gdeService, "getConfigurazioneComponente",
                    componente, giornale);
        }

        @Test
        @DisplayName("should return null when componente is null")
        void shouldReturnNullWhenComponenteIsNull() {
            assertNull(invoke(null, new Giornale()));
        }

        @Test
        @DisplayName("should return null when giornale is null")
        void shouldReturnNullWhenGiornaleIsNull() {
            assertNull(invoke(ComponenteEvento.API_PAGOPA, null));
        }

        @Test
        @DisplayName("should map each componente to the matching Giornale getter")
        void shouldMapEachComponenteToMatchingGetter() {
            Giornale giornale = new Giornale();
            GdeInterfaccia apiPagoPA = new GdeInterfaccia();
            GdeInterfaccia apiEnte = new GdeInterfaccia();
            GdeInterfaccia apiPagamento = new GdeInterfaccia();
            GdeInterfaccia apiRagioneria = new GdeInterfaccia();
            GdeInterfaccia apiBackoffice = new GdeInterfaccia();
            GdeInterfaccia apiPendenze = new GdeInterfaccia();
            GdeInterfaccia apiBackendIO = new GdeInterfaccia();
            GdeInterfaccia apiMaggioliJPPA = new GdeInterfaccia();
            giornale.setApiPagoPA(apiPagoPA);
            giornale.setApiEnte(apiEnte);
            giornale.setApiPagamento(apiPagamento);
            giornale.setApiRagioneria(apiRagioneria);
            giornale.setApiBackoffice(apiBackoffice);
            giornale.setApiPendenze(apiPendenze);
            giornale.setApiBackendIO(apiBackendIO);
            giornale.setApiMaggioliJPPA(apiMaggioliJPPA);

            assertSame(apiPagoPA, invoke(ComponenteEvento.API_PAGOPA, giornale));
            assertSame(apiEnte, invoke(ComponenteEvento.API_ENTE, giornale));
            assertSame(apiPagamento, invoke(ComponenteEvento.API_PAGAMENTO, giornale));
            assertSame(apiRagioneria, invoke(ComponenteEvento.API_RAGIONERIA, giornale));
            assertSame(apiBackoffice, invoke(ComponenteEvento.API_BACKOFFICE, giornale));
            assertSame(apiPendenze, invoke(ComponenteEvento.API_PENDENZE, giornale));
            assertSame(apiBackendIO, invoke(ComponenteEvento.API_BACKEND_IO, giornale));
            assertSame(apiMaggioliJPPA, invoke(ComponenteEvento.API_MAGGIOLI_JPPA, giornale));
        }

        @Test
        @DisplayName("should return null for unmapped componente values")
        void shouldReturnNullForUnmappedComponenti() {
            Giornale giornale = new Giornale();
            assertNull(invoke(ComponenteEvento.API_GOVPAY, giornale));
            assertNull(invoke(ComponenteEvento.GOVPAY, giornale));
            assertNull(invoke(ComponenteEvento.API_USER, giornale));
        }
    }

    @Nested
    @DisplayName("saveSendReceiptKo")
    class SaveSendReceiptKoTest {

        @Test
        @DisplayName("should create and send KO event for failed SOAP call")
        void shouldCreateAndSendKoEventForFailedSoapCall() {
            setupGdeEnabled();
            PaSendRTV2Request request = new PaSendRTV2Request();
            Exception exception = new RuntimeException("SOAP Fault");

            NuovoEvento mockEvento = new NuovoEvento();
            mockEvento.setEsito(EsitoEvento.FAIL);

            when(eventoRtMapper.createEventoKoSoap(eq(rtInfo), anyString(), anyString(),
                    eq(dataStart), eq(dataEnd), eq(exception)))
                    .thenReturn(mockEvento);

            gdeService.saveSendReceiptKo(rtInfo, request, exception, dataStart, dataEnd);

            verify(eventoRtMapper).createEventoKoSoap(eq(rtInfo), anyString(), anyString(),
                    eq(dataStart), eq(dataEnd), eq(exception));
            verify(eventoRtMapper).setParametriRichiesta(eq(mockEvento), eq(GOVPAY_URL), eq("POST"), anyList());
            verify(eventoRtMapper).setParametriRispostaSoapKo(eq(mockEvento), eq(dataEnd), eq(exception), anyList());
            verify(gdeRestTemplate).postForEntity(eq(GDE_ENDPOINT), eq(mockEvento), eq(Void.class));
        }
    }
}
