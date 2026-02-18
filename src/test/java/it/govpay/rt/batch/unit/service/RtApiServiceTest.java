package it.govpay.rt.batch.unit.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.DominioRepository;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.rt.batch.config.RtApiClientConfig;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.service.GdeService;
import it.govpay.rt.batch.service.RtApiService;

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

    private RtApiService service;
    private RtRetrieveContext rtInfo;
    private CompletableFuture<HttpStatusCode> statusCodeFuture;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";
    private static final String INTERMEDIARY_ID = "12345678901";

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
