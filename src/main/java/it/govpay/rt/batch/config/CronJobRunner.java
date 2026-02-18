package it.govpay.rt.batch.config;

import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.runner.AbstractCronJobRunner;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.rt.batch.Costanti;

/**
 * Runner per l'esecuzione da command line del job RT Retrieve in modalita' multi-nodo.
 * <p>
 * Attivo solo con profile "cron" (non "default").
 */
@Component
@Profile("cron")
public class CronJobRunner extends AbstractCronJobRunner {

    public CronJobRunner(
            JobExecutionHelper jobExecutionHelper,
            @Qualifier("rtRetrieveJob") Job rtRetrieveJob) {
        super(jobExecutionHelper, rtRetrieveJob, Costanti.RT_RETRIEVE_JOB_NAME);
    }
}
