package it.govpay.rt.batch.unit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import it.govpay.rt.batch.config.RtApiClientConfig;

@DisplayName("RtApiClientConfig")
class RtApiClientConfigTest {

    private RtApiClientConfig config;

    @BeforeEach
    void setUp() {
        config = new RtApiClientConfig();
        ReflectionTestUtils.setField(config, "timezone", "Europe/Rome");
    }

    @Test
    @DisplayName("createPagoPAObjectMapper should build a configured ObjectMapper")
    void shouldBuildConfiguredObjectMapper() {
        ObjectMapper objectMapper = config.createPagoPAObjectMapper();

        assertNotNull(objectMapper);
        assertEquals(TimeZone.getTimeZone("Europe/Rome"), objectMapper.getSerializationConfig().getTimeZone());
        assertTrue(objectMapper.isEnabled(DeserializationFeature.READ_ENUMS_USING_TO_STRING));
        assertTrue(objectMapper.isEnabled(SerializationFeature.WRITE_ENUMS_USING_TO_STRING));
        assertTrue(objectMapper.isEnabled(SerializationFeature.WRITE_DATES_WITH_ZONE_ID));
        assertFalse(objectMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    @Test
    @DisplayName("ObjectMapper should serialize and deserialize OffsetDateTime and LocalDate")
    void shouldSerializeAndDeserializeDates() throws Exception {
        ObjectMapper objectMapper = config.createPagoPAObjectMapper();

        OffsetDateTime odt = OffsetDateTime.of(2026, 1, 15, 10, 30, 45, 123_000_000, ZoneOffset.UTC);
        String odtJson = objectMapper.writeValueAsString(odt);
        OffsetDateTime odtParsed = objectMapper.readValue(odtJson, OffsetDateTime.class);
        assertNotNull(odtParsed);

        LocalDate date = LocalDate.of(2026, 1, 15);
        String dateJson = objectMapper.writeValueAsString(date);
        LocalDate dateParsed = objectMapper.readValue(dateJson, LocalDate.class);
        assertEquals(date, dateParsed);
    }
}
