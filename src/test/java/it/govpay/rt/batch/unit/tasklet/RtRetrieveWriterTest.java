package it.govpay.rt.batch.unit.tasklet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;

import it.govpay.rt.batch.dto.RtRetrieveBatch;
import it.govpay.rt.batch.tasklet.RtRetrieveWriter;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtRetrieveWriter")
class RtRetrieveWriterTest {

    @Mock
    private StepExecution stepExecution;

    @Mock
    private ExecutionContext executionContext;

    private RtRetrieveWriter writer;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";

    @BeforeEach
    void setUp() {
        writer = new RtRetrieveWriter();
    }

    @Nested
    @DisplayName("write")
    class WriteTest {

        @Test
        @DisplayName("should save max rtId to execution context when stepExecution is set")
        void shouldSaveMaxRtIdToExecutionContext() throws Exception {
            when(stepExecution.getExecutionContext()).thenReturn(executionContext);
            writer.beforeStep(stepExecution);

            RtRetrieveBatch batch1 = RtRetrieveBatch.builder()
                    .rtId(10L)
                    .codDominio(TAX_CODE)
                    .iuv(IUV)
                    .iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();

            RtRetrieveBatch batch2 = RtRetrieveBatch.builder()
                    .rtId(25L)
                    .codDominio(TAX_CODE)
                    .iuv(IUV)
                    .iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();

            RtRetrieveBatch batch3 = RtRetrieveBatch.builder()
                    .rtId(15L)
                    .codDominio(TAX_CODE)
                    .iuv(IUV)
                    .iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch1, batch2, batch3));

            writer.write(chunk);

            verify(executionContext).putLong("lastProcessedId", 25L);
        }

        @Test
        @DisplayName("should handle batch with message (error case)")
        void shouldHandleBatchWithMessage() throws Exception {
            when(stepExecution.getExecutionContext()).thenReturn(executionContext);
            writer.beforeStep(stepExecution);

            RtRetrieveBatch batch = RtRetrieveBatch.builder()
                    .rtId(10L)
                    .codDominio(TAX_CODE)
                    .iuv(IUV)
                    .iur(IUR)
                    .message("Receipt not found")
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch));

            // Should not throw - just logs the message
            assertDoesNotThrow(() -> writer.write(chunk));
            verify(executionContext).putLong("lastProcessedId", 10L);
        }

        @Test
        @DisplayName("should handle batch with retrivedTime (success case)")
        void shouldHandleBatchWithRetrivedTime() throws Exception {
            when(stepExecution.getExecutionContext()).thenReturn(executionContext);
            writer.beforeStep(stepExecution);

            RtRetrieveBatch batch = RtRetrieveBatch.builder()
                    .rtId(10L)
                    .codDominio(TAX_CODE)
                    .iuv(IUV)
                    .iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch));

            assertDoesNotThrow(() -> writer.write(chunk));
            verify(executionContext).putLong("lastProcessedId", 10L);
        }

        @Test
        @DisplayName("should filter out null items when calculating max id")
        void shouldFilterOutNullItemsWhenCalculatingMaxId() throws Exception {
            when(stepExecution.getExecutionContext()).thenReturn(executionContext);
            writer.beforeStep(stepExecution);

            RtRetrieveBatch batch1 = RtRetrieveBatch.builder()
                    .rtId(10L)
                    .codDominio(TAX_CODE)
                    .iuv(IUV)
                    .iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch1, null));

            writer.write(chunk);

            verify(executionContext).putLong("lastProcessedId", 10L);
        }

        @Test
        @DisplayName("should throw when chunk has only null items")
        void shouldThrowWhenChunkHasOnlyNullItems() throws Exception {
            // Use lenient stubbing since exception is thrown before getExecutionContext is called
            lenient().when(stepExecution.getExecutionContext()).thenReturn(executionContext);
            writer.beforeStep(stepExecution);

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(null, null));

            assertThrows(NoSuchElementException.class, () -> writer.write(chunk));
        }

        @Test
        @DisplayName("should not save to execution context when stepExecution is null")
        void shouldNotSaveWhenStepExecutionIsNull() throws Exception {
            // Don't call beforeStep - stepExecution will be null

            RtRetrieveBatch batch = RtRetrieveBatch.builder()
                    .rtId(10L)
                    .codDominio(TAX_CODE)
                    .iuv(IUV)
                    .iur(IUR)
                    .retrivedTime(LocalDateTime.now())
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch));

            // Should not throw even without stepExecution
            assertDoesNotThrow(() -> writer.write(chunk));
            verifyNoInteractions(executionContext);
        }
    }

    @Nested
    @DisplayName("beforeStep")
    class BeforeStepTest {

        @Test
        @DisplayName("should store stepExecution reference")
        void shouldStoreStepExecutionReference() {
            when(stepExecution.getExecutionContext()).thenReturn(executionContext);

            writer.beforeStep(stepExecution);

            // Verify by writing a chunk and checking interaction
            RtRetrieveBatch batch = RtRetrieveBatch.builder()
                    .rtId(10L)
                    .codDominio(TAX_CODE)
                    .iuv(IUV)
                    .iur(IUR)
                    .build();

            Chunk<RtRetrieveBatch> chunk = new Chunk<>(Arrays.asList(batch));

            assertDoesNotThrow(() -> writer.write(chunk));
            verify(stepExecution).getExecutionContext();
        }
    }
}
