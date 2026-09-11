package it.govpay.rt.batch.unit.tasklet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

import it.govpay.rt.batch.dto.RtRetrieveBatch;
import it.govpay.rt.batch.repository.RendicontazioniRepository;
import it.govpay.rt.batch.tasklet.RtRetrieveWriter;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtRetrieveWriter")
class RtRetrieveWriterTest {

    @Mock
    private RendicontazioniRepository rendicontazioniRepository;

    private RtRetrieveWriter writer;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";

    @BeforeEach
    void setUp() {
        writer = new RtRetrieveWriter(rendicontazioniRepository);
    }

    @Nested
    @DisplayName("write")
    class WriteTest {

        @Test
        @DisplayName("should disable esegui_recupero_rt for every non-null, non-retryable item")
        void shouldDisableRecuperoRtForEachTerminalItem() {
            RtRetrieveBatch batch1 = RtRetrieveBatch.builder()
                    .rtId(10L).codDominio(TAX_CODE).iuv(IUV).iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();
            RtRetrieveBatch batch2 = RtRetrieveBatch.builder()
                    .rtId(25L).codDominio(TAX_CODE).iuv(IUV).iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch1, batch2));

            writer.write(chunk);

            verify(rendicontazioniRepository).disableRecuperoRt(10L);
            verify(rendicontazioniRepository).disableRecuperoRt(25L);
        }

        @Test
        @DisplayName("should handle batch with message (error case)")
        void shouldHandleBatchWithMessage() {
            RtRetrieveBatch batch = RtRetrieveBatch.builder()
                    .rtId(10L).codDominio(TAX_CODE).iuv(IUV).iur(IUR)
                    .message("Send to govpay failed")
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch));

            assertDoesNotThrow(() -> writer.write(chunk));
            verify(rendicontazioniRepository).disableRecuperoRt(10L);
        }

        @Test
        @DisplayName("should handle batch with retrivedTime (success case)")
        void shouldHandleBatchWithRetrivedTime() {
            RtRetrieveBatch batch = RtRetrieveBatch.builder()
                    .rtId(10L).codDominio(TAX_CODE).iuv(IUV).iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch));

            assertDoesNotThrow(() -> writer.write(chunk));
            verify(rendicontazioniRepository).disableRecuperoRt(10L);
        }

        @Test
        @DisplayName("should not disable esegui_recupero_rt for a retryable item (404 Receipt not found)")
        void shouldNotDisableRecuperoRtForRetryableItem() {
            RtRetrieveBatch batch = RtRetrieveBatch.builder()
                    .rtId(10L).codDominio(TAX_CODE).iuv(IUV).iur(IUR)
                    .message("Receipt not found")
                    .retryable(true)
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch));

            writer.write(chunk);

            verify(rendicontazioniRepository, never()).disableRecuperoRt(anyLong());
        }

        @Test
        @DisplayName("should disable terminal items but not a retryable one in the same chunk")
        void shouldDistinguishRetryableFromTerminalInSameChunk() {
            RtRetrieveBatch success = RtRetrieveBatch.builder()
                    .rtId(10L).codDominio(TAX_CODE).iuv(IUV).iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();
            RtRetrieveBatch notFound = RtRetrieveBatch.builder()
                    .rtId(11L).codDominio(TAX_CODE).iuv(IUV).iur(IUR)
                    .message("Receipt not found")
                    .retryable(true)
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(success, notFound));

            writer.write(chunk);

            verify(rendicontazioniRepository).disableRecuperoRt(10L);
            verify(rendicontazioniRepository, never()).disableRecuperoRt(11L);
        }

        @Test
        @DisplayName("should tolerate a null item without throwing")
        void shouldTolerateNullItem() {
            RtRetrieveBatch batch = RtRetrieveBatch.builder()
                    .rtId(10L).codDominio(TAX_CODE).iuv(IUV).iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch, null));

            assertDoesNotThrow(() -> writer.write(chunk));
            verify(rendicontazioniRepository).disableRecuperoRt(10L);
        }
    }
}
