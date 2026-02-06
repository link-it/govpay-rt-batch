package it.govpay.rt.batch.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.entity.Dominio;
import it.govpay.rt.batch.entity.Fr;
import it.govpay.rt.batch.entity.Rendicontazione;
import it.govpay.rt.batch.entity.SingoloVersamento;
import it.govpay.rt.batch.repository.RendicontazioniRepository;
import it.govpay.rt.batch.service.PaForNodeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("integration")
@WireMockTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("RT Retrieve Job Integration Test")
class RtRetrieveJobIT {

    private static int wireMockPort;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job rtRetrieveJob;

    @Autowired
    private RendicontazioniRepository rendicontazioniRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456789";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("wiremock.server.port", () -> wireMockPort);
        registry.add("pagopa.rt.base-url", () -> "http://localhost:" + wireMockPort);
    }

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        wireMockPort = wmRuntimeInfo.getHttpPort();
        jobLauncherTestUtils.setJob(rtRetrieveJob);
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public PaForNodeService mockPaForNodeService() {
            return new PaForNodeService(null, null) {
                @Override
                public boolean sendReceipt(RtRetrieveContext rtInfo, PaSendRTV2Request request) {
                    return true;
                }
            };
        }
    }

    @Test
    @DisplayName("should complete successfully when receipt is found")
    void shouldCompleteSuccessfullyWhenReceiptIsFound() throws Exception {
        // Given: a rendicontazione record with singoloVersamento but no idPagamento
        createTestDataInTransaction();

        // And: WireMock returns a valid receipt
        stubFor(get(urlPathMatching("/organizations/.*/receipts/.*/iuv/.*"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(createReceiptJson())));

        // When: run the batch job
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then: job completes successfully
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());

        // And: the pagoPA API was called
        verify(getRequestedFor(urlPathMatching("/organizations/.*/receipts/.*/iuv/.*")));
    }

    @Test
    @DisplayName("should complete with no processing when no records to retrieve")
    void shouldCompleteWithNoProcessingWhenNoRecords() throws Exception {
        // Given: no rendicontazione records

        // When: run the batch job
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then: job completes successfully
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());

        // And: no API calls were made
        verify(0, getRequestedFor(urlPathMatching("/organizations/.*")));
    }

    @Test
    @DisplayName("should handle receipt not found gracefully")
    void shouldHandleReceiptNotFoundGracefully() throws Exception {
        // Given: a rendicontazione record
        createTestDataInTransaction();

        // And: WireMock returns 404
        stubFor(get(urlPathMatching("/organizations/.*/receipts/.*/iuv/.*"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.NOT_FOUND.value())));

        // When: run the batch job
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then: job completes (processor returns null for not found, writer skips)
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
    }

    @Test
    @DisplayName("should process multiple records in sequence")
    void shouldProcessMultipleRecordsInSequence() throws Exception {
        // Given: multiple rendicontazione records
        createTestDataMultipleInTransaction(3);

        // And: WireMock returns valid receipts
        stubFor(get(urlPathMatching("/organizations/.*/receipts/.*/iuv/.*"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(createReceiptJson())));

        // When: run the batch job
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then: job completes successfully
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());

        // And: API was called 3 times (once per record)
        verify(3, getRequestedFor(urlPathMatching("/organizations/.*/receipts/.*/iuv/.*")));
    }

    private void createTestDataInTransaction() {
        transactionTemplate.execute(status -> {
            createSingleTestData(IUV, IUR);
            return null;
        });
    }

    private void createTestDataMultipleInTransaction(int count) {
        transactionTemplate.execute(status -> {
            for (int i = 0; i < count; i++) {
                createSingleTestData(IUV + "_" + i, IUR + "_" + i);
            }
            return null;
        });
    }

    private Rendicontazione createSingleTestData(String iuv, String iur) {
        // Create Dominio
        Dominio dominio = Dominio.builder()
                .codDominio(TAX_CODE)
                .build();
        entityManager.persist(dominio);

        // Create Fr
        Fr fr = Fr.builder()
                .dominio(dominio)
                .build();
        entityManager.persist(fr);

        // Create SingoloVersamento
        SingoloVersamento sv = SingoloVersamento.builder()
                .build();
        entityManager.persist(sv);

        // Create Rendicontazione with singoloVersamento but NO idPagamento
        Rendicontazione rnd = Rendicontazione.builder()
                .fr(fr)
                .singoloVersamento(sv)
                .iuv(iuv)
                .iur(iur)
                .data(LocalDateTime.now())
                .idPagamento(null)  // This is the key: no payment yet
                .build();
        entityManager.persist(rnd);

        return rnd;
    }

    private String createReceiptJson() {
        return """
            {
                "receiptId": "receipt-123",
                "noticeNumber": "302000000000000001",
                "fiscalCode": "%s",
                "outcome": "OK",
                "creditorReferenceId": "%s",
                "paymentAmount": 100.50,
                "description": "Test payment",
                "companyName": "Test Company",
                "idPSP": "AGID_01",
                "pspFiscalCode": "97735020584",
                "pspPartitaIVA": "97735020584",
                "pspCompanyName": "Test PSP",
                "idChannel": "97735020584_01",
                "channelDescription": "Test Channel",
                "paymentMethod": "CP",
                "fee": 1.50,
                "primaryCiIncurredFee": 0.50,
                "idBundle": "bundle-123",
                "idCiBundle": "ci-bundle-123",
                "applicationDate": "2024-01-15",
                "transferDate": "2024-01-15",
                "debtor": {
                    "fullName": "Mario Rossi",
                    "entityUniqueIdentifierType": "F",
                    "entityUniqueIdentifierValue": "RSSMRA80A01H501U"
                },
                "transferList": [
                    {
                        "idTransfer": "1",
                        "fiscalCodePA": "%s",
                        "companyName": "Test Company",
                        "amount": 100.50,
                        "transferCategory": "9/0101108TS/",
                        "remittanceInformation": "Test remittance"
                    }
                ]
            }
            """.formatted(TAX_CODE, IUV, TAX_CODE);
    }
}
