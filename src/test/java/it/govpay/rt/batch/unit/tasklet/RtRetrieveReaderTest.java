package it.govpay.rt.batch.unit.tasklet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.repository.RendicontazioniRepository;
import it.govpay.rt.batch.tasklet.RtRetrieveReader;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtRetrieveReader")
class RtRetrieveReaderTest {

    @Mock
    private RendicontazioniRepository rndRepository;

    private static final int FINESTRA_TEMPORALE = 30;

    private static final String TAX_CODE_1 = "12345678901";
    private static final String TAX_CODE_2 = "98765432101";
    private static final String IUV_1 = "01234567890123456";
    private static final String IUV_2 = "65432109876543210";
    private static final String IUR_1 = "IUR123456";
    private static final String IUR_2 = "IUR654321";

    @Nested
    @DisplayName("initToBeRetrieve")
    class InitToBeRetrieveTest {

        @Test
        @DisplayName("should query repository without lastProcessedId when lastProcessedId is 0")
        void shouldQueryRepositoryWithoutLastProcessedIdWhenZero() {
            RtRetrieveReader reader = new RtRetrieveReader(rndRepository, FINESTRA_TEMPORALE, 0L);
            when(rndRepository.findRendicontazioneWithNoPagamento(any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            reader.initToBeRetrieve();

            verify(rndRepository).findRendicontazioneWithNoPagamento(any(LocalDateTime.class));
            verify(rndRepository, never()).findRendicontazioneWithNoPagamentoAfterId(anyLong(), any());
        }

        @Test
        @DisplayName("should query repository with lastProcessedId when lastProcessedId > 0")
        void shouldQueryRepositoryWithLastProcessedIdWhenGreaterThanZero() {
            RtRetrieveReader reader = new RtRetrieveReader(rndRepository, FINESTRA_TEMPORALE, 100L);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(eq(100L), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            reader.initToBeRetrieve();

            verify(rndRepository).findRendicontazioneWithNoPagamentoAfterId(eq(100L), any(LocalDateTime.class));
            verify(rndRepository, never()).findRendicontazioneWithNoPagamento(any());
        }

        @Test
        @DisplayName("should populate list with results from repository using Long ids")
        void shouldPopulateListWithResultsUsingLongIds() {
            RtRetrieveReader reader = new RtRetrieveReader(rndRepository, FINESTRA_TEMPORALE, 0L);

            List<Object[]> results = new ArrayList<>();
            results.add(new Object[]{1L, TAX_CODE_1, IUR_1, IUV_1});
            results.add(new Object[]{2L, TAX_CODE_2, IUR_2, IUV_2});
            when(rndRepository.findRendicontazioneWithNoPagamento(any(LocalDateTime.class)))
                    .thenReturn(results);

            reader.initToBeRetrieve();

            // Read all items to verify
            RtRetrieveContext first = reader.read();
            assertNotNull(first);
            assertEquals(1L, first.getRtId());
            assertEquals(TAX_CODE_1, first.getTaxCode());
            assertEquals(IUR_1, first.getIur());
            assertEquals(IUV_1, first.getIuv());

            RtRetrieveContext second = reader.read();
            assertNotNull(second);
            assertEquals(2L, second.getRtId());
        }

        @Test
        @DisplayName("should handle BigInteger ids from repository")
        void shouldHandleBigIntegerIdsFromRepository() {
            RtRetrieveReader reader = new RtRetrieveReader(rndRepository, FINESTRA_TEMPORALE, 0L);

            List<Object[]> results = new ArrayList<>();
            results.add(new Object[]{BigInteger.valueOf(999L), TAX_CODE_1, IUR_1, IUV_1});
            when(rndRepository.findRendicontazioneWithNoPagamento(any(LocalDateTime.class)))
                    .thenReturn(results);

            reader.initToBeRetrieve();

            RtRetrieveContext result = reader.read();
            assertNotNull(result);
            assertEquals(999L, result.getRtId());
        }
    }

    @Nested
    @DisplayName("read")
    class ReadTest {

        @Test
        @DisplayName("should return items in order and null when exhausted")
        void shouldReturnItemsInOrderAndNullWhenExhausted() {
            RtRetrieveReader reader = new RtRetrieveReader(rndRepository, FINESTRA_TEMPORALE, 0L);

            List<Object[]> results = new ArrayList<>();
            results.add(new Object[]{1L, TAX_CODE_1, IUR_1, IUV_1});
            results.add(new Object[]{2L, TAX_CODE_2, IUR_2, IUV_2});
            when(rndRepository.findRendicontazioneWithNoPagamento(any(LocalDateTime.class)))
                    .thenReturn(results);

            reader.initToBeRetrieve();

            // First read
            RtRetrieveContext first = reader.read();
            assertNotNull(first);
            assertEquals(1L, first.getRtId());

            // Second read
            RtRetrieveContext second = reader.read();
            assertNotNull(second);
            assertEquals(2L, second.getRtId());

            // Third read - should be null
            RtRetrieveContext third = reader.read();
            assertNull(third);
        }

        @Test
        @DisplayName("should return null immediately when no items")
        void shouldReturnNullImmediatelyWhenNoItems() {
            RtRetrieveReader reader = new RtRetrieveReader(rndRepository, FINESTRA_TEMPORALE, 0L);
            when(rndRepository.findRendicontazioneWithNoPagamento(any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            reader.initToBeRetrieve();

            RtRetrieveContext result = reader.read();
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("convertToLong")
    class ConvertToLongTest {

        @Test
        @DisplayName("should throw IllegalArgumentException for unsupported types")
        void shouldThrowForUnsupportedTypes() {
            RtRetrieveReader reader = new RtRetrieveReader(rndRepository, FINESTRA_TEMPORALE, 0L);

            // Create a result with an Integer (unsupported)
            List<Object[]> results = new ArrayList<>();
            results.add(new Object[]{Integer.valueOf(1), TAX_CODE_1, IUR_1, IUV_1});
            when(rndRepository.findRendicontazioneWithNoPagamento(any(LocalDateTime.class)))
                    .thenReturn(results);

            assertThrows(IllegalArgumentException.class, () -> reader.initToBeRetrieve());
        }
    }
}
