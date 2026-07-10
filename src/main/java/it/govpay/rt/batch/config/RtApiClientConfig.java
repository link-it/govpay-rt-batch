package it.govpay.rt.batch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.utils.LocalDateFlexibleDeserializer;
import it.govpay.rt.batch.utils.OffsetDateTimeDeserializer;
import it.govpay.rt.batch.utils.OffsetDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.TimeZone;

/**
 * Configuration for RT API client.
 * Provides the custom ObjectMapper for pagoPA date handling,
 * used by RtApiService when creating per-domain RestTemplate instances.
 */
@Slf4j
@Configuration
public class RtApiClientConfig {

    @Value("${spring.jackson.time-zone:Europe/Rome}")
    private String timezone;

    /**
     * Creates a custom ObjectMapper for pagoPA API client with enhanced date handling security.
     * <p>
     * Configuration:
     * - Serialization: uses fixed format yyyy-MM-dd'T'HH:mm:ss.SSS
     * - Deserialization: accepts variable-length milliseconds (1-9 digits) for security
     * - Fallback: if timezone is missing, defaults to CET
     * - Dates: written as ISO-8601 strings (not timestamps) with zone ID
     * - Timezone: configured from spring.jackson.time-zone property
     *
     * @return configured JsonMapper for pagoPA API
     */
    public JsonMapper createPagoPAObjectMapper() {
        // Register custom date serializers/deserializers in a dedicated module.
        // Serializer: fixed 3-digit milliseconds format for outgoing requests
        // Deserializer: flexible format accepting variable milliseconds from pagoPA responses
        // LocalDate deserializer: handles both date and datetime formats from pagoPA
        SimpleModule dateModule = new SimpleModule();
        dateModule.addSerializer(
            OffsetDateTime.class,
            new OffsetDateTimeSerializer(Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS)
        );
        dateModule.addDeserializer(
            OffsetDateTime.class,
            new OffsetDateTimeDeserializer(Costanti.PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI_XXX)
        );
        dateModule.addDeserializer(
            LocalDate.class,
            new LocalDateFlexibleDeserializer()
        );

        // Jackson 3 ObjectMapper is immutable: configuration is applied via the builder.
        return JsonMapper.builder()
            // Timezone from configuration
            .defaultTimeZone(TimeZone.getTimeZone(timezone))
            // Date format for java.util.Date (legacy support)
            .defaultDateFormat(new SimpleDateFormat(Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS))
            .addModule(dateModule)
            // Enum and date serialization
            .enable(EnumFeature.READ_ENUMS_USING_TO_STRING)
            .enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
            .enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    }
}
