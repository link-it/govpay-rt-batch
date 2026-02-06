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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gde.client.model.DettaglioRichiesta;
import it.govpay.gde.client.model.DettaglioRisposta;
import it.govpay.gde.client.model.NuovoEvento;
import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.gde.utils.GdeUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("GdeUtils")
class GdeUtilsTest {

    @Mock
    private ObjectMapper objectMapper;

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
    @DisplayName("serializzaPayload")
    class SerializzaPayloadTest {

        @Test
        @DisplayName("should serialize response body when response is OK")
        void shouldSerializeResponseBodyWhenOk() throws JsonProcessingException {
            String responseBody = "{\"status\":\"OK\"}";
            ResponseEntity<String> response = ResponseEntity.ok(responseBody);
            when(objectMapper.writeValueAsString(responseBody)).thenReturn(responseBody);

            GdeUtils.serializzaPayload(objectMapper, nuovoEvento, response, null);

            String expectedPayload = Base64.getEncoder().encodeToString(responseBody.getBytes());
            assertEquals(expectedPayload, nuovoEvento.getParametriRisposta().getPayload());
        }

        @Test
        @DisplayName("should serialize error body from HttpStatusCodeException")
        void shouldSerializeErrorBodyFromHttpStatusCodeException() {
            byte[] errorBody = "Error message".getBytes();
            HttpClientErrorException exception = HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, errorBody, null);

            GdeUtils.serializzaPayload(objectMapper, nuovoEvento, null, exception);

            String expectedPayload = Base64.getEncoder().encodeToString(errorBody);
            assertEquals(expectedPayload, nuovoEvento.getParametriRisposta().getPayload());
        }

        @Test
        @DisplayName("should serialize exception message for non-HTTP exceptions")
        void shouldSerializeExceptionMessageForNonHttpExceptions() {
            RestClientException exception = new RestClientException("Connection refused");

            GdeUtils.serializzaPayload(objectMapper, nuovoEvento, null, exception);

            String expectedPayload = Base64.getEncoder().encodeToString("Connection refused".getBytes());
            assertEquals(expectedPayload, nuovoEvento.getParametriRisposta().getPayload());
        }

        @Test
        @DisplayName("should handle null parametriRisposta gracefully")
        void shouldHandleNullParametriRispostaGracefully() {
            nuovoEvento.setParametriRisposta(null);
            ResponseEntity<String> response = ResponseEntity.ok("body");

            assertDoesNotThrow(() -> GdeUtils.serializzaPayload(objectMapper, nuovoEvento, response, null));
        }
    }

    @Nested
    @DisplayName("serializzaPayloadSoap")
    class SerializzaPayloadSoapTest {

        @Test
        @DisplayName("should serialize SOAP request as XML")
        void shouldSerializeSoapRequestAsXml() {
            Object request = new Object();
            doAnswer(invocation -> {
                // Simulate marshaller writing to result
                return null;
            }).when(marshaller).marshal(eq(request), any(Result.class));

            GdeUtils.serializzaPayloadSoap(marshaller, nuovoEvento, request, null, null);

            assertNotNull(nuovoEvento.getParametriRichiesta().getPayload());
        }

        @Test
        @DisplayName("should serialize SOAP response as XML")
        void shouldSerializeSoapResponseAsXml() {
            Object response = new Object();
            doAnswer(invocation -> null).when(marshaller).marshal(eq(response), any(Result.class));

            GdeUtils.serializzaPayloadSoap(marshaller, nuovoEvento, null, response, null);

            assertNotNull(nuovoEvento.getParametriRisposta().getPayload());
        }

        @Test
        @DisplayName("should serialize exception message when error occurs")
        void shouldSerializeExceptionMessageWhenErrorOccurs() {
            Exception exception = new RuntimeException("SOAP Fault");

            GdeUtils.serializzaPayloadSoap(marshaller, nuovoEvento, null, null, exception);

            String expectedPayload = Base64.getEncoder().encodeToString("SOAP Fault".getBytes());
            assertEquals(expectedPayload, nuovoEvento.getParametriRisposta().getPayload());
        }

        @Test
        @DisplayName("should handle marshaller exception gracefully")
        void shouldHandleMarshallerExceptionGracefully() {
            Object request = new Object();
            doThrow(new RuntimeException("Marshal error")).when(marshaller).marshal(eq(request), any(Result.class));

            GdeUtils.serializzaPayloadSoap(marshaller, nuovoEvento, request, null, null);

            String expectedPayload = Base64.getEncoder().encodeToString(Costanti.MSG_PAYLOAD_NON_SERIALIZZABILE.getBytes());
            assertEquals(expectedPayload, nuovoEvento.getParametriRichiesta().getPayload());
        }
    }

    @Nested
    @DisplayName("writeValueAsString")
    class WriteValueAsStringTest {

        @Test
        @DisplayName("should return serialized string on success")
        void shouldReturnSerializedStringOnSuccess() throws JsonProcessingException {
            Object obj = new Object();
            when(objectMapper.writeValueAsString(obj)).thenReturn("{\"test\":true}");

            String result = GdeUtils.writeValueAsString(objectMapper, obj);

            assertEquals("{\"test\":true}", result);
        }

        @Test
        @DisplayName("should return error message on serialization failure")
        void shouldReturnErrorMessageOnSerializationFailure() throws JsonProcessingException {
            Object obj = new Object();
            when(objectMapper.writeValueAsString(obj)).thenThrow(new JsonProcessingException("Error") {});

            String result = GdeUtils.writeValueAsString(objectMapper, obj);

            assertEquals(Costanti.MSG_PAYLOAD_NON_SERIALIZZABILE, result);
        }
    }

    @Nested
    @DisplayName("getCapturedRequestHeaders")
    class GetCapturedRequestHeadersTest {

        @Test
        @DisplayName("should return empty list when no headers captured")
        void shouldReturnEmptyListWhenNoHeadersCaptured() {
            var headers = GdeUtils.getCapturedRequestHeaders();

            assertNotNull(headers);
            assertTrue(headers.isEmpty());
        }
    }
}
