package it.govpay.rt.batch.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.DominioEntity;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.entity.StazioneEntity;
import it.govpay.common.repository.DominioRepository;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.rt.batch.config.RtApiClientConfig;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.service.GdeService;
import it.govpay.rt.batch.service.RtApiService;
import it.govpay.rt.client.ApiClient;
import it.govpay.rt.client.api.PaymentReceiptsRestApisApi;
import it.govpay.rt.client.model.CtReceiptModelResponse;
import it.govpay.rt.client.model.Debtor;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtApiService")
class RtApiServiceTest {

    @Mock
    private ConnettoreService connettoreService;

    @Mock
    private IntermediarioRepository intermediarioRepository;

    @Mock
    private DominioRepository dominioRepository;

    @Mock
    private RtApiClientConfig rtApiClientConfig;

    @Mock
    private GdeService gdeService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PaymentReceiptsRestApisApi paymentRtRestApi;

    private RtApiService service;
    private RtRetrieveContext rtInfo;
    private CompletableFuture<HttpStatusCode> statusCodeFuture;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";
    private static final String INTERMEDIARY_ID = "12345678901";
    private static final String STATION_ID = "12345678901_01";
    private static final String COD_CONNETTORE_RT = "COD_CONNETTORE_RT_TEST";
    private static final String PAGOPA_BASE_URL = "https://api.pagopa.it";

    @BeforeEach
    void setUp() {
        service = new RtApiService(connettoreService, intermediarioRepository, dominioRepository,
                rtApiClientConfig, gdeService);

        rtInfo = RtRetrieveContext.builder()
                .rtId(1L)
                .taxCode(TAX_CODE)
                .iuv(IUV)
                .iur(IUR)
                .build();

        statusCodeFuture = new CompletableFuture<>();
    }

    private void setupConnector() {
        IntermediarioEntity intermediario = IntermediarioEntity.builder()
                .codIntermediario(INTERMEDIARY_ID)
                .codConnettoreRecuperoRt(COD_CONNETTORE_RT)
                .build();
        when(intermediarioRepository.findByCodDominio(TAX_CODE))
                .thenReturn(Optional.of(intermediario));

        when(connettoreService.getRestTemplate(COD_CONNETTORE_RT)).thenReturn(restTemplate);
        when(rtApiClientConfig.createPagoPAObjectMapper()).thenReturn(new ObjectMapper());

        Connettore connettore = new Connettore();
        connettore.setUrl(PAGOPA_BASE_URL);
        when(connettoreService.getConnettore(COD_CONNETTORE_RT)).thenReturn(connettore);
    }

    private void setupDomainInfo() {
        IntermediarioEntity intermediario = IntermediarioEntity.builder()
                .codIntermediario(INTERMEDIARY_ID)
                .build();
        StazioneEntity stazione = StazioneEntity.builder()
                .codStazione(STATION_ID)
                .intermediario(intermediario)
                .build();
        DominioEntity dominio = DominioEntity.builder()
                .codDominio(TAX_CODE)
                .stazione(stazione)
                .build();
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(dominio));
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
        @DisplayName("should throw when no intermediario found for domain")
        void shouldThrowWhenNoIntermediarioFound() {
            when(intermediarioRepository.findByCodDominio(TAX_CODE))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalStateException.class,
                    () -> service.retrieveReceipt(rtInfo, statusCodeFuture));
        }

        @Test
        @DisplayName("should throw when connettore RT not configured")
        void shouldThrowWhenConnettoreRtNotConfigured() {
            IntermediarioEntity intermediario = IntermediarioEntity.builder()
                    .codIntermediario(INTERMEDIARY_ID)
                    .codConnettoreRecuperoRt(null)
                    .build();
            when(intermediarioRepository.findByCodDominio(TAX_CODE))
                    .thenReturn(Optional.of(intermediario));

            assertThrows(IllegalStateException.class,
                    () -> service.retrieveReceipt(rtInfo, statusCodeFuture));
        }
    }
}
