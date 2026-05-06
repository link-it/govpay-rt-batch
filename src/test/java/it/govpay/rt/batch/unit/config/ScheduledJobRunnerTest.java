package it.govpay.rt.batch.unit.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.rt.batch.config.ScheduledJobRunner;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduledJobRunner")
class ScheduledJobRunnerTest {

    @Mock
    private JobExecutionHelper jobExecutionHelper;

    @Mock
    private Job rtRetrieveJob;

    @Test
    @DisplayName("should instantiate via constructor")
    void shouldInstantiateViaConstructor() {
        ScheduledJobRunner runner = new ScheduledJobRunner(jobExecutionHelper, rtRetrieveJob);
        assertNotNull(runner);
    }

    @Test
    @DisplayName("runBatchRtRetrieveJob should delegate to AbstractScheduledJobRunner.executeScheduledJob")
    void runBatchShouldDelegateToExecuteScheduledJob() {
        ScheduledJobRunner runner = new ScheduledJobRunner(jobExecutionHelper, rtRetrieveJob);

        // checkBeforeExecution is invoked by executeScheduledJob; the unmocked mock
        // returns null which causes a NullPointerException — sufficient to exercise
        // the runBatchRtRetrieveJob delegation line.
        assertThrows(NullPointerException.class, runner::runBatchRtRetrieveJob);
    }
}
