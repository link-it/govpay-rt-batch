package it.govpay.rt.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import it.govpay.rt.batch.utils.CurrencyTypeAdapter;

@DisplayName("CurrencyTypeAdapter")
class CurrencyTypeAdapterTest {

    private CurrencyTypeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CurrencyTypeAdapter();
    }

    @Nested
    @DisplayName("unmarshal")
    class UnmarshalTest {

        @Test
        @DisplayName("should convert string to cents")
        void shouldConvertStringToCents() {
            Long result = adapter.unmarshal("100.50");

            assertEquals(10050L, result);
        }

        @Test
        @DisplayName("should handle zero value")
        void shouldHandleZeroValue() {
            Long result = adapter.unmarshal("0.00");

            assertEquals(0L, result);
        }

        @Test
        @DisplayName("should handle whole numbers")
        void shouldHandleWholeNumbers() {
            Long result = adapter.unmarshal("100.00");

            assertEquals(10000L, result);
        }

        @Test
        @DisplayName("should handle large amounts")
        void shouldHandleLargeAmounts() {
            Long result = adapter.unmarshal("999999.99");

            assertEquals(99999999L, result);
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            Long result = adapter.unmarshal(null);

            assertNull(result);
        }

        @Test
        @DisplayName("should handle small decimals")
        void shouldHandleSmallDecimals() {
            Long result = adapter.unmarshal("0.01");

            assertEquals(1L, result);
        }
    }

    @Nested
    @DisplayName("marshal")
    class MarshalTest {

        @Test
        @DisplayName("should convert cents to string")
        void shouldConvertCentsToString() {
            String result = adapter.marshal(10050L);

            assertEquals("100.50", result);
        }

        @Test
        @DisplayName("should handle zero value")
        void shouldHandleZeroValue() {
            String result = adapter.marshal(0L);

            assertEquals(".00", result);
        }

        @Test
        @DisplayName("should handle whole euros")
        void shouldHandleWholeEuros() {
            String result = adapter.marshal(10000L);

            assertEquals("100.00", result);
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            String result = adapter.marshal(null);

            assertNull(result);
        }

        @Test
        @DisplayName("should handle single cent")
        void shouldHandleSingleCent() {
            String result = adapter.marshal(1L);

            assertEquals(".01", result);
        }

        @Test
        @DisplayName("should handle large amounts")
        void shouldHandleLargeAmounts() {
            String result = adapter.marshal(99999999L);

            assertEquals("999999.99", result);
        }
    }

    @Nested
    @DisplayName("convertBigDecimalCents")
    class ConvertBigDecimalCentsTest {

        @Test
        @DisplayName("should convert BigDecimal to cents")
        void shouldConvertBigDecimalToCents() {
            Long result = CurrencyTypeAdapter.convertBigDecimalCents(new BigDecimal("100.50"));

            assertEquals(10050L, result);
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            Long result = CurrencyTypeAdapter.convertBigDecimalCents(null);

            assertNull(result);
        }

        @Test
        @DisplayName("should handle precise decimals")
        void shouldHandlePreciseDecimals() {
            Long result = CurrencyTypeAdapter.convertBigDecimalCents(new BigDecimal("123.45"));

            assertEquals(12345L, result);
        }
    }

    @Nested
    @DisplayName("roundtrip")
    class RoundtripTest {

        @Test
        @DisplayName("unmarshal then marshal should preserve value")
        void unmarshalThenMarshalShouldPreserveValue() {
            String original = "100.50";

            Long cents = adapter.unmarshal(original);
            String result = adapter.marshal(cents);

            assertEquals(original, result);
        }
    }
}
