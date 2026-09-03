package it.govpay.rt.batch.config;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClientException;

import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.dto.RtRecuperoBatch;
import it.govpay.rt.batch.dto.RtRetrieveBatch;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.listener.BatchExecutionRecapListener;
import it.govpay.rt.batch.listener.WatermarkBootstrapListener;
import it.govpay.rt.batch.tasklet.RtRecuperoProcessor;
import it.govpay.rt.batch.tasklet.RtRecuperoReader;
import it.govpay.rt.batch.tasklet.RtRecuperoRetentionTasklet;
import it.govpay.rt.batch.tasklet.RtRecuperoWriter;
import it.govpay.rt.batch.tasklet.RtRetrieveProcessor;
import it.govpay.rt.batch.tasklet.RtRetrieveReader;
import it.govpay.rt.batch.tasklet.RtRetrieveWriter;

/**
 * Configuration for RT retrieve Batch Job
 */
@Configuration
@Slf4j
public class BatchJobConfiguration {

    private static final long RETRY_INITIAL_INTERVAL_MS = 2000L;
    private static final double RETRY_MULTIPLIER = 2.0;
    private static final long RETRY_MAX_INTERVAL_MS = 10000L;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public BatchJobConfiguration(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    /**
     * Main RT Retrieve Job: scansione automatica su rendicontazioni, poi recupero
     * puntuale su richiesta nella stessa esecuzione, poi retention.
     */
    @Bean
    public Job rtRetrieveJob(
        Step rtRetrieveTasklet,
        Step rtRecuperoPuntualeStep,
        Step rtRecuperoRetentionStep,
        WatermarkBootstrapListener bootstrap,
        BatchExecutionRecapListener batchExecutionRecapListener
    ) {
        return new JobBuilder(Costanti.RT_RETRIEVE_JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .listener(bootstrap)
            .listener(batchExecutionRecapListener)
            .start(rtRetrieveTasklet)
            .next(rtRecuperoPuntualeStep)
            .next(rtRecuperoRetentionStep)
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
        // chunk(int, PlatformTransactionManager) e' deprecato da Spring Batch 6 e rimosso
        // in 7: il transaction manager si passa ora con la fluent .transactionManager().
        return new StepBuilder("rtRetrieveTasklet", jobRepository)
            .<RtRetrieveContext, RtRetrieveBatch>chunk(1)
            .transactionManager(transactionManager)
            .reader(rtRetrieveReader)
            .processor(rtRetrieveProcessor)
            .writer(rtRetrieveWriter)
            .build();
    }

    // ----- Recupero puntuale ---------------------------------------------

    /**
     * Retry sul 429 per il recupero puntuale, mirror del pattern gia' in
     * produzione in govpay-fdr-batch (BatchJobConfiguration.retryPolicy()).
     * Applicato dentro {@link RtRecuperoProcessor} via {@link RetryTemplate}
     * (non con {@code .faultTolerant()} sullo step): un fallimento definitivo
     * su una riga deve produrre un output "marcato" normale, non far fallire
     * l'intero step — altrimenti le righe successive non verrebbero mai lette.
     */
    @Bean
    public RetryPolicy rtRecuperoRetryPolicy(
            @Value("${govpay.batch.recupero-puntuale.max-retries:3}") int maxRetries) {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(RestClientException.class, true);
        retryableExceptions.put(IllegalArgumentException.class, false);
        retryableExceptions.put(NullPointerException.class, false);
        // Dominio/stazione/intermediario/connettore non configurati (RtApiService): non e' un
        // errore transitorio, ritentare non serve a nulla. Gestita per-item da RtRecuperoProcessor.
        retryableExceptions.put(IllegalStateException.class, false);
        return new SimpleRetryPolicy(maxRetries, retryableExceptions);
    }

    @Bean
    public BackOffPolicy rtRecuperoBackOffPolicy() {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(RETRY_INITIAL_INTERVAL_MS);
        backOffPolicy.setMultiplier(RETRY_MULTIPLIER);
        backOffPolicy.setMaxInterval(RETRY_MAX_INTERVAL_MS);
        return backOffPolicy;
    }

    @Bean
    public RetryTemplate rtRecuperoRetryTemplate(RetryPolicy rtRecuperoRetryPolicy, BackOffPolicy rtRecuperoBackOffPolicy) {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(rtRecuperoRetryPolicy);
        retryTemplate.setBackOffPolicy(rtRecuperoBackOffPolicy);
        return retryTemplate;
    }

    /**
     * Step: recupero puntuale su richiesta. Legge rt_recuperi, step
     * separato da rtRetrieveTasklet: nessuna interferenza sul watermark
     * (gia' inerte, bug tracciato in #21) ne' sulla finestra temporale.
     */
    @Bean
    public Step rtRecuperoPuntualeStep(
        RtRecuperoReader rtRecuperoReader,
        RtRecuperoProcessor rtRecuperoProcessor,
        RtRecuperoWriter rtRecuperoWriter
    ) {
        return new StepBuilder("rtRecuperoPuntualeStep", jobRepository)
            .<RtRetrieveContext, RtRecuperoBatch>chunk(1)
            .transactionManager(transactionManager)
            .reader(rtRecuperoReader)
            .processor(rtRecuperoProcessor)
            .writer(rtRecuperoWriter)
            .build();
    }

    /**
     * Step: retention delle righe rt_recuperi marcate.
     */
    @Bean
    public Step rtRecuperoRetentionStep(RtRecuperoRetentionTasklet rtRecuperoRetentionTasklet) {
        return new StepBuilder("rtRecuperoRetentionStep", jobRepository)
            .tasklet(rtRecuperoRetentionTasklet, transactionManager)
            .build();
    }

}
