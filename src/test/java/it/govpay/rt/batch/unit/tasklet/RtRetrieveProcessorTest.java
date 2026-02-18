package it.govpay.rt.batch.unit.tasklet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.rt.batch.dto.RtRetrieveBatch;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.service.PaForNodeService;
import it.govpay.rt.batch.service.RtApiService;
import it.govpay.rt.batch.tasklet.RtRetrieveProcessor;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtRetrieveProcessor")
class RtRetrieveProcessorTest {

    @Mock
    private RtApiService rtApiService;

    @Mock
    private PaForNodeService govpayService;

    private RtRetrieveProcessor processor;
    private RtRetrieveContext context;

    private static final Long RT_ID = 1L;
    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";

    @BeforeEach
    void setUp() {
        processor = new RtRetrieveProcessor(rtApiService, govpayService);

        context = RtRetrieveContext.builder()
                .rtId(RT_ID)
                .taxCode(TAX_CODE)
                .iuv(IUV)
                .iur(IUR)
                .build();
    }

    @Nested
    @DisplayName("process")
    class ProcessTest {

        @Test
        @DisplayName("should return batch with retrivedTime when receipt retrieved and sent successfully")
        void shouldReturnBatchWithRetrivedTimeWhenSuccess() throws Exception {
            PaSendRTV2Request request = new PaSendRTV2Request();
            when(rtApiService.retrieveReceipt(eq(context), any(CompletableFuture.class))).thenReturn(request);
            when(govpayService.sendReceipt(context, request)).thenReturn(true);

            RtRetrieveBatch result = processor.process(context);

            assertNotNull(result);
            assertEquals(RT_ID, result.getRtId());
            assertEquals(TAX_CODE, result.getCodDominio());
            assertEquals(IUV, result.getIuv());
            assertEquals(IUR, result.getIur());
            assertNotNull(result.getRetrivedTime());
            assertNull(result.getMessage());
        }

        @Test
        @DisplayName("should return batch with error message when send to govpay fails")
        void shouldReturnBatchWithErrorMessageWhenSendFails() throws Exception {
            PaSendRTV2Request request = new PaSendRTV2Request();
            when(rtApiService.retrieveReceipt(eq(context), any(CompletableFuture.class))).thenReturn(request);
            when(govpayService.sendReceipt(context, request)).thenReturn(false);

            RtRetrieveBatch result = processor.process(context);

            assertNotNull(result);
            assertEquals(RT_ID, result.getRtId());
            assertEquals("Send to govpay failed", result.getMessage());
            assertNull(result.getRetrivedTime());
        }

        @Test
        @DisplayName("should return batch with not found message when receipt not found")
        void shouldReturnBatchWithNotFoundMessageWhenReceiptNotFound() throws Exception {
            when(rtApiService.retrieveReceipt(eq(context), any(CompletableFuture.class)))
                    .thenAnswer(invocation -> {
                        CompletableFuture<HttpStatusCode> future = invocation.getArgument(1);
                        future.complete(HttpStatus.NOT_FOUND);
                        return null;
                    });

            RtRetrieveBatch result = processor.process(context);

            assertNotNull(result);
            assertEquals(RT_ID, result.getRtId());
            assertEquals("Receipt not found", result.getMessage());
            assertNull(result.getRetrivedTime());
        }

        @Test
        @DisplayName("should return null when retrieveReceipt returns null without NOT_FOUND status")
        void shouldReturnNullWhenRetrieveReceiptReturnsNullWithoutNotFoundStatus() throws Exception {
            when(rtApiService.retrieveReceipt(eq(context), any(CompletableFuture.class)))
                    .thenAnswer(invocation -> {
                        // Future not completed
                        return null;
                    });

            RtRetrieveBatch result = processor.process(context);

            assertNull(result);
        }

        @Test
        @DisplayName("should propagate exception when rtApiService throws")
        void shouldPropagateExceptionWhenRtApiServiceThrows() throws Exception {
            RuntimeException exception = new RuntimeException("API Error");
            when(rtApiService.retrieveReceipt(eq(context), any(CompletableFuture.class))).thenThrow(exception);

            assertThrows(RuntimeException.class, () -> processor.process(context));
            verify(govpayService, never()).sendReceipt(any(), any());
        }

        @Test
        @DisplayName("should call services in correct order")
        void shouldCallServicesInCorrectOrder() throws Exception {
            PaSendRTV2Request request = new PaSendRTV2Request();
            when(rtApiService.retrieveReceipt(eq(context), any(CompletableFuture.class))).thenReturn(request);
            when(govpayService.sendReceipt(context, request)).thenReturn(true);

            processor.process(context);

            var inOrder = inOrder(rtApiService, govpayService);
            inOrder.verify(rtApiService).retrieveReceipt(eq(context), any(CompletableFuture.class));
            inOrder.verify(govpayService).sendReceipt(context, request);
        }
    }
}
