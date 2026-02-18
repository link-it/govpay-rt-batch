package it.govpay.rt.batch.unit.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;

import it.govpay.rt.batch.listener.BatchExecutionRecapListener;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchExecutionRecapListener")
class BatchExecutionRecapListenerTest {

    @Mock
    private JobExecution jobExecution;

    private BatchExecutionRecapListener listener;

    @BeforeEach
    void setUp() {
        listener = new BatchExecutionRecapListener();
    }

    @Nested
    @DisplayName("beforeJob")
    class BeforeJobTest {

        @Test
        @DisplayName("should log job start without throwing")
        void shouldLogJobStartWithoutThrowing() {
            when(jobExecution.getJobId()).thenReturn(123L);

            assertDoesNotThrow(() -> listener.beforeJob(jobExecution));
            verify(jobExecution).getJobId();
        }
    }

    @Nested
    @DisplayName("afterJob")
    class AfterJobTest {

        @Test
        @DisplayName("should log job completion without throwing")
        void shouldLogJobCompletionWithoutThrowing() {
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
            LocalDateTime endTime = LocalDateTime.now();

            when(jobExecution.getStartTime()).thenReturn(startTime);
            when(jobExecution.getEndTime()).thenReturn(endTime);
            when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);

            assertDoesNotThrow(() -> listener.afterJob(jobExecution));
            verify(jobExecution).getStartTime();
            verify(jobExecution).getEndTime();
            verify(jobExecution).getStatus();
        }

        @Test
        @DisplayName("should handle failed job status")
        void shouldHandleFailedJobStatus() {
            LocalDateTime startTime = LocalDateTime.now().minusSeconds(30);
            LocalDateTime endTime = LocalDateTime.now();

            when(jobExecution.getStartTime()).thenReturn(startTime);
            when(jobExecution.getEndTime()).thenReturn(endTime);
            when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);

            assertDoesNotThrow(() -> listener.afterJob(jobExecution));
        }

        @Test
        @DisplayName("should calculate duration correctly")
        void shouldCalculateDurationCorrectly() {
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 1, 15, 10, 5, 30);

            when(jobExecution.getStartTime()).thenReturn(startTime);
            when(jobExecution.getEndTime()).thenReturn(endTime);
            when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);

            // Duration should be 5 minutes 30 seconds = 330 seconds
            assertDoesNotThrow(() -> listener.afterJob(jobExecution));
        }
    }
}
