package it.govpay.rt.batch.tasklet;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.rt.batch.repository.RtRecuperoRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Retention delle righe {@code rt_recuperi} marcate:
 * elimina le righe con {@code esito} valorizzato più vecchie
 * della soglia configurata. Senza questa procedura la tabella crescerebbe
 * senza limite — le righe marcate non si eliminano da sole.
 */
@Component
@Slf4j
public class RtRecuperoRetentionTasklet implements Tasklet {

    private final RtRecuperoRepository rtRecuperoRepository;
    private final Clock clock;
    private final int retentionGiorni;

    public RtRecuperoRetentionTasklet(RtRecuperoRepository rtRecuperoRepository, Clock clock,
            @Value("${govpay.batch.recupero-puntuale.retention-giorni:7}") int retentionGiorni) {
        this.rtRecuperoRepository = rtRecuperoRepository;
        this.clock = clock;
        this.retentionGiorni = retentionGiorni;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        OffsetDateTime soglia = OffsetDateTime.now(clock).minusDays(retentionGiorni);
        int eliminate = rtRecuperoRepository.eliminaMarcatePrimaDi(soglia);
        log.info("Retention rt_recuperi: eliminate {} righe marcate precedenti a {}", eliminate, soglia);
        return RepeatStatus.FINISHED;
    }
}
