package it.govpay.rt.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;

import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.utils.OffsetDateTimeSerializer;

@ExtendWith(MockitoExtension.class)
@DisplayName("OffsetDateTimeSerializer")
class OffsetDateTimeSerializerTest {

    @Mock
    private JsonGenerator jsonGenerator;

    @Mock
    private SerializerProvider serializerProvider;

    @Nested
    @DisplayName("serialize with default constructor")
    class SerializeDefaultTest {

        @Test
        @DisplayName("should serialize OffsetDateTime with default pattern")
        void shouldSerializeWithDefaultPattern() throws IOException {
            OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer();
            OffsetDateTime dateTime = OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 123000000, ZoneOffset.ofHours(1));

            serializer.serialize(dateTime, jsonGenerator, serializerProvider);

            verify(jsonGenerator).writeString("2024-03-15T10:30:45.123+01:00");
        }

        @Test
        @DisplayName("should serialize null as null string")
        void shouldSerializeNullAsNullString() throws IOException {
            OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer();

            serializer.serialize(null, jsonGenerator, serializerProvider);

            verify(jsonGenerator).writeString((String) null);
        }

        @Test
        @DisplayName("should serialize UTC offset correctly")
        void shouldSerializeUtcOffsetCorrectly() throws IOException {
            OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer();
            OffsetDateTime dateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

            serializer.serialize(dateTime, jsonGenerator, serializerProvider);

            verify(jsonGenerator).writeString("2024-01-01T00:00:00.000Z");
        }
    }

    @Nested
    @DisplayName("serialize with custom pattern")
    class SerializeCustomPatternTest {

        @Test
        @DisplayName("should serialize with custom pattern without timezone")
        void shouldSerializeWithCustomPatternWithoutTimezone() throws IOException {
            OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer(
                    Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS);
            OffsetDateTime dateTime = OffsetDateTime.of(2024, 6, 20, 14, 25, 30, 500000000, ZoneOffset.ofHours(2));

            serializer.serialize(dateTime, jsonGenerator, serializerProvider);

            verify(jsonGenerator).writeString("2024-06-20T14:25:30.500");
        }

        @Test
        @DisplayName("should serialize with full timestamp pattern including timezone")
        void shouldSerializeWithFullTimestampPattern() throws IOException {
            OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer(
                    Costanti.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX);
            OffsetDateTime dateTime = OffsetDateTime.of(2024, 12, 31, 23, 59, 59, 999000000, ZoneOffset.ofHours(-5));

            serializer.serialize(dateTime, jsonGenerator, serializerProvider);

            verify(jsonGenerator).writeString("2024-12-31T23:59:59.999-05:00");
        }

        @Test
        @DisplayName("should handle zero milliseconds")
        void shouldHandleZeroMilliseconds() throws IOException {
            OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer(
                    Costanti.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX);
            OffsetDateTime dateTime = OffsetDateTime.of(2024, 5, 10, 12, 0, 0, 0, ZoneOffset.ofHours(1));

            serializer.serialize(dateTime, jsonGenerator, serializerProvider);

            verify(jsonGenerator).writeString("2024-05-10T12:00:00.000+01:00");
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("should handle negative offset")
        void shouldHandleNegativeOffset() throws IOException {
            OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer();
            OffsetDateTime dateTime = OffsetDateTime.of(2024, 7, 4, 8, 0, 0, 0, ZoneOffset.ofHours(-8));

            serializer.serialize(dateTime, jsonGenerator, serializerProvider);

            verify(jsonGenerator).writeString("2024-07-04T08:00:00.000-08:00");
        }

        @Test
        @DisplayName("should handle offset with minutes")
        void shouldHandleOffsetWithMinutes() throws IOException {
            OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer();
            OffsetDateTime dateTime = OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

            serializer.serialize(dateTime, jsonGenerator, serializerProvider);

            verify(jsonGenerator).writeString("2024-01-01T12:00:00.000+05:30");
        }
    }
}
