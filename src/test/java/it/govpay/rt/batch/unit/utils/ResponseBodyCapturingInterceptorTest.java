package it.govpay.rt.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import it.govpay.rt.batch.utils.ResponseBodyCapturingInterceptor;
import it.govpay.rt.batch.utils.ResponseBodyHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResponseBodyCapturingInterceptor")
class ResponseBodyCapturingInterceptorTest {

    @Mock
    private HttpRequest httpRequest;

    @Mock
    private ClientHttpRequestExecution execution;

    @Mock
    private ClientHttpResponse response;

    private ResponseBodyCapturingInterceptor interceptor;

    private static final byte[] REQUEST_BODY = "request body".getBytes();
    private static final byte[] RESPONSE_BODY = "{\"status\":\"ok\"}".getBytes();

    @BeforeEach
    void setUp() {
        interceptor = new ResponseBodyCapturingInterceptor();
        ResponseBodyHolder.clear();
    }

    @AfterEach
    void tearDown() {
        ResponseBodyHolder.clear();
    }

    @Nested
    @DisplayName("intercept")
    class InterceptTest {

        @Test
        @DisplayName("should capture request headers")
        void shouldCaptureRequestHeaders() throws IOException {
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.add("Content-Type", "application/json");
            requestHeaders.add("Authorization", "Bearer token123");

            when(httpRequest.getHeaders()).thenReturn(requestHeaders);
            when(httpRequest.getURI()).thenReturn(URI.create("http://example.com/api"));
            when(execution.execute(httpRequest, REQUEST_BODY)).thenReturn(response);
            when(response.getBody()).thenReturn(new ByteArrayInputStream(RESPONSE_BODY));

            interceptor.intercept(httpRequest, REQUEST_BODY, execution);

            HttpHeaders capturedHeaders = ResponseBodyHolder.getRequestHeaders();
            assertNotNull(capturedHeaders);
            assertEquals("application/json", capturedHeaders.getFirst("Content-Type"));
            assertEquals("Bearer token123", capturedHeaders.getFirst("Authorization"));
        }

        @Test
        @DisplayName("should capture response body")
        void shouldCaptureResponseBody() throws IOException {
            when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());
            when(httpRequest.getURI()).thenReturn(URI.create("http://example.com/api"));
            when(execution.execute(httpRequest, REQUEST_BODY)).thenReturn(response);
            when(response.getBody()).thenReturn(new ByteArrayInputStream(RESPONSE_BODY));

            interceptor.intercept(httpRequest, REQUEST_BODY, execution);

            byte[] capturedBody = ResponseBodyHolder.getResponseBody();
            assertNotNull(capturedBody);
            assertArrayEquals(RESPONSE_BODY, capturedBody);
        }

        @Test
        @DisplayName("should return buffered response that can be read multiple times")
        void shouldReturnBufferedResponseThatCanBeReadMultipleTimes() throws IOException {
            when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());
            when(httpRequest.getURI()).thenReturn(URI.create("http://example.com/api"));
            when(execution.execute(httpRequest, REQUEST_BODY)).thenReturn(response);
            when(response.getBody()).thenReturn(new ByteArrayInputStream(RESPONSE_BODY));

            ClientHttpResponse bufferedResponse = interceptor.intercept(httpRequest, REQUEST_BODY, execution);

            // Read the body first time
            InputStream body1 = bufferedResponse.getBody();
            byte[] read1 = body1.readAllBytes();
            assertArrayEquals(RESPONSE_BODY, read1);

