package it.govpay.rt.batch.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.service.GdeService;
import it.govpay.rt.batch.service.RtApiService;
import it.govpay.rt.client.api.PaymentReceiptsRestApisApi;
import it.govpay.rt.client.model.CtReceiptModelResponse;
import it.govpay.rt.client.model.Debtor;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtApiService")
class RtApiServiceTest {

    @Mock
    private PaymentReceiptsRestApisApi paymentRtRestApi;

    @Mock
    private GdeService gdeService;

    private RtApiService service;
    private RtRetrieveContext rtInfo;
    private CompletableFuture<HttpStatusCode> statusCodeFuture;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";
    private static final String INTERMEDIARY_ID = "12345678901";
    private static final String STATION_ID = "12345678901_01";

    @BeforeEach
    void setUp() {
        service = new RtApiService(paymentRtRestApi, gdeService);
        ReflectionTestUtils.setField(service, "intermediaryId", INTERMEDIARY_ID);
        ReflectionTestUtils.setField(service, "stationId", STATION_ID);

        rtInfo = RtRetrieveContext.builder()
                .rtId(1L)
                .taxCode(TAX_CODE)
                .iuv(IUV)
                .iur(IUR)
                .build();

        statusCodeFuture = new CompletableFuture<>();
    }

    private CtReceiptModelResponse createValidReceipt() {
        CtReceiptModelResponse receipt = new CtReceiptModelResponse();
        receipt.setReceiptId("receipt-123");
        receipt.setNoticeNumber("302000000000000001");
        receipt.setFiscalCode(TAX_CODE);
        receipt.setOutcome("OK");
        receipt.setCreditorReferenceId(IUV);
        receipt.setPaymentAmount(new BigDecimal("100.50"));
        receipt.setDescription("Test payment");
        receipt.setCompanyName("Test Company");
        receipt.setIdPSP("AGID_01");
        receipt.setApplicationDate(LocalDate.now());
        receipt.setTransferDate(LocalDate.now());

        Debtor debtor = new Debtor();
        debtor.setFullName("Mario Rossi");
        debtor.setEntityUniqueIdentifierType(Debtor.EntityUniqueIdentifierTypeEnum.F);
        debtor.setEntityUniqueIdentifierValue("RSSMRA80A01H501U");
        receipt.setDebtor(debtor);

        return receipt;
    }

    @Nested
    @DisplayName("retrieveReceipt")
    class RetrieveReceiptTest {

        @Test
        @DisplayName("should return PaSendRTV2Request when response is OK")
        void shouldReturnPaSendRTV2RequestWhenResponseIsOk() {
            CtReceiptModelResponse receipt = createValidReceipt();
            ResponseEntity<CtReceiptModelResponse> response = ResponseEntity.ok(receipt);

            when(paymentRtRestApi.getOrganizationReceiptIuvIurWithHttpInfo(
                    eq(TAX_CODE), eq(IUR), eq(IUV), isNull()))
                    .thenReturn(response);

            PaSendRTV2Request result = service.retrieveReceipt(rtInfo, statusCodeFuture);

            assertNotNull(result);
            assertEquals(INTERMEDIARY_ID, result.getIdBrokerPA());
            assertEquals(STATION_ID, result.getIdStation());
            assertEquals(TAX_CODE, result.getIdPA());
            assertNotNull(result.getReceipt());

            assertTrue(statusCodeFuture.isDone());
            assertEquals(HttpStatus.OK, statusCodeFuture.join());

            verify(gdeService).saveGetReceiptOk(eq(rtInfo), eq(response), any(), any());
        }

