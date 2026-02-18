package it.govpay.rt.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import it.govpay.rt.batch.utils.OffsetDateTimeAdapter;

@DisplayName("OffsetDateTimeAdapter")
class OffsetDateTimeAdapterTest {

    private OffsetDateTimeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OffsetDateTimeAdapter();
    }

    @Nested
    @DisplayName("unmarshal")
    class UnmarshalTest {

        @Test
        @DisplayName("should parse ISO date time string")
        void shouldParseIsoDateTimeString() {
            String input = "2024-01-15T10:30:00+01:00";

            OffsetDateTime result = adapter.unmarshal(input);

            assertNotNull(result);
            assertEquals(2024, result.getYear());
            assertEquals(1, result.getMonthValue());
            assertEquals(15, result.getDayOfMonth());
            assertEquals(10, result.getHour());
            assertEquals(30, result.getMinute());
            assertEquals(0, result.getSecond());
        }

        @Test
        @DisplayName("should parse UTC date time")
        void shouldParseUtcDateTime() {
            String input = "2024-06-20T15:45:30Z";

            OffsetDateTime result = adapter.unmarshal(input);

            assertNotNull(result);
            assertEquals(ZoneOffset.UTC, result.getOffset());
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            OffsetDateTime result = adapter.unmarshal(null);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for blank input")
        void shouldReturnNullForBlankInput() {
            OffsetDateTime result = adapter.unmarshal("   ");

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmptyInput() {
            OffsetDateTime result = adapter.unmarshal("");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("marshal")
    class MarshalTest {

        @Test
        @DisplayName("should format to ISO date time string")
        void shouldFormatToIsoDateTimeString() {
            OffsetDateTime input = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHours(1));

            String result = adapter.marshal(input);

            assertNotNull(result);
            assertEquals("2024-01-15T10:30:00+01:00", result);
        }

        @Test
        @DisplayName("should format UTC date time with Z suffix")
        void shouldFormatUtcDateTimeWithZSuffix() {
            OffsetDateTime input = OffsetDateTime.of(2024, 6, 20, 15, 45, 30, 0, ZoneOffset.UTC);

            String result = adapter.marshal(input);

            assertNotNull(result);
            assertEquals("2024-06-20T15:45:30Z", result);
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
            OffsetDateTime original = OffsetDateTime.of(2024, 3, 10, 14, 25, 45, 0, ZoneOffset.ofHours(2));

            String marshalled = adapter.marshal(original);
            OffsetDateTime unmarshalled = adapter.unmarshal(marshalled);

            assertEquals(original, unmarshalled);
        }
    }
}
