package it.govpay.rt.batch.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
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
import it.govpay.rt.batch.service.mapper.CtReceiptV2Converter;
import it.govpay.rt.client.ApiClient;
import it.govpay.rt.client.api.PaymentReceiptsRestApisApi;
import it.govpay.rt.client.model.CtReceiptModelResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for interacting with pagoPA RT API.
 * Resolves the RT connector per-domain via IntermediarioRepository,
 * following the chain: DominioEntity -> StazioneEntity -> IntermediarioEntity.codConnettoreRecuperoRt
 */
@Service
@Slf4j
public class RtApiService {

	private final GdeService gdeService;
	private final ConnettoreService connettoreService;
	private final IntermediarioRepository intermediarioRepository;
	private final DominioRepository dominioRepository;
	private final RtApiClientConfig rtApiClientConfig;

	/** Cache of PaymentReceiptsRestApisApi instances keyed by connector code */
	private final ConcurrentHashMap<String, PaymentReceiptsRestApisApi> apiCache = new ConcurrentHashMap<>();

	public RtApiService(ConnettoreService connettoreService,
						IntermediarioRepository intermediarioRepository,
						DominioRepository dominioRepository,
						RtApiClientConfig rtApiClientConfig,
						GdeService gdeService) {
		this.connettoreService = connettoreService;
		this.intermediarioRepository = intermediarioRepository;
		this.dominioRepository = dominioRepository;
		this.rtApiClientConfig = rtApiClientConfig;
		this.gdeService = gdeService;
	}

	/**
	 * Resolves the connector code for the given domain via IntermediarioRepository.
	 */
	private String resolveConnectorCode(String codDominio) {
		Optional<IntermediarioEntity> intermediarioOpt = intermediarioRepository.findByCodDominio(codDominio);
		IntermediarioEntity intermediario = intermediarioOpt.orElseThrow(() ->
			new IllegalStateException("Nessun intermediario trovato per il dominio: " + codDominio));

		String codConnettore = intermediario.getCodConnettoreRecuperoRt();
		if (codConnettore == null || codConnettore.isBlank()) {
			throw new IllegalStateException(
				"Connettore Recupero RT non configurato per l'intermediario " + intermediario.getCodIntermediario()
				+ " (dominio: " + codDominio + ")");
		}

		log.debug("Dominio {} -> Intermediario {} -> Connettore RT: {}",
			codDominio, intermediario.getCodIntermediario(), codConnettore);
		return codConnettore;
	}

	/**
	 * Gets or creates a PaymentReceiptsRestApisApi instance for the given domain.
	 * Uses a cache keyed by connector code to avoid creating duplicate instances
	 * for domains sharing the same intermediary.
	 */
	private PaymentReceiptsRestApisApi getOrCreateApi(String codDominio) {
		String codConnettore = resolveConnectorCode(codDominio);
		return apiCache.computeIfAbsent(codConnettore, code -> {
			RestTemplate restTemplate = connettoreService.getRestTemplate(code);

			// Customize ObjectMapper for pagoPA date handling
			MappingJackson2HttpMessageConverter converter =
				new MappingJackson2HttpMessageConverter(rtApiClientConfig.createPagoPAObjectMapper());
			restTemplate.getMessageConverters().removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
			restTemplate.getMessageConverters().add(0, converter);

			Connettore connettore = connettoreService.getConnettore(code);
			ApiClient apiClient = new ApiClient(restTemplate);
			apiClient.setBasePath(connettore.getUrl());

			log.info("Creata istanza PaymentReceiptsRestApisApi per connettore {} (URL: {})", code, connettore.getUrl());
			return new PaymentReceiptsRestApisApi(apiClient);
		});
	}

	/**
	 * Returns the pagoPA base URL for the given domain (for GDE event tracking).
	 * Delegates to ConnettoreService which has its own internal caching.
	 */
	private String getBaseUrl(String codDominio) {
		String codConnettore = resolveConnectorCode(codDominio);
		return connettoreService.getConnettore(codConnettore).getUrl();
	}

