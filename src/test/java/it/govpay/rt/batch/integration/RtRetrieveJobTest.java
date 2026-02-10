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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
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
import it.govpay.gde.client.api.EventiApi;
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

	@TestConfiguration
	static class TestConfig {
		@Bean(name = "gdeTaskExecutor")
		public TaskExecutor gdeTaskExecutor() {
			return new SyncTaskExecutor();
		}
	}

	private static final String TAX_CODE = "12345678901";
	private static final String IUV = "01234567890123456";
	private static final String IUR = "IUR123456789";

	@Autowired
	private Job rtRetrieveJob;

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	private RestTemplate rtApiRestTemplate;

	@Autowired
	private GovpayClient govpayClient;

	@MockitoBean
	private EventiApi eventiApi;

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
		mockRestServer = MockRestServiceServer.createServer(rtApiRestTemplate);
		mockWsServer = MockWebServiceServer.createServer(govpayClient);

		// Clean DB data between tests (respect FK order)
		transactionTemplate.executeWithoutResult(status -> {
			entityManager.createQuery("DELETE FROM Rendicontazione").executeUpdate();
			entityManager.createQuery("DELETE FROM Fr").executeUpdate();
			entityManager.createQuery("DELETE FROM SingoloVersamento").executeUpdate();
			entityManager.createQuery("DELETE FROM Dominio").executeUpdate();
		});

		reset(eventiApi);
	}

	@Test
	@DisplayName("Happy path: receipt retrieved and sent to GovPay successfully")
	void happyPath_receiptRetrievedAndSent() throws Exception {
		// Given: a rendicontazione with singoloVersamento but no idPagamento
		insertTestData(TAX_CODE, IUV, IUR, null);

		// The reader maps query result columns as:
		// rndInfo[2] (r.iuv) -> context.iur, rndInfo[3] (r.iur) -> context.iuv
		String expectedRestUrl = "http://localhost/organizations/" + TAX_CODE
				+ "/receipts/" + IUV + "/paymentoptions/" + IUR;

		// Mock REST: pagoPA returns a valid receipt
		CtReceiptModelResponse receiptResponse = createReceiptResponse();
		String jsonReceipt = objectMapper.writeValueAsString(receiptResponse);

		mockRestServer.expect(requestTo(expectedRestUrl))
				.andExpect(method(HttpMethod.GET))
				.andExpect(MockRestRequestMatchers.header("Ocp-Apim-Subscription-Key", "test-subscription-key"))
				.andRespond(withSuccess(jsonReceipt, MediaType.APPLICATION_JSON));

		// Mock SOAP: GovPay returns OK
		mockWsServer.expect(org.springframework.ws.test.client.RequestMatchers.anything())
				.andRespond(ResponseCreators.withPayload(marshalSoapResponse(StOutcome.OK)));

		// When: launch the job
		JobExecution jobExecution = jobLauncher.run(rtRetrieveJob, uniqueJobParameters());

		// Then: job completed successfully
		assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());

		// Verify REST call was made
		mockRestServer.verify();

		// Verify SOAP call was made
		mockWsServer.verify();

		// Verify GDE events were tracked (at least for REST OK + SOAP OK)
		verify(eventiApi, atLeastOnce()).addEvento(any());
	}

	@Test
	@DisplayName("Receipt not found (404): job fails with exception")
	void receiptNotFound_jobFails() throws Exception {
		// Given: a rendicontazione with singoloVersamento but no idPagamento
		insertTestData(TAX_CODE, IUV, IUR, null);

		String expectedRestUrl = "http://localhost/organizations/" + TAX_CODE
				+ "/receipts/" + IUV + "/paymentoptions/" + IUR;

		// Mock REST: pagoPA returns 404
		mockRestServer.expect(requestTo(expectedRestUrl))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withResourceNotFound());

		// When: launch the job
		JobExecution jobExecution = jobLauncher.run(rtRetrieveJob, uniqueJobParameters());

		// Then: job failed because 404 throws HttpClientErrorException
		assertEquals("FAILED", jobExecution.getExitStatus().getExitCode());

		// Verify REST call was made
		mockRestServer.verify();

		// Verify no SOAP call was made (receipt not retrieved)
		mockWsServer.verify();
	}

	@Test
	@DisplayName("SOAP KO outcome: job completes but receipt send failed")
	void soapKoOutcome_jobCompletes() throws Exception {
		// Given: a rendicontazione with singoloVersamento but no idPagamento
		insertTestData(TAX_CODE, IUV, IUR, null);

		String expectedRestUrl = "http://localhost/organizations/" + TAX_CODE
				+ "/receipts/" + IUV + "/paymentoptions/" + IUR;

		// Mock REST: pagoPA returns a valid receipt
		CtReceiptModelResponse receiptResponse = createReceiptResponse();
		String jsonReceipt = objectMapper.writeValueAsString(receiptResponse);

		mockRestServer.expect(requestTo(expectedRestUrl))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(jsonReceipt, MediaType.APPLICATION_JSON));

		// Mock SOAP: GovPay returns KO
		mockWsServer.expect(org.springframework.ws.test.client.RequestMatchers.anything())
				.andRespond(ResponseCreators.withPayload(marshalSoapResponse(StOutcome.KO)));

		// When: launch the job
		JobExecution jobExecution = jobLauncher.run(rtRetrieveJob, uniqueJobParameters());

		// Then: job completed (SOAP KO doesn't cause job failure)
		assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());

		// Verify both REST and SOAP calls were made
		mockRestServer.verify();
		mockWsServer.verify();

		// Verify GDE events were tracked
		verify(eventiApi, atLeastOnce()).addEvento(any());
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
