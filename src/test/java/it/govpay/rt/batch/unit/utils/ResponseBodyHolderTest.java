package it.govpay.rt.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import it.govpay.rt.batch.utils.ResponseBodyHolder;

@DisplayName("ResponseBodyHolder")
class ResponseBodyHolderTest {

    @AfterEach
    void tearDown() {
        ResponseBodyHolder.clear();
    }

    @Nested
    @DisplayName("responseBody")
    class ResponseBodyTest {

        @Test
        @DisplayName("should store and retrieve response body")
        void shouldStoreAndRetrieveResponseBody() {
            byte[] body = "test response body".getBytes();

            ResponseBodyHolder.setResponseBody(body);

            assertArrayEquals(body, ResponseBodyHolder.getResponseBody());
        }

        @Test
        @DisplayName("should return null when not set")
        void shouldReturnNullWhenNotSet() {
            assertNull(ResponseBodyHolder.getResponseBody());
        }

        @Test
        @DisplayName("should overwrite previous value")
        void shouldOverwritePreviousValue() {
            ResponseBodyHolder.setResponseBody("first".getBytes());
            ResponseBodyHolder.setResponseBody("second".getBytes());

            assertArrayEquals("second".getBytes(), ResponseBodyHolder.getResponseBody());
        }
    }

    @Nested
    @DisplayName("requestHeaders")
    class RequestHeadersTest {

        @Test
        @DisplayName("should store and retrieve request headers")
        void shouldStoreAndRetrieveRequestHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            headers.add("X-Custom-Header", "custom-value");

            ResponseBodyHolder.setRequestHeaders(headers);

            HttpHeaders retrieved = ResponseBodyHolder.getRequestHeaders();
            assertNotNull(retrieved);
            assertEquals("application/json", retrieved.getFirst("Content-Type"));
            assertEquals("custom-value", retrieved.getFirst("X-Custom-Header"));
        }

        @Test
        @DisplayName("should return null when not set")
        void shouldReturnNullWhenNotSet() {
            assertNull(ResponseBodyHolder.getRequestHeaders());
        }
    }

    @Nested
    @DisplayName("clear")
    class ClearTest {

        @Test
        @DisplayName("should clear response body")
        void shouldClearResponseBody() {
            ResponseBodyHolder.setResponseBody("test".getBytes());

            ResponseBodyHolder.clear();

            assertNull(ResponseBodyHolder.getResponseBody());
        }

        @Test
        @DisplayName("should clear request headers")
        void shouldClearRequestHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Test", "value");
            ResponseBodyHolder.setRequestHeaders(headers);

            ResponseBodyHolder.clear();

            assertNull(ResponseBodyHolder.getRequestHeaders());
        }

        @Test
        @DisplayName("should clear both response body and request headers")
        void shouldClearBoth() {
            ResponseBodyHolder.setResponseBody("test".getBytes());
            HttpHeaders headers = new HttpHeaders();
            headers.add("Test", "value");
            ResponseBodyHolder.setRequestHeaders(headers);

            ResponseBodyHolder.clear();

            assertNull(ResponseBodyHolder.getResponseBody());
            assertNull(ResponseBodyHolder.getRequestHeaders());
        }

        @Test
        @DisplayName("should not throw when clearing empty holder")
        void shouldNotThrowWhenClearingEmptyHolder() {
            assertDoesNotThrow(() -> ResponseBodyHolder.clear());
        }
    }

    @Nested
    @DisplayName("threadIsolation")
    class ThreadIsolationTest {

        @Test
        @DisplayName("should isolate data between threads")
        void shouldIsolateDataBetweenThreads() throws InterruptedException {
            ResponseBodyHolder.setResponseBody("main thread".getBytes());

            Thread otherThread = new Thread(() -> {
                // Other thread should not see main thread's data
                assertNull(ResponseBodyHolder.getResponseBody());

                // Other thread sets its own data
                ResponseBodyHolder.setResponseBody("other thread".getBytes());
                assertArrayEquals("other thread".getBytes(), ResponseBodyHolder.getResponseBody());

                ResponseBodyHolder.clear();
            });

            otherThread.start();
            otherThread.join();

            // Main thread's data should be unchanged
            assertArrayEquals("main thread".getBytes(), ResponseBodyHolder.getResponseBody());
        }
    }
}