	/**
	 * Resolves intermediaryId and stationId from DB for a given domain.
	 * Follows the chain: DominioEntity -> StazioneEntity -> IntermediarioEntity
	 */
	private DomainInfo resolveDomainInfo(String codDominio) {
		DominioEntity dominio = dominioRepository.findByCodDominio(codDominio)
			.orElseThrow(() -> new IllegalStateException("Nessun dominio trovato per il codDominio: " + codDominio));

		StazioneEntity stazione = dominio.getStazione();
		if (stazione == null) {
			throw new IllegalStateException("Nessuna stazione associata al dominio: " + codDominio);
		}

		IntermediarioEntity intermediario = stazione.getIntermediario();
		if (intermediario == null) {
			throw new IllegalStateException("Nessun intermediario associato alla stazione: " + stazione.getCodStazione());
		}

		return new DomainInfo(intermediario.getCodIntermediario(), stazione.getCodStazione());
	}

	public PaSendRTV2Request retrieveReceipt(RtRetrieveContext rtInfo, CompletableFuture<HttpStatusCode> statusCodeFuture) throws RestClientException {
		log.debug("Recupero ricevuta per l'organizzazione {} con iur {} e iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
		OffsetDateTime dataStart = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime dataEnd = null;
		String pagoPABaseUrl = getBaseUrl(rtInfo.getTaxCode());

		ResponseEntity<CtReceiptModelResponse> response = null;
		try {
			response = getOrCreateApi(rtInfo.getTaxCode()).getOrganizationReceiptIuvIurWithHttpInfo(rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv(), null);
			statusCodeFuture.complete(response.getStatusCode());
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
		} catch (RestClientException e) {
			log.error("Failed http call to retrieve missing receipt", e);
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
			gdeService.saveGetReceiptKo(rtInfo, response, e, dataStart, dataEnd, pagoPABaseUrl);
			throw e;
		}

		PaSendRTV2Request ret = null;
		if (response.getStatusCode().equals(HttpStatus.OK)) {
			log.debug("Recuperata ricevuta per l'organizzazione {} con iur {} e iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
			DomainInfo domainInfo = resolveDomainInfo(rtInfo.getTaxCode());
			ret = CtReceiptV2Converter.toPaSendRTV2Request(domainInfo.intermediaryId(), domainInfo.stationId(), rtInfo.getTaxCode(), response.getBody());
			gdeService.saveGetReceiptOk(rtInfo, response, dataStart, dataEnd, pagoPABaseUrl);
		} else
		if (response.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
			log.error("Receipt not found: taxCode: {} - iur: {} - iuv: {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
			gdeService.saveGetReceiptKo(rtInfo, response, new RestClientException("Receipt not found"), dataStart, dataEnd, pagoPABaseUrl);
		} else
		if (response.getStatusCode().equals(HttpStatus.TOO_MANY_REQUESTS)) {
			log.warn("Rate limit reached");
			throw new RestClientException("Rate limit reached");
		} else
		if (response.getStatusCode().is5xxServerError()) {
			log.error("Server error to retrieve missing receipt with status code {}", response.getStatusCode());
			throw new RestClientException("Server error to retrieve missing receipt");
		} else {
			log.error("Fail to retrieve missing receipt with status code {}", response.getStatusCode());
			throw new RestClientException("Fail to retrieve missing receipt");
		}
		return ret;
	}

	/**
	 * Svuota la cache delle istanze API per forzare la ricreazione al prossimo utilizzo.
	 */
	public void clearCache() {
		apiCache.clear();
		connettoreService.clearCache();
		log.info("Cache connettori RT svuotata");
	}

	/**
	 * Helper record to hold intermediary/station info resolved from DB.
	 */
	private record DomainInfo(String intermediaryId, String stationId) {}
}
