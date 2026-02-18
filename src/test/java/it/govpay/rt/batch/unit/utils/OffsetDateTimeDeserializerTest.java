package it.govpay.rt.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

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

import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.utils.OffsetDateTimeDeserializer;

@ExtendWith(MockitoExtension.class)
@DisplayName("OffsetDateTimeDeserializer")
class OffsetDateTimeDeserializerTest {

    @Mock
    private JsonParser jsonParser;

    @Mock
    private DeserializationContext deserializationContext;

    private OffsetDateTimeDeserializer deserializer;
    private DateTimeFormatter formatter;

    @BeforeEach
    void setUp() {
        deserializer = new OffsetDateTimeDeserializer();
        formatter = DateTimeFormatter.ofPattern(
                Costanti.PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI_XXX, Locale.getDefault());
    }

    @Nested
    @DisplayName("deserialize")
    class DeserializeTest {

        @Test
        @DisplayName("should deserialize standard format with 3 digit milliseconds")
        void shouldDeserializeStandardFormat() throws IOException {
            when(jsonParser.getCurrentToken()).thenReturn(JsonToken.VALUE_STRING);
            when(jsonParser.getText()).thenReturn("2024-03-15T10:30:45.123+01:00");

            OffsetDateTime result = deserializer.deserialize(jsonParser, deserializationContext);

            assertNotNull(result);
            assertEquals(2024, result.getYear());
            assertEquals(3, result.getMonthValue());
            assertEquals(15, result.getDayOfMonth());
            assertEquals(10, result.getHour());
            assertEquals(30, result.getMinute());
            assertEquals(45, result.getSecond());
            assertEquals(123000000, result.getNano());
            assertEquals(ZoneOffset.ofHours(1), result.getOffset());
        }

        @Test
        @DisplayName("should return null for non-string token")
        void shouldReturnNullForNonStringToken() throws IOException {
            when(jsonParser.getCurrentToken()).thenReturn(JsonToken.VALUE_NUMBER_INT);

            OffsetDateTime result = deserializer.deserialize(jsonParser, deserializationContext);

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
    @DisplayName("parseOffsetDateTime with variable milliseconds")
    class ParseVariableMillisecondsTest {

        @Test
        @DisplayName("should parse with 1 digit millisecond")
        void shouldParseWith1DigitMillisecond() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2024-03-15T10:30:45.1+01:00", formatter);

            assertNotNull(result);
            assertEquals(100000000, result.getNano());
        }

        @Test
        @DisplayName("should parse with 6 digit microseconds")
        void shouldParseWith6DigitMicroseconds() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2024-03-15T10:30:45.123456+01:00", formatter);

            assertNotNull(result);
            assertEquals(123456000, result.getNano());
        }

        @Test
        @DisplayName("should parse with 9 digit nanoseconds")
        void shouldParseWith9DigitNanoseconds() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2024-03-15T10:30:45.123456789+01:00", formatter);

            assertNotNull(result);
            assertEquals(123456789, result.getNano());
        }

        @Test
        @DisplayName("should parse without milliseconds")
        void shouldParseWithoutMilliseconds() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2024-03-15T10:30:45+01:00", formatter);

            assertNotNull(result);
            assertEquals(0, result.getNano());
        }
    }

    @Nested
    @DisplayName("parseOffsetDateTime without seconds")
    class ParseWithoutSecondsTest {

        @Test
        @DisplayName("should parse format without seconds")
        void shouldParseFormatWithoutSeconds() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2025-12-09T00:00+01:00", formatter);

            assertNotNull(result);
            assertEquals(2025, result.getYear());
            assertEquals(12, result.getMonthValue());
            assertEquals(9, result.getDayOfMonth());
            assertEquals(0, result.getHour());
            assertEquals(0, result.getMinute());
            assertEquals(0, result.getSecond());
        }

        @Test
        @DisplayName("should parse format without seconds with Z timezone")
        void shouldParseFormatWithoutSecondsWithZ() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2025-06-15T14:30Z", formatter);

            assertNotNull(result);
            assertEquals(14, result.getHour());
            assertEquals(30, result.getMinute());
            assertEquals(ZoneOffset.UTC, result.getOffset());
        }
    }

    @Nested
    @DisplayName("parseOffsetDateTime without timezone (fallback to CET)")
    class ParseWithoutTimezoneTest {

        @Test
        @DisplayName("should parse LocalDateTime and add CET offset")
        void shouldParseLocalDateTimeAndAddCetOffset() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2024-03-15T10:30:45", formatter);

            assertNotNull(result);
            assertEquals(2024, result.getYear());
            assertEquals(10, result.getHour());
            assertEquals(ZoneOffset.ofHours(1), result.getOffset()); // CET
        }

        @Test
        @DisplayName("should parse LocalDateTime with milliseconds and add CET offset")
        void shouldParseLocalDateTimeWithMillisecondsAndAddCetOffset() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2024-03-15T10:30:45.123", formatter);

            assertNotNull(result);
            assertEquals(123000000, result.getNano());
            assertEquals(ZoneOffset.ofHours(1), result.getOffset());
        }

        @Test
        @DisplayName("should parse LocalDateTime without seconds and add CET offset")
        void shouldParseLocalDateTimeWithoutSecondsAndAddCetOffset() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2024-03-15T10:30", formatter);

            assertNotNull(result);
            assertEquals(10, result.getHour());
            assertEquals(30, result.getMinute());
            assertEquals(0, result.getSecond());
            assertEquals(ZoneOffset.ofHours(1), result.getOffset());
        }
    }

    @Nested
    @DisplayName("parseOffsetDateTime edge cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            OffsetDateTime result = deserializer.parseOffsetDateTime(null, formatter);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for empty string")
        void shouldReturnNullForEmptyString() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("", formatter);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for whitespace string")
        void shouldReturnNullForWhitespaceString() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("   ", formatter);

            assertNull(result);
        }

        @Test
        @DisplayName("should trim whitespace before parsing")
        void shouldTrimWhitespaceBeforeParsing() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("  2024-03-15T10:30:45+01:00  ", formatter);

            assertNotNull(result);
            assertEquals(2024, result.getYear());
        }

        @Test
        @DisplayName("should throw DateTimeParseException for invalid format")
        void shouldThrowForInvalidFormat() {
            assertThrows(DateTimeParseException.class,
                    () -> deserializer.parseOffsetDateTime("not-a-date", formatter));
        }

        @Test
        @DisplayName("should handle UTC offset with Z")
        void shouldHandleUtcOffsetWithZ() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2024-03-15T10:30:45.123Z", formatter);

            assertNotNull(result);
            assertEquals(ZoneOffset.UTC, result.getOffset());
        }

        @Test
        @DisplayName("should handle negative offset")
        void shouldHandleNegativeOffset() {
            OffsetDateTime result = deserializer.parseOffsetDateTime("2024-03-15T10:30:45.123-05:00", formatter);

            assertNotNull(result);
            assertEquals(ZoneOffset.ofHours(-5), result.getOffset());
        }
    }
}
