package it.govpay.rt.batch.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.CtFaultBean;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.StOutcome;
import it.govpay.rt.batch.client.GovpayClient;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.service.GdeService;
import it.govpay.rt.batch.service.PaForNodeService;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaForNodeService")
class PaForNodeServiceTest {

    @Mock
    private GdeService gdeService;

    @Mock
    private GovpayClient govpayClient;

    private PaForNodeService service;
    private RtRetrieveContext rtInfo;
    private PaSendRTV2Request request;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";

    @BeforeEach
    void setUp() {
        service = new PaForNodeService(gdeService, govpayClient);

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
            when(govpayClient.sendReceipt(request)).thenReturn(response);

            boolean result = service.sendReceipt(rtInfo, request);

            assertTrue(result);
            verify(govpayClient).sendReceipt(request);
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
            when(govpayClient.sendReceipt(request)).thenReturn(response);

            boolean result = service.sendReceipt(rtInfo, request);

            assertFalse(result);
            verify(govpayClient).sendReceipt(request);
            verify(gdeService).saveSendReceiptKo(eq(rtInfo), eq(request), any(Exception.class), any(), any());
            verify(gdeService, never()).saveSendReceiptOk(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should return false and save KO event when exception occurs")
        void shouldReturnFalseWhenExceptionOccurs() {
            RuntimeException exception = new RuntimeException("Connection error");
            when(govpayClient.sendReceipt(request)).thenThrow(exception);

            boolean result = service.sendReceipt(rtInfo, request);

            assertFalse(result);
            verify(govpayClient).sendReceipt(request);
            verify(gdeService).saveSendReceiptKo(eq(rtInfo), eq(request), eq(exception), any(), any());
            verify(gdeService, never()).saveSendReceiptOk(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should work when GdeService is null")
        void shouldWorkWhenGdeServiceIsNull() {
            service = new PaForNodeService(null, govpayClient);
            PaSendRTV2Response response = new PaSendRTV2Response();
            response.setOutcome(StOutcome.OK);
            when(govpayClient.sendReceipt(request)).thenReturn(response);

            // Should throw NullPointerException because gdeService is null
            assertThrows(NullPointerException.class, () -> service.sendReceipt(rtInfo, request));
        }
    }
}
