package it.govpay.rt.batch.unit.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;

import it.govpay.rt.batch.client.AuthorizationHeaderInserter;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationHeaderInserter")
class AuthorizationHeaderInserterTest {

    @Mock
    private MessageContext messageContext;

    @Mock
    private TransportContext transportContext;

    @Mock
    private HeadersAwareSenderWebServiceConnection httpConnection;

    private AuthorizationHeaderInserter interceptor;

    private static final String HEADER_NAME = "X-Auth-Header";
    private static final String SUBSCRIPTION_KEY = "test-key-12345";

    @BeforeEach
    void setUp() {
        interceptor = new AuthorizationHeaderInserter(HEADER_NAME, SUBSCRIPTION_KEY);
    }

    @Nested
    @DisplayName("handleRequest")
    class HandleRequestTest {

        @Test
        @DisplayName("should always return true")
        void shouldAlwaysReturnTrue() {
            boolean result = interceptor.handleRequest(messageContext);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("handleResponse")
    class HandleResponseTest {

        @Test
        @DisplayName("should add header when connection is HeadersAware")
        void shouldAddHeaderWhenConnectionIsHeadersAware() throws IOException {
            try (MockedStatic<TransportContextHolder> mockedHolder = mockStatic(TransportContextHolder.class)) {
                mockedHolder.when(TransportContextHolder::getTransportContext).thenReturn(transportContext);
                when(transportContext.getConnection()).thenReturn(httpConnection);

                boolean result = interceptor.handleResponse(messageContext);

                assertTrue(result);
                verify(httpConnection).addRequestHeader(HEADER_NAME, SUBSCRIPTION_KEY);
            }
        }

        @Test
        @DisplayName("should not add header when connection is not HeadersAware")
        void shouldNotAddHeaderWhenConnectionIsNotHeadersAware() {
            WebServiceConnection plainConnection = mock(WebServiceConnection.class);

            try (MockedStatic<TransportContextHolder> mockedHolder = mockStatic(TransportContextHolder.class)) {
                mockedHolder.when(TransportContextHolder::getTransportContext).thenReturn(transportContext);
                when(transportContext.getConnection()).thenReturn(plainConnection);

                boolean result = interceptor.handleResponse(messageContext);

                assertTrue(result);
                // No header added because connection is not HeadersAware
            }
        }

        @Test
        @DisplayName("should throw WebServiceIOException when IOException occurs")
        void shouldThrowWebServiceIOExceptionWhenIOExceptionOccurs() throws IOException {
            try (MockedStatic<TransportContextHolder> mockedHolder = mockStatic(TransportContextHolder.class)) {
                mockedHolder.when(TransportContextHolder::getTransportContext).thenReturn(transportContext);
                when(transportContext.getConnection()).thenReturn(httpConnection);
                doThrow(new IOException("Connection error")).when(httpConnection).addRequestHeader(anyString(), anyString());

                assertThrows(WebServiceIOException.class, () -> interceptor.handleResponse(messageContext));
            }
        }
    }

    @Nested
    @DisplayName("handleFault")
    class HandleFaultTest {

        @Test
        @DisplayName("should always return true")
        void shouldAlwaysReturnTrue() {
            boolean result = interceptor.handleFault(messageContext);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("afterCompletion")
    class AfterCompletionTest {

        @Test
        @DisplayName("should not throw")
        void shouldNotThrow() {
            assertDoesNotThrow(() -> interceptor.afterCompletion(messageContext, null));
        }

        @Test
        @DisplayName("should not throw when exception is passed")
        void shouldNotThrowWhenExceptionIsPassed() {
            Exception ex = new RuntimeException("Test error");
            assertDoesNotThrow(() -> interceptor.afterCompletion(messageContext, ex));
        }
    }
}
