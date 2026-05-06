package it.govpay.rt.batch.unit.client;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.core.SoapActionCallback;

import jakarta.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import it.gov.pagopa.pagopa_api.pa.pafornode.CtReceiptV2;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.govpay.rt.batch.client.GovpayClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("GovpayClient")
class GovpayClientTest {

    @Mock
    private WebServiceTemplate webServiceTemplate;

    private GovpayClient client;

    @BeforeEach
    void setUp() {
        client = new GovpayClient();
        client.setWebServiceTemplate(webServiceTemplate);
    }

    @Nested
    @DisplayName("sendReceipt")
    class SendReceipt {

        @Test
        @DisplayName("should return null when receipt is null")
        void shouldReturnNullWhenReceiptIsNull() {
            assertNull(client.sendReceipt(null));
            verifyNoInteractions(webServiceTemplate);
        }

        @Test
        @DisplayName("should return response when WebServiceTemplate returns JAXBElement")
        void shouldUnwrapJAXBElementResponse() {
            PaSendRTV2Request request = buildRequest();
            PaSendRTV2Response response = new PaSendRTV2Response();
            JAXBElement<PaSendRTV2Response> jaxbElement = new JAXBElement<>(
                    new QName("http://example.com", "paSendRTV2Response"),
                    PaSendRTV2Response.class, response);
            when(webServiceTemplate.marshalSendAndReceive(any(Object.class), any(SoapActionCallback.class)))
                    .thenReturn(jaxbElement);

            PaSendRTV2Response result = client.sendReceipt(request);

            assertSame(response, result);
            verify(webServiceTemplate).marshalSendAndReceive(any(Object.class), any(SoapActionCallback.class));
        }

        @Test
        @DisplayName("should return response directly when WebServiceTemplate returns the response object")
        void shouldReturnResponseObjectDirectly() {
            PaSendRTV2Request request = buildRequest();
            PaSendRTV2Response response = new PaSendRTV2Response();
            when(webServiceTemplate.marshalSendAndReceive(any(Object.class), any(SoapActionCallback.class)))
                    .thenReturn(response);

            PaSendRTV2Response result = client.sendReceipt(request);

            assertSame(response, result);
        }

        private PaSendRTV2Request buildRequest() {
            PaSendRTV2Request request = new PaSendRTV2Request();
            CtReceiptV2 receipt = new CtReceiptV2();
            receipt.setReceiptId("RECEIPT-123");
            request.setReceipt(receipt);
            return request;
        }
    }
}
