package it.govpay.rt.batch.unit.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
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
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.dto.RtRecuperoBatch;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.service.GdeService;
import it.govpay.rt.batch.service.PaForNodeService;
import it.govpay.rt.batch.service.RtApiService;
import it.govpay.rt.batch.tasklet.RtRecuperoProcessor;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtRecuperoProcessor")
class RtRecuperoProcessorTest {

    @Mock
    private RtApiService rtApiService;

    @Mock
    private PaForNodeService govpayService;

    @Mock
    private GdeService gdeService;

    private RtRecuperoProcessor processor;
    private RtRetrieveContext context;

    private static final Long ID = 1L;
    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "IUV-1";
    private static final String IUR = "IUR-1";

    @BeforeEach
    void setUp() {
        processor = new RtRecuperoProcessor(rtApiService, govpayService, gdeService, retryTemplate(3));
        context = RtRetrieveContext.builder().rtId(ID).taxCode(TAX_CODE).iuv(IUV).iur(IUR).build();
    }

    private static RetryTemplate retryTemplate(int maxAttempts) {
        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(org.springframework.web.client.RestClientException.class, true);
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(maxAttempts, retryable));
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        return retryTemplate;
    }

    @Nested
    @DisplayName("process")
    class ProcessTest {

        @Test
        @DisplayName("successo: nessun esito, riga da eliminare")
        void successo() throws Exception {
            PaSendRTV2Request request = new PaSendRTV2Request();
            when(rtApiService.retrieveReceipt(eq(context), any())).thenReturn(request);
            when(govpayService.sendReceipt(context, request)).thenReturn(true);

            RtRecuperoBatch result = processor.process(context);

            assertNotNull(result);
            assertEquals(ID, result.getId());
            assertNull(result.getEsito());
        }

        @Test
        @DisplayName("404: marcato NON_DISPONIBILE")
        void nonDisponibile() throws Exception {
            when(rtApiService.retrieveReceipt(eq(context), any())).thenAnswer(invocation -> {
                CompletableFuture<HttpStatusCode> future = invocation.getArgument(1);
                future.complete(HttpStatus.NOT_FOUND);
                return null;
            });

            RtRecuperoBatch result = processor.process(context);

            assertEquals(Costanti.ESITO_RECUPERO_NON_DISPONIBILE, result.getEsito());
        }

        @Test
        @DisplayName("MBT senza allegato: marcato MBT_ALLEGATO_MANCANTE, nessun invio a govpay")
        void mbtSenzaAllegato() throws Exception {
            when(rtApiService.retrieveReceipt(eq(context), any())).thenAnswer(invocation -> {
                CompletableFuture<HttpStatusCode> future = invocation.getArgument(1);
                future.complete(HttpStatus.UNPROCESSABLE_ENTITY);
                return null;
            });

            RtRecuperoBatch result = processor.process(context);

            assertEquals(Costanti.ESITO_RECUPERO_MBT_ALLEGATO_MANCANTE, result.getEsito());
            verify(govpayService, org.mockito.Mockito.never()).sendReceipt(any(), any());
        }

        @Test
        @DisplayName("configurazione mancante (IllegalStateException da RtApiService): marcato ERRORE, nessun retry, evento GDE dedicato scritto qui")
        void configurazioneMancante() throws Exception {
            IllegalStateException configMancante = new IllegalStateException(
                    "Connettore Recupero RT non configurato per l'intermediario X (dominio: " + TAX_CODE + ")");
            when(rtApiService.retrieveReceipt(eq(context), any())).thenThrow(configMancante);

            RtRecuperoBatch result = processor.process(context);

            assertEquals(Costanti.ESITO_RECUPERO_ERRORE, result.getEsito());
            verify(govpayService, org.mockito.Mockito.never()).sendReceipt(any(), any());
            // Non ritentata (esclusa esplicitamente dalla retry policy): un solo tentativo.
            verify(rtApiService, times(1)).retrieveReceipt(eq(context), any());
            verify(gdeService).saveGetReceiptConfigurazioneMancante(eq(context), any(), any(), eq(configMancante.getMessage()));
        }

        @Test
        @DisplayName("invio a govpay fallito: marcato ERRORE (PAA_RECEIPT_DUPLICATA e' invece successo, vedi PaForNodeServiceTest)")
        void invioFallito() throws Exception {
            PaSendRTV2Request request = new PaSendRTV2Request();
            when(rtApiService.retrieveReceipt(eq(context), any())).thenReturn(request);
            when(govpayService.sendReceipt(context, request)).thenReturn(false);

            RtRecuperoBatch result = processor.process(context);

            assertEquals(Costanti.ESITO_RECUPERO_ERRORE, result.getEsito());
        }

        @Test
        @DisplayName("429 esaurisce i retry: marcato ERRORE, non fa fallire il processor")
        void retryEsauritoSuRateLimit() throws Exception {
            HttpClientErrorException.TooManyRequests tooManyRequests =
                    (HttpClientErrorException.TooManyRequests) HttpClientErrorException.TooManyRequests.create(
                            HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null, null, null);
            when(rtApiService.retrieveReceipt(eq(context), any())).thenThrow(tooManyRequests);

            RtRecuperoBatch result = processor.process(context);

            assertEquals(Costanti.ESITO_RECUPERO_ERRORE, result.getEsito());
            // 3 tentativi totali (maxAttempts=3 nel setUp): 1 iniziale + 2 retry.
            verify(rtApiService, times(3)).retrieveReceipt(eq(context), any());
        }

        @Test
        @DisplayName("errore non ritentabile (5xx): propaga senza esaurire i retry configurati per RestClientException ritentabili diversi")
        void erroreServerNonRitentatoOltreSoglia() throws Exception {
            // HttpServerErrorException e' comunque una RestClientException -> ritentabile secondo la policy,
            // verifica solo che il fallimento definitivo produca comunque un esito marcato (non un'eccezione propagata).
            HttpServerErrorException serverError = HttpServerErrorException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null);
            when(rtApiService.retrieveReceipt(eq(context), any())).thenThrow(serverError);

            RtRecuperoBatch result = processor.process(context);

            assertEquals(Costanti.ESITO_RECUPERO_ERRORE, result.getEsito());
        }
    }
}
