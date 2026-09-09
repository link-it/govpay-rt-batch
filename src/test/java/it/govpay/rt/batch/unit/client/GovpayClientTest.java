package it.govpay.rt.batch.unit.client;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.context.DefaultTransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;

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

    @AfterEach
    void tearDown() {
        TransportContextHolder.setTransportContext(null);
    }

    @Nested
    @DisplayName("sendReceipt")
    class SendReceipt {

        @Test
        @DisplayName("should return null when receipt is null")
        void shouldReturnNullWhenReceiptIsNull() {
            assertNull(client.sendReceipt(null, "u-operatore"));
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
            when(webServiceTemplate.marshalSendAndReceive(any(Object.class), any(WebServiceMessageCallback.class)))
                    .thenReturn(jaxbElement);

            PaSendRTV2Response result = client.sendReceipt(request, "u-operatore");

            assertSame(response, result);
            verify(webServiceTemplate).marshalSendAndReceive(any(Object.class), any(WebServiceMessageCallback.class));
        }

        @Test
        @DisplayName("should return response directly when WebServiceTemplate returns the response object")
        void shouldReturnResponseObjectDirectly() {
            PaSendRTV2Request request = buildRequest();
            PaSendRTV2Response response = new PaSendRTV2Response();
            when(webServiceTemplate.marshalSendAndReceive(any(Object.class), any(WebServiceMessageCallback.class)))
                    .thenReturn(response);

            PaSendRTV2Response result = client.sendReceipt(request, null);

            assertSame(response, result);
        }

        @Test
        @DisplayName("should add GOVPAY-ON-BEHALF-OF header on the wire when onBehalfOf is set")
        void shouldAddOnBehalfOfHeaderWhenPresent() throws Exception {
            PaSendRTV2Request request = buildRequest();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<WebServiceMessageCallback> callbackCaptor = ArgumentCaptor.forClass(WebServiceMessageCallback.class);
            when(webServiceTemplate.marshalSendAndReceive(any(Object.class), callbackCaptor.capture()))
                    .thenReturn(new PaSendRTV2Response());

            client.sendReceipt(request, "u-operatore");

            SoapMessage soapMessage = mock(SoapMessage.class);
            HeadersAwareSenderWebServiceConnection connection = mock(HeadersAwareSenderWebServiceConnection.class);
            TransportContextHolder.setTransportContext(new DefaultTransportContext(connection));
            callbackCaptor.getValue().doWithMessage(soapMessage);

            verify(soapMessage).setSoapAction("paSendRTV2");
            verify(connection).addRequestHeader("GOVPAY-ON-BEHALF-OF", "u-operatore");
        }

        @Test
        @DisplayName("should not add GOVPAY-ON-BEHALF-OF header when onBehalfOf is null")
        void shouldNotAddOnBehalfOfHeaderWhenAbsent() throws Exception {
            PaSendRTV2Request request = buildRequest();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<WebServiceMessageCallback> callbackCaptor = ArgumentCaptor.forClass(WebServiceMessageCallback.class);
            when(webServiceTemplate.marshalSendAndReceive(any(Object.class), callbackCaptor.capture()))
                    .thenReturn(new PaSendRTV2Response());

            client.sendReceipt(request, null);

            SoapMessage soapMessage = mock(SoapMessage.class);
            HeadersAwareSenderWebServiceConnection connection = mock(HeadersAwareSenderWebServiceConnection.class);
            TransportContextHolder.setTransportContext(new DefaultTransportContext(connection));
            callbackCaptor.getValue().doWithMessage(soapMessage);

            verify(soapMessage).setSoapAction("paSendRTV2");
            verifyNoMoreInteractions(connection);
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
