package it.govpay.rt.batch.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
	private JobLauncher jobLauncher;

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
		JobExecution jobExecution = jobLauncher.run(rtRetrieveJob, uniqueJobParameters());

		// Then: job completed with no processing
		assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());

		// Verify no REST or SOAP calls were made
		mockRestServer.verify();
		mockWsServer.verify();
	}

	private org.springframework.batch.core.JobParameters uniqueJobParameters() {
		return new JobParametersBuilder()
				.addLong("run.id", System.currentTimeMillis())
				.toJobParameters();
	}
}
