package it.govpay.rt.batch.unit.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.rt.batch.config.CronJobRunner;

@ExtendWith(MockitoExtension.class)
@DisplayName("CronJobRunner")
class CronJobRunnerTest {

    @Mock
    private JobExecutionHelper jobExecutionHelper;

    @Mock
    private Job rtRetrieveJob;

    @Test
    @DisplayName("should instantiate via constructor delegating to AbstractCronJobRunner")
    void shouldInstantiateViaConstructor() {
        CronJobRunner runner = new CronJobRunner(jobExecutionHelper, rtRetrieveJob);
        assertNotNull(runner);
    }
}
