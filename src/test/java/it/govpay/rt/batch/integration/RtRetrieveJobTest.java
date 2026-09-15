package it.govpay.rt.batch.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.entity.DominioEntity;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.entity.StazioneEntity;
import it.govpay.common.repository.DominioRepository;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.rt.batch.client.GovpayClient;
import it.govpay.rt.batch.entity.Fr;
import it.govpay.rt.batch.entity.Rendicontazione;
import it.govpay.rt.batch.entity.SingoloVersamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@ActiveProfiles("integration-e2e")
@Import(RtRetrieveJobTest.TestConfig.class)
@DisplayName("RtRetrieveJob End-to-End Integration Test")
class RtRetrieveJobTest {

	private static final String TAX_CODE = "12345678901";
	private static final String INTERMEDIARY_ID = "15376371009";
	private static final String STATION_ID = "15376371009_01";
	private static final String COD_CONNETTORE_RT = "COD_CONNETTORE_RT_TEST";
	private static final String PAGOPA_BASE_URL = "http://localhost";

	@TestConfiguration
	static class TestConfig {
		@Bean(name = "asyncHttpExecutor")
		public Executor asyncHttpExecutor() {
			return Runnable::run;
		}

		@Bean
		public RestTemplate testPagoPARestTemplate() {
			return new RestTemplate();
		}
	}

	@Autowired
	private Job rtRetrieveJob;

	@Autowired
	private JobOperator jobOperator;

	@Autowired
	private RestTemplate testPagoPARestTemplate;

	@Autowired
	private GovpayClient govpayClient;

	@MockitoBean
	private ConnettoreService connettoreService;

	@MockitoBean
	private IntermediarioRepository intermediarioRepository;

	@MockitoBean
	private DominioRepository dominioRepository;

	@MockitoBean
	private ConfigurazioneService configurazioneService;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private MockRestServiceServer mockRestServer;
	private MockWebServiceServer mockWsServer;

	@BeforeEach
	void setUp() {
		mockRestServer = MockRestServiceServer.createServer(testPagoPARestTemplate);
		mockWsServer = MockWebServiceServer.createServer(govpayClient);

		// Clean DB data between tests (respect FK order)
		transactionTemplate.executeWithoutResult(status -> {
			entityManager.createQuery("DELETE FROM Rendicontazione").executeUpdate();
			entityManager.createQuery("DELETE FROM Fr").executeUpdate();
			entityManager.createQuery("DELETE FROM SingoloVersamento").executeUpdate();
			entityManager.createQuery("DELETE FROM DominioEntity").executeUpdate();
		});

		// Setup connettore mocks
		IntermediarioEntity intermediario = IntermediarioEntity.builder()
				.codIntermediario(INTERMEDIARY_ID)
				.codConnettoreRecuperoRt(COD_CONNETTORE_RT)
				.build();
		when(intermediarioRepository.findByCodDominio(TAX_CODE))
				.thenReturn(Optional.of(intermediario));

		when(connettoreService.getRestTemplate(COD_CONNETTORE_RT)).thenReturn(testPagoPARestTemplate);

		Connettore connettore = new Connettore();
		connettore.setUrl(PAGOPA_BASE_URL);
		when(connettoreService.getConnettore(COD_CONNETTORE_RT)).thenReturn(connettore);

		// Setup domain info for intermediaryId/stationId resolution
		IntermediarioEntity domIntermediario = IntermediarioEntity.builder()
				.codIntermediario(INTERMEDIARY_ID)
				.build();
		StazioneEntity stazione = StazioneEntity.builder()
				.codStazione(STATION_ID)
				.intermediario(domIntermediario)
				.build();
		DominioEntity dominioEntity = DominioEntity.builder()
				.codDominio(TAX_CODE)
				.stazione(stazione)
				.build();
		when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(dominioEntity));

		// GDE disabled for integration tests (simplifies setup)
		when(configurazioneService.isServizioGDEAbilitato()).thenReturn(false);

