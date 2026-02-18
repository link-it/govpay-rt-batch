package it.govpay.rt.batch.listener;

import java.util.List;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import it.govpay.rt.batch.Costanti;

@Component
public class WatermarkBootstrapListener implements JobExecutionListener {

    private final JobExplorer jobExplorer;

    public WatermarkBootstrapListener(JobExplorer jobExplorer) {
        this.jobExplorer = jobExplorer;
    }

    @Override
    public void beforeJob(JobExecution current) {
        String jobName = current.getJobInstance().getJobName();

        Long last = findLastWatermark(jobName, current.getId());
        current.getExecutionContext().putLong(Costanti.LAST_PROCESSED_ID_KEY, last != null ? last : 0L);
    }

    private Long findLastWatermark(String jobName, Long currentExecutionId) {
        // prendi un po' di JobInstance recenti e cerca l’ultima che non è quella corrente e che ha un lastProcesseId valorizzato maggiore di 0
        List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, 10);

        for (JobInstance ji : instances) {
            for (JobExecution je : jobExplorer.getJobExecutions(ji)) {
                if (je.getId().equals(currentExecutionId)) continue;
                ExecutionContext ctx = je.getExecutionContext();
                if (ctx.containsKey(Costanti.LAST_PROCESSED_ID_KEY) && ctx.getLong(Costanti.LAST_PROCESSED_ID_KEY) > 0L) {
                	return ctx.getLong(Costanti.LAST_PROCESSED_ID_KEY);
                }
            }
        }
        return null;
    }
}