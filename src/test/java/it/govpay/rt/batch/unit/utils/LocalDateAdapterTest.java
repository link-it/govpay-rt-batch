package it.govpay.rt.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import it.govpay.rt.batch.utils.LocalDateAdapter;

@DisplayName("LocalDateAdapter")
class LocalDateAdapterTest {

    private LocalDateAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalDateAdapter();
    }

    @Nested
    @DisplayName("unmarshal")
    class UnmarshalTest {

        @Test
        @DisplayName("should parse ISO date string")
        void shouldParseIsoDateString() {
            String input = "2024-01-15";

            LocalDate result = adapter.unmarshal(input);

            assertNotNull(result);
            assertEquals(2024, result.getYear());
            assertEquals(1, result.getMonthValue());
            assertEquals(15, result.getDayOfMonth());
        }

        @Test
        @DisplayName("should parse leap year date")
        void shouldParseLeapYearDate() {
            String input = "2024-02-29";

            LocalDate result = adapter.unmarshal(input);

            assertNotNull(result);
            assertEquals(29, result.getDayOfMonth());
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            LocalDate result = adapter.unmarshal(null);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for blank input")
        void shouldReturnNullForBlankInput() {
            LocalDate result = adapter.unmarshal("   ");

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmptyInput() {
            LocalDate result = adapter.unmarshal("");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("marshal")
    class MarshalTest {

        @Test
        @DisplayName("should format to ISO date string")
        void shouldFormatToIsoDateString() {
            LocalDate input = LocalDate.of(2024, 1, 15);

            String result = adapter.marshal(input);

            assertNotNull(result);
            assertEquals("2024-01-15", result);
        }

        @Test
        @DisplayName("should format single digit month with leading zero")
        void shouldFormatSingleDigitMonthWithLeadingZero() {
            LocalDate input = LocalDate.of(2024, 3, 5);

            String result = adapter.marshal(input);

            assertEquals("2024-03-05", result);
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            String result = adapter.marshal(null);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("roundtrip")
    class RoundtripTest {

        @Test
        @DisplayName("should marshal and unmarshal to same value")
        void shouldMarshalAndUnmarshalToSameValue() {
            LocalDate original = LocalDate.of(2024, 12, 31);

            String marshalled = adapter.marshal(original);
            LocalDate unmarshalled = adapter.unmarshal(marshalled);

            assertEquals(original, unmarshalled);
        }
    }
}