        @Test
        @DisplayName("should return null and log KO event when receipt not found")
        void shouldReturnNullWhenReceiptNotFound() {
            ResponseEntity<CtReceiptModelResponse> response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();

            when(paymentRtRestApi.getOrganizationReceiptIuvIurWithHttpInfo(
                    eq(TAX_CODE), eq(IUR), eq(IUV), isNull()))
                    .thenReturn(response);

            PaSendRTV2Request result = service.retrieveReceipt(rtInfo, statusCodeFuture);

            assertNull(result);
            assertTrue(statusCodeFuture.isDone());
            assertEquals(HttpStatus.NOT_FOUND, statusCodeFuture.join());

            verify(gdeService).saveGetReceiptKo(eq(rtInfo), eq(response), any(RestClientException.class), any(), any());
        }

        @Test
        @DisplayName("should throw RestClientException when rate limit reached")
        void shouldThrowWhenRateLimitReached() {
            ResponseEntity<CtReceiptModelResponse> response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();

            when(paymentRtRestApi.getOrganizationReceiptIuvIurWithHttpInfo(
                    eq(TAX_CODE), eq(IUR), eq(IUV), isNull()))
                    .thenReturn(response);

            RestClientException exception = assertThrows(RestClientException.class,
                    () -> service.retrieveReceipt(rtInfo, statusCodeFuture));

            assertEquals("Rate limit reached", exception.getMessage());
        }

        @Test
        @DisplayName("should throw RestClientException on 5xx server error")
        void shouldThrowOn5xxServerError() {
            ResponseEntity<CtReceiptModelResponse> response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

            when(paymentRtRestApi.getOrganizationReceiptIuvIurWithHttpInfo(
                    eq(TAX_CODE), eq(IUR), eq(IUV), isNull()))
                    .thenReturn(response);

            RestClientException exception = assertThrows(RestClientException.class,
                    () -> service.retrieveReceipt(rtInfo, statusCodeFuture));

            assertEquals("Server error to retrieve missing receipt", exception.getMessage());
        }

        @Test
        @DisplayName("should throw RestClientException on unexpected status code")
        void shouldThrowOnUnexpectedStatusCode() {
            ResponseEntity<CtReceiptModelResponse> response = ResponseEntity.status(HttpStatus.FORBIDDEN).build();

            when(paymentRtRestApi.getOrganizationReceiptIuvIurWithHttpInfo(
                    eq(TAX_CODE), eq(IUR), eq(IUV), isNull()))
                    .thenReturn(response);

            RestClientException exception = assertThrows(RestClientException.class,
                    () -> service.retrieveReceipt(rtInfo, statusCodeFuture));

            assertEquals("Fail to retrieve missing receipt", exception.getMessage());
        }

        @Test
        @DisplayName("should throw and log KO event when API throws exception")
        void shouldThrowAndLogKoEventWhenApiThrowsException() {
            RestClientException apiException = new RestClientException("Connection refused");

            when(paymentRtRestApi.getOrganizationReceiptIuvIurWithHttpInfo(
                    eq(TAX_CODE), eq(IUR), eq(IUV), isNull()))
                    .thenThrow(apiException);

            RestClientException exception = assertThrows(RestClientException.class,
                    () -> service.retrieveReceipt(rtInfo, statusCodeFuture));

            assertSame(apiException, exception);
            verify(gdeService).saveGetReceiptKo(eq(rtInfo), isNull(), eq(apiException), any(), any());
        }

        @Test
        @DisplayName("should work when GdeService is null")
        void shouldWorkWhenGdeServiceIsNull() {
            service = new RtApiService(paymentRtRestApi, null);
            ReflectionTestUtils.setField(service, "intermediaryId", INTERMEDIARY_ID);
            ReflectionTestUtils.setField(service, "stationId", STATION_ID);

            CtReceiptModelResponse receipt = createValidReceipt();
            ResponseEntity<CtReceiptModelResponse> response = ResponseEntity.ok(receipt);

            when(paymentRtRestApi.getOrganizationReceiptIuvIurWithHttpInfo(
                    eq(TAX_CODE), eq(IUR), eq(IUV), isNull()))
                    .thenReturn(response);

            // Should throw NullPointerException when trying to call gdeService
            assertThrows(NullPointerException.class,
                    () -> service.retrieveReceipt(rtInfo, statusCodeFuture));
        }
    }
}
