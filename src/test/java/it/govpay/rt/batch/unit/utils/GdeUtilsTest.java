package it.govpay.rt.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Base64;

import javax.xml.transform.Result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

import it.govpay.common.gde.GdeUtils;
import it.govpay.gde.client.beans.DettaglioRichiesta;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.rt.batch.gde.utils.RtGdeUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtGdeUtils")
class GdeUtilsTest {

    @Mock
    private Jaxb2Marshaller marshaller;

    private NuovoEvento nuovoEvento;

    @BeforeEach
    void setUp() {
        nuovoEvento = new NuovoEvento();
        nuovoEvento.setParametriRichiesta(new DettaglioRichiesta());
        nuovoEvento.setParametriRisposta(new DettaglioRisposta());
    }

    @Nested
    @DisplayName("serializzaPayloadSoap")
    class SerializzaPayloadSoapTest {

        @Test
        @DisplayName("should serialize SOAP request as XML")
        void shouldSerializeSoapRequestAsXml() {
            Object request = new Object();
            doAnswer(invocation -> null).when(marshaller).marshal(eq(request), any(Result.class));

            RtGdeUtils.serializzaPayloadSoap(marshaller, nuovoEvento, request, null, null);

            assertNotNull(nuovoEvento.getParametriRichiesta().getPayload());
        }

        @Test
        @DisplayName("should serialize SOAP response as XML")
        void shouldSerializeSoapResponseAsXml() {
            Object response = new Object();
            doAnswer(invocation -> null).when(marshaller).marshal(eq(response), any(Result.class));

            RtGdeUtils.serializzaPayloadSoap(marshaller, nuovoEvento, null, response, null);

            assertNotNull(nuovoEvento.getParametriRisposta().getPayload());
        }

        @Test
        @DisplayName("should serialize exception message when error occurs")
        void shouldSerializeExceptionMessageWhenErrorOccurs() {
            Exception exception = new RuntimeException("SOAP Fault");

            RtGdeUtils.serializzaPayloadSoap(marshaller, nuovoEvento, null, null, exception);

            String expectedPayload = Base64.getEncoder().encodeToString("SOAP Fault".getBytes());
            assertEquals(expectedPayload, nuovoEvento.getParametriRisposta().getPayload());
        }

        @Test
        @DisplayName("should handle marshaller exception gracefully")
        void shouldHandleMarshallerExceptionGracefully() {
            Object request = new Object();
            doThrow(new RuntimeException("Marshal error")).when(marshaller).marshal(eq(request), any(Result.class));

            RtGdeUtils.serializzaPayloadSoap(marshaller, nuovoEvento, request, null, null);

            String expectedPayload = Base64.getEncoder().encodeToString(GdeUtils.MSG_PAYLOAD_NON_SERIALIZZABILE.getBytes());
            assertEquals(expectedPayload, nuovoEvento.getParametriRichiesta().getPayload());
        }
    }
}