            // Read the body second time (should still work)
            InputStream body2 = bufferedResponse.getBody();
            byte[] read2 = body2.readAllBytes();
            assertArrayEquals(RESPONSE_BODY, read2);
        }

        @Test
        @DisplayName("should preserve original response headers")
        void shouldPreserveOriginalResponseHeaders() throws IOException {
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.add("X-Custom-Header", "custom-value");

            when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());
            when(httpRequest.getURI()).thenReturn(URI.create("http://example.com/api"));
            when(execution.execute(httpRequest, REQUEST_BODY)).thenReturn(response);
            when(response.getBody()).thenReturn(new ByteArrayInputStream(RESPONSE_BODY));
            when(response.getHeaders()).thenReturn(responseHeaders);

            ClientHttpResponse bufferedResponse = interceptor.intercept(httpRequest, REQUEST_BODY, execution);

            assertEquals("custom-value", bufferedResponse.getHeaders().getFirst("X-Custom-Header"));
        }

        @Test
        @DisplayName("should preserve original status code")
        void shouldPreserveOriginalStatusCode() throws IOException {
            when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());
            when(httpRequest.getURI()).thenReturn(URI.create("http://example.com/api"));
            when(execution.execute(httpRequest, REQUEST_BODY)).thenReturn(response);
            when(response.getBody()).thenReturn(new ByteArrayInputStream(RESPONSE_BODY));
            when(response.getStatusCode()).thenReturn(HttpStatus.CREATED);
            when(response.getStatusText()).thenReturn("Created");

            ClientHttpResponse bufferedResponse = interceptor.intercept(httpRequest, REQUEST_BODY, execution);

            assertEquals(HttpStatus.CREATED, bufferedResponse.getStatusCode());
            assertEquals("Created", bufferedResponse.getStatusText());
        }

        @Test
        @DisplayName("should clear previous holder data before new request")
        void shouldClearPreviousHolderDataBeforeNewRequest() throws IOException {
            // Set some previous data
            ResponseBodyHolder.setResponseBody("old data".getBytes());
            ResponseBodyHolder.setRequestHeaders(new HttpHeaders());

            when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());
            when(httpRequest.getURI()).thenReturn(URI.create("http://example.com/api"));
            when(execution.execute(httpRequest, REQUEST_BODY)).thenReturn(response);
            when(response.getBody()).thenReturn(new ByteArrayInputStream(RESPONSE_BODY));

            interceptor.intercept(httpRequest, REQUEST_BODY, execution);

            // Should have new data, not old
            assertArrayEquals(RESPONSE_BODY, ResponseBodyHolder.getResponseBody());
        }

        @Test
        @DisplayName("should return original response when body read fails")
        void shouldReturnOriginalResponseWhenBodyReadFails() throws IOException {
            when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());
            when(httpRequest.getURI()).thenReturn(URI.create("http://example.com/api"));
            when(execution.execute(httpRequest, REQUEST_BODY)).thenReturn(response);
            when(response.getBody()).thenThrow(new IOException("Stream closed"));

            ClientHttpResponse result = interceptor.intercept(httpRequest, REQUEST_BODY, execution);

            // Should return the original response
            assertSame(response, result);
        }

        @Test
        @DisplayName("should handle empty response body")
        void shouldHandleEmptyResponseBody() throws IOException {
            when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());
            when(httpRequest.getURI()).thenReturn(URI.create("http://example.com/api"));
            when(execution.execute(httpRequest, REQUEST_BODY)).thenReturn(response);
            when(response.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));
            when(response.getStatusCode()).thenReturn(HttpStatus.NO_CONTENT);

            ClientHttpResponse bufferedResponse = interceptor.intercept(httpRequest, REQUEST_BODY, execution);

            byte[] capturedBody = ResponseBodyHolder.getResponseBody();
            assertNotNull(capturedBody);
            assertEquals(0, capturedBody.length);

            assertEquals(HttpStatus.NO_CONTENT, bufferedResponse.getStatusCode());
        }
    }

    @Nested
    @DisplayName("BufferedClientHttpResponse close")
    class CloseTest {

        @Test
        @DisplayName("should delegate close to original response")
        void shouldDelegateCloseToOriginalResponse() throws IOException {
            when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());
            when(httpRequest.getURI()).thenReturn(URI.create("http://example.com/api"));
            when(execution.execute(httpRequest, REQUEST_BODY)).thenReturn(response);
            when(response.getBody()).thenReturn(new ByteArrayInputStream(RESPONSE_BODY));

            ClientHttpResponse bufferedResponse = interceptor.intercept(httpRequest, REQUEST_BODY, execution);
            bufferedResponse.close();

            verify(response).close();
        }
    }
}
