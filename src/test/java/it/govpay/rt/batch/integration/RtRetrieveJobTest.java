package it.govpay.rt.batch.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.xml.transform.Source;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.ws.test.client.ResponseCreators;
import org.springframework.xml.transform.StringSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.StOutcome;
import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.entity.DominioEntity;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.entity.StazioneEntity;
import it.govpay.common.repository.DominioRepository;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.rt.batch.client.GovpayClient;
import it.govpay.rt.batch.entity.Dominio;
import it.govpay.rt.batch.entity.Fr;
import it.govpay.rt.batch.entity.Rendicontazione;
import it.govpay.rt.batch.entity.SingoloVersamento;
import it.govpay.rt.batch.repository.RendicontazioniRepository;
import it.govpay.rt.client.model.CtReceiptModelResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

@SpringBootTest
@ActiveProfiles("integration-e2e")
@Import(RtRetrieveJobTest.TestConfig.class)
@DisplayName("RtRetrieveJob End-to-End Integration Test")
class RtRetrieveJobTest {

	private static final String TAX_CODE = "12345678901";
	private static final String IUV = "01234567890123456";
	private static final String IUR = "IUR123456789";
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
	private RendicontazioniRepository rendicontazioniRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private Jaxb2Marshaller marshaller;

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
			entityManager.createQuery("DELETE FROM Dominio").executeUpdate();
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

	private void insertTestData(String taxCode, String iuv, String iur, Long idPagamento) {
		transactionTemplate.executeWithoutResult(status -> {
			Dominio dominio = Dominio.builder().codDominio(taxCode).build();
			entityManager.persist(dominio);

			Fr fr = Fr.builder().dominio(dominio).build();
			entityManager.persist(fr);

			SingoloVersamento sv = SingoloVersamento.builder().build();
			entityManager.persist(sv);

			Rendicontazione rnd = Rendicontazione.builder()
					.fr(fr)
					.singoloVersamento(sv)
					.iuv(iuv)
					.iur(iur)
					.data(LocalDateTime.now())
					.idPagamento(idPagamento)
					.eseguiRecuperoRt(true)
					.build();
			entityManager.persist(rnd);
		});
	}

	private CtReceiptModelResponse createReceiptResponse() {
		CtReceiptModelResponse r = new CtReceiptModelResponse();
		r.setReceiptId("receipt-123");
		r.setNoticeNumber("302000000000000001");
		r.setFiscalCode(TAX_CODE);
		r.setOutcome("OK");
		r.setCreditorReferenceId(IUV);
		r.setPaymentAmount(new BigDecimal("100.50"));
		r.setDescription("Test payment");
		r.setCompanyName("Test Company");
		r.setIdPSP("AGID_01");
		r.setIdChannel("AGID_01_ONUS");
		r.setPspCompanyName("PSP Company");
		r.setPaymentMethod("CARD");
		r.setFee(new BigDecimal("1.50"));
		r.setPaymentDateTimeFormatted(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC));
		r.setApplicationDate(LocalDate.of(2024, 1, 15));
		r.setTransferDate(LocalDate.of(2024, 1, 16));
		return r;
	}

	private Source marshalSoapResponse(StOutcome outcome) {
		PaSendRTV2Response response = new PaSendRTV2Response();
		response.setOutcome(outcome);

		java.io.StringWriter sw = new java.io.StringWriter();
		try {
			marshaller.createMarshaller().marshal(
					new JAXBElement<>(
							new QName("http://pagopa-api.pagopa.gov.it/pa/paForNode.xsd", "paSendRTV2Response"),
							PaSendRTV2Response.class,
							response),
					new javax.xml.transform.stream.StreamResult(sw));
		} catch (Exception e) {
			throw new RuntimeException("Failed to marshal SOAP response", e);
		}
		return new StringSource(sw.toString());
	}

	private org.springframework.batch.core.JobParameters uniqueJobParameters() {
		return new JobParametersBuilder()
				.addLong("run.id", System.currentTimeMillis())
				.toJobParameters();
	}
}
