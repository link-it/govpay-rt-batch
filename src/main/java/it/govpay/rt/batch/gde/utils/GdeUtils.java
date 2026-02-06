package it.govpay.rt.batch.gde.utils;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.xml.transform.stream.StreamResult;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.utils.ResponseBodyHolder;
import it.govpay.gde.client.model.DettaglioRisposta;
import it.govpay.gde.client.model.Header;
import it.govpay.gde.client.model.NuovoEvento;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GdeUtils {

	private GdeUtils () {}

	/**
	 * Serializza il payload della risposta nel formato Base64 per il GDE.
	 * <p>
	 * Ordine di priorità per il payload:
	 * 1. HttpStatusCodeException.getResponseBodyAsByteArray() - per errori HTTP (4xx, 5xx)
	 * 2. ResponseBodyHolder.getResponseBody() - body catturato dall'interceptor (per errori di deserializzazione)
	 * 3. response.getBody() serializzato - per risposte OK
	 * 4. exception.getMessage() - fallback per altri errori
	 *
	 * @param objectMapper ObjectMapper per serializzazione
	 * @param nuovoEvento evento da arricchire con il payload
	 * @param response risposta HTTP (può essere null)
	 * @param e eccezione (può essere null)
	 */
	public static void serializzaPayload(ObjectMapper objectMapper, NuovoEvento nuovoEvento, ResponseEntity<?> response, RestClientException e) {
		DettaglioRisposta parametriRisposta = nuovoEvento.getParametriRisposta();

		if(parametriRisposta != null) {
			try {
				if(e != null) {
					if (e instanceof HttpStatusCodeException httpStatusCodeException) {
						// Caso 1: errore HTTP con body disponibile
						parametriRisposta.setPayload(Base64.getEncoder().encodeToString(httpStatusCodeException.getResponseBodyAsByteArray()));
					} else {
						// Caso 2: altro tipo di errore (es. deserializzazione fallita)
						// Prova a recuperare il body catturato dall'interceptor
						byte[] capturedBody = ResponseBodyHolder.getResponseBody();
						if (capturedBody != null && capturedBody.length > 0) {
							log.debug("Usando body catturato dall'interceptor: {} bytes", capturedBody.length);
							parametriRisposta.setPayload(Base64.getEncoder().encodeToString(capturedBody));
						} else {
							// Fallback: usa il messaggio dell'eccezione
							log.debug("Body non disponibile, uso messaggio eccezione");
							parametriRisposta.setPayload(Base64.getEncoder().encodeToString(e.getMessage().getBytes()));
						}
					}
				} else if(response != null) {
					// Caso 3: risposta OK
					parametriRisposta.setPayload(Base64.getEncoder().encodeToString(writeValueAsString(objectMapper, response.getBody()).getBytes()));
				}
			} finally {
				// Pulisci sempre il ThreadLocal per evitare memory leak
				ResponseBodyHolder.clear();
			}
		}
	}

	/**
	 * Recupera gli headers della richiesta catturati dall'interceptor e li converte in formato GDE.
	 *
	 * @return lista di Header per il GDE, o lista vuota se non disponibili
	 */
	public static List<Header> getCapturedRequestHeaders() {
		List<Header> headers = new ArrayList<>();
		HttpHeaders httpHeaders = ResponseBodyHolder.getRequestHeaders();

		if (httpHeaders != null) {
			httpHeaders.forEach((key, values) -> {
				if (values != null && !values.isEmpty()) {
					Header header = new Header();
					header.setNome(key);
					// Unisci i valori multipli con virgola
					header.setValore(String.join(", ", values));
					headers.add(header);
				}
			});
			log.trace("Recuperati {} headers della richiesta per GDE", headers.size());
		}

		return headers;
	}

	public static String writeValueAsString(ObjectMapper objectMapper, Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return Costanti.MSG_PAYLOAD_NON_SERIALIZZABILE;
		}
	}

	/**
	 * Serializza il payload della richiesta/risposta SOAP nel formato XML Base64 per il GDE.
	 *
	 * @param marshaller Jaxb2Marshaller per serializzazione XML
	 * @param nuovoEvento evento da arricchire con il payload
	 * @param request richiesta SOAP (può essere null)
	 * @param response risposta SOAP (può essere null)
	 * @param e eccezione (può essere null)
	 */
	public static void serializzaPayloadSoap(Jaxb2Marshaller marshaller, NuovoEvento nuovoEvento,
	                                         Object request, Object response, Exception e) {
		// Serializza la richiesta in XML
		if (nuovoEvento.getParametriRichiesta() != null && request != null) {
			nuovoEvento.getParametriRichiesta().setPayload(
				Base64.getEncoder().encodeToString(marshalToXml(marshaller, request).getBytes()));
		}

		// Serializza la risposta in XML
		DettaglioRisposta parametriRisposta = nuovoEvento.getParametriRisposta();
		if (parametriRisposta != null) {
			if (e != null) {
				parametriRisposta.setPayload(
					Base64.getEncoder().encodeToString(e.getMessage().getBytes()));
			} else if (response != null) {
				parametriRisposta.setPayload(
					Base64.getEncoder().encodeToString(marshalToXml(marshaller, response).getBytes()));
			}
		}
	}

	/**
	 * Converte un oggetto JAXB in stringa XML.
	 *
	 * @param marshaller Jaxb2Marshaller configurato
	 * @param object oggetto JAXB da serializzare
	 * @return stringa XML
	 */
	public static String marshalToXml(Jaxb2Marshaller marshaller, Object object) {
		try {
			StringWriter sw = new StringWriter();
			marshaller.marshal(object, new StreamResult(sw));
			return sw.toString();
		} catch (Exception e) {
			log.warn("Errore durante la serializzazione XML: {}", e.getMessage());
			return Costanti.MSG_PAYLOAD_NON_SERIALIZZABILE;
		}
	}
}
