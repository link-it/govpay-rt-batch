package it.govpay.rt.batch.config;

import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.dto.RtRetrieveBatch;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.listener.BatchExecutionRecapListener;
import it.govpay.rt.batch.listener.WatermarkBootstrapListener;
import it.govpay.rt.batch.tasklet.RtRetrieveProcessor;
import it.govpay.rt.batch.tasklet.RtRetrieveReader;
import it.govpay.rt.batch.tasklet.RtRetrieveWriter;

/**
 * Configuration for RT retrieve Batch Job
 */
@Configuration
@Slf4j
public class BatchJobConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public BatchJobConfiguration(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    /**
     * Main RT Retrieve Job with 3 steps
     */
    @Bean
    public Job rtRetrieveJob(
        Step rtRetrieveTasklet,
        WatermarkBootstrapListener bootstrap,
        BatchExecutionRecapListener batchExecutionRecapListener
    ) {
        return new JobBuilder(Costanti.RT_RETRIEVE_JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .listener(bootstrap)
            .listener(batchExecutionRecapListener)
            .start(rtRetrieveTasklet)
            .build();
    }

    /**
     * Step: Retrieve missing receipt
     */
    @Bean
    public Step rtRetrieveTasklet(
        RtRetrieveReader rtRetrieveReader,
        RtRetrieveProcessor rtRetrieveProcessor,
        RtRetrieveWriter rtRetrieveWriter
    ) {
        return new StepBuilder("rtRetrieveTasklet", jobRepository)
            .<RtRetrieveContext, RtRetrieveBatch>chunk(1, transactionManager)
            .reader(rtRetrieveReader)
            .processor(rtRetrieveProcessor)
            .writer(rtRetrieveWriter)
            .build();
    }

}
