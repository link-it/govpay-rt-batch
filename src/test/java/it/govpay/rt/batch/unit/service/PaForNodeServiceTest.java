package it.govpay.rt.batch.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.CtFaultBean;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.StOutcome;
import it.govpay.rt.batch.client.GovpayClient;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.service.GdeService;
import it.govpay.rt.batch.service.OperatorePrincipalResolver;
import it.govpay.rt.batch.service.PaForNodeService;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaForNodeService")
class PaForNodeServiceTest {

    @Mock
    private GdeService gdeService;

    @Mock
    private GovpayClient govpayClient;

    @Mock
    private OperatorePrincipalResolver operatorePrincipalResolver;

    private PaForNodeService service;
    private RtRetrieveContext rtInfo;
    private PaSendRTV2Request request;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";

    @BeforeEach
    void setUp() {
        service = new PaForNodeService(gdeService, govpayClient, operatorePrincipalResolver,
                JsonMapper.builder().build());

        rtInfo = RtRetrieveContext.builder()
                .rtId(1L)
                .taxCode(TAX_CODE)
                .iuv(IUV)
                .iur(IUR)
                .build();

        request = new PaSendRTV2Request();
    }

    @Nested
    @DisplayName("sendReceipt")
    class SendReceiptTest {

        @Test
        @DisplayName("should return true and save OK event when response is OK")
        void shouldReturnTrueWhenResponseIsOk() {
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(StOutcome.OK);
            when(govpayClient.sendReceipt(request, null)).thenReturn(response);

            boolean result = service.sendReceipt(rtInfo, request);

            assertTrue(result);
            verify(govpayClient).sendReceipt(request, null);
            verify(gdeService).saveSendReceiptOk(eq(rtInfo), eq(request), eq(response), any(), any());
            verify(gdeService, never()).saveSendReceiptKo(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should return false and save KO event when response is KO")
        void shouldReturnFalseWhenResponseIsKo() {
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(StOutcome.KO);
            CtFaultBean fault = new CtFaultBean();
            fault.setFaultCode("PAA_ERROR");
            fault.setDescription("Payment error");
            response.setFault(fault);
            when(govpayClient.sendReceipt(request, null)).thenReturn(response);

            boolean result = service.sendReceipt(rtInfo, request);

            assertFalse(result);
            verify(govpayClient).sendReceipt(request, null);
            verify(gdeService).saveSendReceiptKo(eq(rtInfo), eq(request), any(Exception.class), any(), any());
            verify(gdeService, never()).saveSendReceiptOk(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("issue #40: il dettaglio del KO e' il fault in JSON, senza prefisso e senza campi nulli")
        void shouldDescribeFaultAsJson() {
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(StOutcome.KO);
            CtFaultBean fault = new CtFaultBean();
            fault.setFaultCode("PAA_SINTASSI_XSD");
            fault.setFaultString("Errore di sintassi");
            fault.setId("12345678901");
            fault.setDescription("cvc-complex-type.2.4.a");
            fault.setSerial(1);
            response.setFault(fault);
            when(govpayClient.sendReceipt(request, null)).thenReturn(response);

            assertFalse(service.sendReceipt(rtInfo, request));

            ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
            verify(gdeService).saveSendReceiptKo(eq(rtInfo), eq(request), captor.capture(), any(), any());
            assertEquals("{\"faultCode\":\"PAA_SINTASSI_XSD\",\"faultString\":\"Errore di sintassi\","
                    + "\"id\":\"12345678901\",\"description\":\"cvc-complex-type.2.4.a\",\"serial\":1}",
                    captor.getValue().getMessage());
        }

        @Test
        @DisplayName("issue #40: senza fault il dettaglio lo dice in modo esplicito")
        void shouldDescribeMissingFault() {
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(StOutcome.KO);
            when(govpayClient.sendReceipt(request, null)).thenReturn(response);

            assertFalse(service.sendReceipt(rtInfo, request));

            ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
            verify(gdeService).saveSendReceiptKo(eq(rtInfo), eq(request), captor.capture(), any(), any());
            assertEquals("Esito KO senza fault nella risposta di GovPay", captor.getValue().getMessage());
        }

        @Test
        @DisplayName("should return false and save KO event when exception occurs")
        void shouldReturnFalseWhenExceptionOccurs() {
            RuntimeException exception = new RuntimeException("Connection error");
            when(govpayClient.sendReceipt(request, null)).thenThrow(exception);

            boolean result = service.sendReceipt(rtInfo, request);

            assertFalse(result);
            verify(govpayClient).sendReceipt(request, null);
            verify(gdeService).saveSendReceiptKo(eq(rtInfo), eq(request), eq(exception), any(), any());
            verify(gdeService, never()).saveSendReceiptOk(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should return false and save KO event when response is null")
        void shouldReturnFalseWhenResponseIsNull() {
            // GovpayClient restituisce null quando la richiesta non e' valorizzata:
            // il servizio non deve dereferenziare la response.
            when(govpayClient.sendReceipt(request, null)).thenReturn(null);

            boolean result = service.sendReceipt(rtInfo, request);

            assertFalse(result);
            verify(govpayClient).sendReceipt(request, null);
            verify(gdeService).saveSendReceiptKo(eq(rtInfo), eq(request), any(Exception.class), any(), any());
            verify(gdeService, never()).saveSendReceiptOk(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should return false and save KO event when outcome is null")
        void shouldReturnFalseWhenOutcomeIsNull() {
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(null);
            when(govpayClient.sendReceipt(request, null)).thenReturn(response);

            boolean result = service.sendReceipt(rtInfo, request);

            assertFalse(result);
            verify(gdeService).saveSendReceiptKo(eq(rtInfo), eq(request), any(Exception.class), any(), any());
            verify(gdeService, never()).saveSendReceiptOk(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("issue #59 §10 / #17 §D: should return true and save evento dedicato quando il fault e' PAA_RECEIPT_DUPLICATA")
        void shouldReturnTrueAndSaveDuplicataEventWhenFaultIsPaaReceiptDuplicata() {
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(StOutcome.KO);
            CtFaultBean fault = new CtFaultBean();
            fault.setFaultCode("PAA_RECEIPT_DUPLICATA");
            fault.setDescription("La ricevuta e' gia' stata acquisita");
            response.setFault(fault);
            when(govpayClient.sendReceipt(request, null)).thenReturn(response);

            boolean result = service.sendReceipt(rtInfo, request);

            assertTrue(result);
            verify(gdeService).saveSendReceiptDuplicata(eq(rtInfo), eq(request), eq(response), any(), any());
            verify(gdeService, never()).saveSendReceiptKo(any(), any(), any(), any(), any());
            verify(gdeService, never()).saveSendReceiptOk(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should resolve idOperatore to a principal and pass it as onBehalfOf")
        void shouldPassResolvedPrincipalAsOnBehalfOf() {
            RtRetrieveContext rtInfoConOperatore = RtRetrieveContext.builder()
                    .rtId(1L).taxCode(TAX_CODE).iuv(IUV).iur(IUR).idOperatore(42L).build();
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(StOutcome.OK);
            when(operatorePrincipalResolver.resolve(42L)).thenReturn("u-operatore");
            when(govpayClient.sendReceipt(request, "u-operatore")).thenReturn(response);

            boolean result = service.sendReceipt(rtInfoConOperatore, request);

            assertTrue(result);
            verify(govpayClient).sendReceipt(request, "u-operatore");
        }
    }
}
