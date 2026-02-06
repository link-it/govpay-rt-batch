package it.govpay.rt.batch.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.StOutcome;
import it.govpay.gde.client.api.EventiApi;
import it.govpay.gde.client.model.EsitoEvento;
import it.govpay.gde.client.model.NuovoEvento;
import it.govpay.rt.batch.config.PagoPAProperties;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.mapper.EventoRtMapper;
import it.govpay.rt.batch.gde.service.GdeService;

@ExtendWith(MockitoExtension.class)
@DisplayName("GdeService")
class GdeServiceTest {

    @Mock
    private EventiApi eventiApi;

    @Mock
    private EventoRtMapper eventoRtMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Jaxb2Marshaller jaxb2Marshaller;

    @Mock
    private PagoPAProperties pagoPAProperties;

    // Use SyncTaskExecutor for predictable test execution
    private TaskExecutor taskExecutor = new SyncTaskExecutor();

    private GdeService gdeService;
    private RtRetrieveContext rtInfo;
    private OffsetDateTime dataStart;
    private OffsetDateTime dataEnd;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";
    private static final String PAGOPA_BASE_URL = "https://api.pagopa.it";
    private static final String GOVPAY_URL = "https://govpay.example.com/pa";

    @BeforeEach
    void setUp() {
        gdeService = new GdeService(eventiApi, eventoRtMapper, objectMapper,
                jaxb2Marshaller, pagoPAProperties, taskExecutor);
        ReflectionTestUtils.setField(gdeService, "gdeEnabled", true);
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

    private void setupPagoPABaseUrl() {
        when(pagoPAProperties.getBaseUrl()).thenReturn(PAGOPA_BASE_URL);
    }

    @Nested
    @DisplayName("inviaEvento")
    class InviaEventoTest {

        @Test
        @DisplayName("should send event when GDE is enabled")
        void shouldSendEventWhenGdeEnabled() throws Exception {
            NuovoEvento evento = new NuovoEvento();
            evento.setTipoEvento("TEST");

            gdeService.inviaEvento(evento);

            verify(eventiApi).addEvento(evento);
        }

        @Test
        @DisplayName("should not send event when GDE is disabled")
        void shouldNotSendEventWhenGdeDisabled() throws Exception {
            ReflectionTestUtils.setField(gdeService, "gdeEnabled", false);
            NuovoEvento evento = new NuovoEvento();
            evento.setTipoEvento("TEST");

            gdeService.inviaEvento(evento);

            verifyNoInteractions(eventiApi);
        }

        @Test
        @DisplayName("should handle API exception gracefully")
        void shouldHandleApiExceptionGracefully() throws Exception {
            NuovoEvento evento = new NuovoEvento();
            evento.setTipoEvento("TEST");
            doThrow(new RuntimeException("API Error")).when(eventiApi).addEvento(any());

            // Should not throw - errors are logged but not propagated
            assertDoesNotThrow(() -> gdeService.inviaEvento(evento));
        }
    }

    @Nested
    @DisplayName("saveGetReceiptOk")
    class SaveGetReceiptOkTest {

        @Test
        @DisplayName("should create and send OK event for successful GET receipt")
        void shouldCreateAndSendOkEventForSuccessfulGetReceipt() throws Exception {
            setupPagoPABaseUrl();
            ResponseEntity<String> response = ResponseEntity.ok("receipt data");
            NuovoEvento mockEvento = new NuovoEvento();
            mockEvento.setEsito(EsitoEvento.OK);

            when(eventoRtMapper.createEventoOk(eq(rtInfo), anyString(), anyString(), eq(dataStart), eq(dataEnd)))
                    .thenReturn(mockEvento);

            gdeService.saveGetReceiptOk(rtInfo, response, dataStart, dataEnd);

            verify(eventoRtMapper).createEventoOk(eq(rtInfo), anyString(), anyString(), eq(dataStart), eq(dataEnd));
            verify(eventoRtMapper).setParametriRichiesta(eq(mockEvento), contains(TAX_CODE), eq("GET"), anyList());
            verify(eventoRtMapper).setParametriRisposta(eq(mockEvento), eq(dataEnd), eq(response), isNull());
            verify(eventiApi).addEvento(mockEvento);
        }
    }

    @Nested
    @DisplayName("saveGetReceiptKo")
    class SaveGetReceiptKoTest {

        @Test
        @DisplayName("should create and send KO event for failed GET receipt")
        void shouldCreateAndSendKoEventForFailedGetReceipt() throws Exception {
            setupPagoPABaseUrl();
            ResponseEntity<String> response = ResponseEntity.badRequest().body("error");
            RestClientException exception = new RestClientException("API Error");
            NuovoEvento mockEvento = new NuovoEvento();
            mockEvento.setEsito(EsitoEvento.KO);

            when(eventoRtMapper.createEventoKo(eq(rtInfo), anyString(), anyString(),
                    eq(dataStart), eq(dataEnd), isNull(), any()))
                    .thenReturn(mockEvento);

            gdeService.saveGetReceiptKo(rtInfo, response, exception, dataStart, dataEnd);

            verify(eventoRtMapper).createEventoKo(eq(rtInfo), anyString(), anyString(),
                    eq(dataStart), eq(dataEnd), isNull(), eq(exception));
            verify(eventiApi).addEvento(mockEvento);
        }
    }

    @Nested
    @DisplayName("saveSendReceiptOk")
    class SaveSendReceiptOkTest {

        @Test
        @DisplayName("should create and send OK event for successful SOAP call")
        void shouldCreateAndSendOkEventForSuccessfulSoapCall() throws Exception {
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
            verify(eventoRtMapper).setParametriRispostaSoap(eq(mockEvento), eq(dataEnd), eq(response));
            verify(eventiApi).addEvento(mockEvento);
        }
    }

    @Nested
    @DisplayName("saveSendReceiptKo")
    class SaveSendReceiptKoTest {

        @Test
        @DisplayName("should create and send KO event for failed SOAP call")
        void shouldCreateAndSendKoEventForFailedSoapCall() throws Exception {
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
            verify(eventoRtMapper).setParametriRispostaSoapKo(eq(mockEvento), eq(dataEnd), eq(exception));
            verify(eventiApi).addEvento(mockEvento);
        }
    }
}
