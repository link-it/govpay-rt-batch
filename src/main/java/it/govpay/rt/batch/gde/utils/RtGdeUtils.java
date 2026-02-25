package it.govpay.rt.batch.gde.utils;

import java.io.StringWriter;
import java.util.Base64;

import javax.xml.transform.stream.StreamResult;

import org.springframework.oxm.jaxb.Jaxb2Marshaller;

import it.gov.pagopa.pagopa_api.pa.pafornode.ObjectFactory;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.govpay.common.gde.GdeUtils;
import it.govpay.gde.client.beans.NuovoEvento;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for RT-specific GDE operations (SOAP payload serialization).
 * <p>
 * REST payload serialization is handled by {@link GdeUtils} from govpay-common.
 * This class retains only the SOAP-specific methods that are unique to govpay-rt-batch.
 */
@Slf4j
public class RtGdeUtils {

	private static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

	private RtGdeUtils () {}

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
	                                         PaSendRTV2Request request, PaSendRTV2Response response, Exception e) {
		// Serializza la richiesta in XML wrappando in JAXBElement (manca @XmlRootElement)
		if (nuovoEvento.getParametriRichiesta() != null && request != null) {
			nuovoEvento.getParametriRichiesta().setPayload(
				Base64.getEncoder().encodeToString(marshalToXml(marshaller, OBJECT_FACTORY.createPaSendRTV2Request(request)).getBytes()));
		}

		// Serializza la risposta in XML
		if (nuovoEvento.getParametriRisposta() != null) {
			if (e != null) {
				nuovoEvento.getParametriRisposta().setPayload(
					Base64.getEncoder().encodeToString(e.getMessage().getBytes()));
			} else if (response != null) {
				nuovoEvento.getParametriRisposta().setPayload(
					Base64.getEncoder().encodeToString(marshalToXml(marshaller, OBJECT_FACTORY.createPaSendRTV2Response(response)).getBytes()));
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
			return GdeUtils.MSG_PAYLOAD_NON_SERIALIZZABILE;
		}
	}
}
