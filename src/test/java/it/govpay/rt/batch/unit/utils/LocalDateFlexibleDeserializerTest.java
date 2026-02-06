package it.govpay.rt.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;

import it.govpay.rt.batch.utils.LocalDateFlexibleDeserializer;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalDateFlexibleDeserializer")
class LocalDateFlexibleDeserializerTest {

    @Mock
    private JsonParser jsonParser;

    @Mock
    private DeserializationContext deserializationContext;

    private LocalDateFlexibleDeserializer deserializer;

    @BeforeEach
    void setUp() {
        deserializer = new LocalDateFlexibleDeserializer();
    }

    @Nested
    @DisplayName("deserialize")
    class DeserializeTest {

        @Test
        @DisplayName("should deserialize standard date format")
        void shouldDeserializeStandardDateFormat() throws IOException {
            when(jsonParser.getCurrentToken()).thenReturn(JsonToken.VALUE_STRING);
            when(jsonParser.getText()).thenReturn("2024-03-15");

            LocalDate result = deserializer.deserialize(jsonParser, deserializationContext);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 3, 15), result);
        }

        @Test
        @DisplayName("should return null for non-string token")
        void shouldReturnNullForNonStringToken() throws IOException {
            when(jsonParser.getCurrentToken()).thenReturn(JsonToken.VALUE_NUMBER_INT);

            LocalDate result = deserializer.deserialize(jsonParser, deserializationContext);

            assertNull(result);
        }

        @Test
        @DisplayName("should throw IOException on parse failure")
        void shouldThrowIOExceptionOnParseFailure() throws IOException {
            when(jsonParser.getCurrentToken()).thenReturn(JsonToken.VALUE_STRING);
            when(jsonParser.getText()).thenReturn("invalid-date");

            assertThrows(IOException.class, () -> deserializer.deserialize(jsonParser, deserializationContext));
        }
    }

    @Nested
    @DisplayName("parseLocalDate standard format")
    class ParseStandardFormatTest {

        @Test
        @DisplayName("should parse ISO local date")
        void shouldParseIsoLocalDate() {
            LocalDate result = deserializer.parseLocalDate("2024-03-15");

            assertEquals(LocalDate.of(2024, 3, 15), result);
        }

        @Test
        @DisplayName("should parse first day of year")
        void shouldParseFirstDayOfYear() {
            LocalDate result = deserializer.parseLocalDate("2024-01-01");

            assertEquals(LocalDate.of(2024, 1, 1), result);
        }

        @Test
        @DisplayName("should parse last day of year")
        void shouldParseLastDayOfYear() {
            LocalDate result = deserializer.parseLocalDate("2024-12-31");

            assertEquals(LocalDate.of(2024, 12, 31), result);
        }
    }

    @Nested
    @DisplayName("parseLocalDate from OffsetDateTime")
    class ParseFromOffsetDateTimeTest {

        @Test
        @DisplayName("should extract date from OffsetDateTime with positive offset")
        void shouldExtractDateFromOffsetDateTimeWithPositiveOffset() {
            LocalDate result = deserializer.parseLocalDate("2025-03-12T00:00:00.000000+02:00");

            assertEquals(LocalDate.of(2025, 3, 12), result);
        }

        @Test
        @DisplayName("should extract date from OffsetDateTime with Z offset")
        void shouldExtractDateFromOffsetDateTimeWithZOffset() {
            LocalDate result = deserializer.parseLocalDate("2024-06-15T14:30:00Z");

            assertEquals(LocalDate.of(2024, 6, 15), result);
        }

        @Test
        @DisplayName("should extract date from OffsetDateTime with negative offset")
        void shouldExtractDateFromOffsetDateTimeWithNegativeOffset() {
            LocalDate result = deserializer.parseLocalDate("2024-07-04T20:00:00-05:00");

            assertEquals(LocalDate.of(2024, 7, 4), result);
        }

        @Test
        @DisplayName("should extract date from OffsetDateTime with milliseconds")
        void shouldExtractDateFromOffsetDateTimeWithMilliseconds() {
            LocalDate result = deserializer.parseLocalDate("2024-08-20T10:30:45.123+01:00");

            assertEquals(LocalDate.of(2024, 8, 20), result);
        }

        @Test
        @DisplayName("should extract date from OffsetDateTime with microseconds")
        void shouldExtractDateFromOffsetDateTimeWithMicroseconds() {
            LocalDate result = deserializer.parseLocalDate("2024-09-10T12:00:00.123456+01:00");

            assertEquals(LocalDate.of(2024, 9, 10), result);
        }
    }

    @Nested
    @DisplayName("parseLocalDate from LocalDateTime")
    class ParseFromLocalDateTimeTest {

        @Test
        @DisplayName("should extract date from LocalDateTime without timezone")
        void shouldExtractDateFromLocalDateTimeWithoutTimezone() {
            LocalDate result = deserializer.parseLocalDate("2024-05-20T10:30:45");

            assertEquals(LocalDate.of(2024, 5, 20), result);
        }

        @Test
        @DisplayName("should extract date from LocalDateTime with milliseconds")
        void shouldExtractDateFromLocalDateTimeWithMilliseconds() {
            LocalDate result = deserializer.parseLocalDate("2024-11-15T08:45:30.500");

            assertEquals(LocalDate.of(2024, 11, 15), result);
        }
    }

    @Nested
    @DisplayName("parseLocalDate edge cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            LocalDate result = deserializer.parseLocalDate(null);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for empty string")
        void shouldReturnNullForEmptyString() {
            LocalDate result = deserializer.parseLocalDate("");

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for whitespace string")
        void shouldReturnNullForWhitespaceString() {
            LocalDate result = deserializer.parseLocalDate("   ");

            assertNull(result);
        }

        @Test
        @DisplayName("should trim whitespace before parsing")
        void shouldTrimWhitespaceBeforeParsing() {
            LocalDate result = deserializer.parseLocalDate("  2024-03-15  ");

            assertEquals(LocalDate.of(2024, 3, 15), result);
        }

        @Test
        @DisplayName("should throw DateTimeParseException for invalid format")
        void shouldThrowForInvalidFormat() {
            assertThrows(DateTimeParseException.class,
                    () -> deserializer.parseLocalDate("not-a-date"));
        }

        @Test
        @DisplayName("should handle leap year date")
        void shouldHandleLeapYearDate() {
            LocalDate result = deserializer.parseLocalDate("2024-02-29");

            assertEquals(LocalDate.of(2024, 2, 29), result);
        }
    }
}
