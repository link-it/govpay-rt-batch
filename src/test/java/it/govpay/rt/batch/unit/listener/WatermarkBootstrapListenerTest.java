package it.govpay.rt.batch.unit.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.item.ExecutionContext;

import it.govpay.rt.batch.listener.WatermarkBootstrapListener;

@ExtendWith(MockitoExtension.class)
@DisplayName("WatermarkBootstrapListener")
class WatermarkBootstrapListenerTest {

    @Mock
    private JobExplorer jobExplorer;

    @Mock
    private JobExecution currentExecution;

    @Mock
    private JobInstance currentJobInstance;

    private WatermarkBootstrapListener listener;
    private ExecutionContext currentContext;

    private static final String JOB_NAME = "rtRetrieveJob";
    private static final Long CURRENT_EXECUTION_ID = 100L;

    @BeforeEach
    void setUp() {
        listener = new WatermarkBootstrapListener(jobExplorer);
        currentContext = new ExecutionContext();

        when(currentExecution.getId()).thenReturn(CURRENT_EXECUTION_ID);
        when(currentExecution.getJobInstance()).thenReturn(currentJobInstance);
        when(currentExecution.getExecutionContext()).thenReturn(currentContext);
        when(currentJobInstance.getJobName()).thenReturn(JOB_NAME);
    }

    @Nested
    @DisplayName("beforeJob")
    class BeforeJobTest {

        @Test
        @DisplayName("should set lastProcessedId to 0 when no previous executions exist")
        void shouldSetLastProcessedIdToZeroWhenNoPreviousExecutions() {
            when(jobExplorer.getJobInstances(JOB_NAME, 0, 10)).thenReturn(Collections.emptyList());

            listener.beforeJob(currentExecution);

            assertEquals(0L, currentContext.getLong("lastProcessedId"));
        }

        @Test
        @DisplayName("should set lastProcessedId from previous completed execution")
        void shouldSetLastProcessedIdFromPreviousExecution() {
            // Setup previous execution with lastProcessedId = 50
            JobInstance prevInstance = mock(JobInstance.class);
            JobExecution prevExecution = mock(JobExecution.class);
            ExecutionContext prevContext = new ExecutionContext();
            prevContext.putLong("lastProcessedId", 50L);

            when(prevExecution.getId()).thenReturn(99L);
            when(prevExecution.getExecutionContext()).thenReturn(prevContext);

            when(jobExplorer.getJobInstances(JOB_NAME, 0, 10)).thenReturn(Arrays.asList(prevInstance));
            when(jobExplorer.getJobExecutions(prevInstance)).thenReturn(Arrays.asList(prevExecution));

            listener.beforeJob(currentExecution);

            assertEquals(50L, currentContext.getLong("lastProcessedId"));
        }

        @Test
        @DisplayName("should skip current execution when searching for watermark")
        void shouldSkipCurrentExecutionWhenSearchingForWatermark() {
            // Setup: current execution also exists in job instances
            JobInstance instance = mock(JobInstance.class);
            JobExecution prevExecution = mock(JobExecution.class);
            ExecutionContext prevContext = new ExecutionContext();
            prevContext.putLong("lastProcessedId", 30L);

            when(prevExecution.getId()).thenReturn(99L);
            when(prevExecution.getExecutionContext()).thenReturn(prevContext);

            // Current execution should be skipped
            when(jobExplorer.getJobInstances(JOB_NAME, 0, 10)).thenReturn(Arrays.asList(instance));
            when(jobExplorer.getJobExecutions(instance)).thenReturn(Arrays.asList(currentExecution, prevExecution));

            listener.beforeJob(currentExecution);

            assertEquals(30L, currentContext.getLong("lastProcessedId"));
        }

        @Test
        @DisplayName("should skip executions with lastProcessedId = 0")
        void shouldSkipExecutionsWithLastProcessedIdZero() {
            JobInstance instance1 = mock(JobInstance.class);
            JobInstance instance2 = mock(JobInstance.class);

            // First execution has lastProcessedId = 0 (should be skipped)
            JobExecution execution1 = mock(JobExecution.class);
            ExecutionContext context1 = new ExecutionContext();
            context1.putLong("lastProcessedId", 0L);
            when(execution1.getId()).thenReturn(98L);
            when(execution1.getExecutionContext()).thenReturn(context1);

            // Second execution has lastProcessedId = 25
            JobExecution execution2 = mock(JobExecution.class);
            ExecutionContext context2 = new ExecutionContext();
            context2.putLong("lastProcessedId", 25L);
            when(execution2.getId()).thenReturn(97L);
            when(execution2.getExecutionContext()).thenReturn(context2);

            when(jobExplorer.getJobInstances(JOB_NAME, 0, 10)).thenReturn(Arrays.asList(instance1, instance2));
            when(jobExplorer.getJobExecutions(instance1)).thenReturn(Arrays.asList(execution1));
            when(jobExplorer.getJobExecutions(instance2)).thenReturn(Arrays.asList(execution2));

            listener.beforeJob(currentExecution);

            assertEquals(25L, currentContext.getLong("lastProcessedId"));
        }

        @Test
        @DisplayName("should skip executions without lastProcessedId key")
        void shouldSkipExecutionsWithoutLastProcessedIdKey() {
            JobInstance instance1 = mock(JobInstance.class);
            JobInstance instance2 = mock(JobInstance.class);

            // First execution doesn't have lastProcessedId
            JobExecution execution1 = mock(JobExecution.class);
            ExecutionContext context1 = new ExecutionContext();
            when(execution1.getId()).thenReturn(98L);
            when(execution1.getExecutionContext()).thenReturn(context1);

            // Second execution has lastProcessedId = 15
            JobExecution execution2 = mock(JobExecution.class);
            ExecutionContext context2 = new ExecutionContext();
            context2.putLong("lastProcessedId", 15L);
            when(execution2.getId()).thenReturn(97L);
            when(execution2.getExecutionContext()).thenReturn(context2);

            when(jobExplorer.getJobInstances(JOB_NAME, 0, 10)).thenReturn(Arrays.asList(instance1, instance2));
            when(jobExplorer.getJobExecutions(instance1)).thenReturn(Arrays.asList(execution1));
            when(jobExplorer.getJobExecutions(instance2)).thenReturn(Arrays.asList(execution2));

            listener.beforeJob(currentExecution);

            assertEquals(15L, currentContext.getLong("lastProcessedId"));
        }

        @Test
        @DisplayName("should return first valid watermark found")
        void shouldReturnFirstValidWatermarkFound() {
            JobInstance instance = mock(JobInstance.class);

            // Multiple executions - should return first valid one (most recent)
            JobExecution execution1 = mock(JobExecution.class);
            ExecutionContext context1 = new ExecutionContext();
            context1.putLong("lastProcessedId", 100L);
            when(execution1.getId()).thenReturn(99L);
            when(execution1.getExecutionContext()).thenReturn(context1);

            // execution2 is never reached because execution1 is valid
            JobExecution execution2 = mock(JobExecution.class);
            lenient().when(execution2.getId()).thenReturn(98L);

            when(jobExplorer.getJobInstances(JOB_NAME, 0, 10)).thenReturn(Arrays.asList(instance));
            when(jobExplorer.getJobExecutions(instance)).thenReturn(Arrays.asList(execution1, execution2));

            listener.beforeJob(currentExecution);

            assertEquals(100L, currentContext.getLong("lastProcessedId"));
        }
    }
}
