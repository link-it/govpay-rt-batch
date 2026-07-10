package it.govpay.rt.batch.config;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.runner.AbstractScheduledJobRunner;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.rt.batch.Costanti;

/**
 * Runner per l'esecuzione schedulata del job RT Retrieve in modalita' multi-nodo.
 * <p>
 * Attivo solo con profile "default" (non "cron").
 */
@Component
@Profile("default")
@EnableScheduling
public class ScheduledJobRunner extends AbstractScheduledJobRunner {

    public ScheduledJobRunner(
            JobExecutionHelper jobExecutionHelper,
            @Qualifier("rtRetrieveJob") Job rtRetrieveJob) {
        super(jobExecutionHelper, rtRetrieveJob, Costanti.RT_RETRIEVE_JOB_NAME);
    }

    @Scheduled(
        fixedDelayString = "${scheduler.rtRetrieveJob.fixedDelayString:7200000}",
        initialDelayString = "${scheduler.initialDelayString:1}"
    )
    public JobExecution runBatchRtRetrieveJob() throws JobExecutionAlreadyRunningException, JobRestartException,
            JobInstanceAlreadyCompleteException, InvalidJobParametersException {
        return executeScheduledJob();
    }
}