		reset(connettoreService, intermediarioRepository, dominioRepository);
		// Re-setup after reset
		when(intermediarioRepository.findByCodDominio(TAX_CODE))
				.thenReturn(Optional.of(intermediario));
		when(connettoreService.getRestTemplate(COD_CONNETTORE_RT)).thenReturn(testPagoPARestTemplate);
		when(connettoreService.getConnettore(COD_CONNETTORE_RT)).thenReturn(connettore);
		when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(dominioEntity));
		when(configurazioneService.isServizioGDEAbilitato()).thenReturn(false);
	}

	@Test
	@DisplayName("No rendicontazioni to process: job completes immediately")
	void noRendicontazioni_jobCompletesImmediately() throws Exception {
		// Given: empty database (no rendicontazioni to process)

		// When: launch the job
		JobExecution jobExecution = jobOperator.start(rtRetrieveJob, uniqueJobParameters());

		// Then: job completed with no processing
		assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());

		// Verify no REST or SOAP calls were made
		mockRestServer.verify();
		mockWsServer.verify();
	}

	/**
	 * govpay-rt-batch#21 punto 1+3: dimostra il flusso completo su due
	 * esecuzioni, non solo i due comportamenti isolati (processor marca
	 * retryable, writer non disabilita) gia' coperti dagli unit test.
	 * Senza watermark, la rendicontazione resta candidata a ogni giro finche'
	 * il recupero non riesce o esce dalla finestra temporale.
	 */
	@Test
	@DisplayName("A 404 (Receipt not found) leaves esegui_recupero_rt=true: the row is retried on the next job execution")
	void notFoundReceiptIsRetriedOnNextExecution() throws Exception {
		// Given: a rendicontazione candidate for automatic RT retrieval
		Long rndId = persistCandidateRendicontazione();

		// First execution: pagoPA answers 404 (Receipt not found)
		mockRestServer.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		JobExecution first = jobOperator.start(rtRetrieveJob, uniqueJobParameters());
		assertEquals(ExitStatus.COMPLETED, first.getExitStatus());
		mockRestServer.verify();

		assertTrue(currentEseguiRecuperoRt(rndId), "esegui_recupero_rt deve restare true dopo un 404");

		// Second execution: a fresh MockRestServiceServer (new expectation) proves the
		// SAME row is selected again — if it weren't retried, no second GET would occur
		// and verify() below would fail.
		mockRestServer = MockRestServiceServer.createServer(testPagoPARestTemplate);
		mockRestServer.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		JobExecution second = jobOperator.start(rtRetrieveJob, uniqueJobParameters());
		assertEquals(ExitStatus.COMPLETED, second.getExitStatus());
		mockRestServer.verify();

		assertTrue(currentEseguiRecuperoRt(rndId), "esegui_recupero_rt deve restare true anche dopo il secondo tentativo");
	}

	private Long persistCandidateRendicontazione() {
		return transactionTemplate.execute(status -> {
			DominioEntity dominio = DominioEntity.builder().codDominio(TAX_CODE)
					.abilitato(true).ragioneSociale("Test").auxDigit(0).intermediato(true).scaricaFr(false).build();
			entityManager.persist(dominio);

			Fr fr = Fr.builder().dominio(dominio).build();
			entityManager.persist(fr);

			SingoloVersamento sv = SingoloVersamento.builder().build();
			entityManager.persist(sv);

			Rendicontazione rnd = Rendicontazione.builder()
					.fr(fr)
					.singoloVersamento(sv)
					.iuv("01234567890123456")
					.iur("IUR123456789")
					.data(LocalDateTime.now())
					.idPagamento(null)
					.eseguiRecuperoRt(true)
					.build();
			entityManager.persist(rnd);
			entityManager.flush();
			return rnd.getId();
		});
	}

	private boolean currentEseguiRecuperoRt(Long id) {
		return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
			entityManager.clear();
			return entityManager.find(Rendicontazione.class, id).getEseguiRecuperoRt();
		}));
	}

	private org.springframework.batch.core.job.parameters.JobParameters uniqueJobParameters() {
		return new JobParametersBuilder()
				.addLong("run.id", System.currentTimeMillis())
				.toJobParameters();
	}
}
