package it.govpay.rt.batch.tasklet;

import java.util.Objects;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.dto.RtRetrieveBatch;
import it.govpay.rt.batch.repository.RendicontazioniRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Writer to regisger last processed Id
 */
@Component
@Slf4j
public class RtRetrieveWriter implements ItemWriter<RtRetrieveBatch> {

    private final RendicontazioniRepository rendicontazioniRepository;
    private StepExecution stepExecution;

    public RtRetrieveWriter(RendicontazioniRepository rendicontazioniRepository) {
    	this.rendicontazioniRepository = rendicontazioniRepository;
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    @Transactional
    public void write(Chunk<? extends RtRetrieveBatch> chunk) {
        for (RtRetrieveBatch batch : chunk) {
            if (batch == null)
                log.info("Internal error: no retrieve processor output");
            else {
                rendicontazioniRepository.disableRecuperoRt(batch.getRtId());
                if (batch.getMessage() != null)
                    log.info(batch.getMessage());
                if (batch.getRetrivedTime() != null)
                    log.info("Ricevuta recuperata: taxCode {} - iur {} - iuv {} ",
                             batch.getCodDominio(), batch.getIur(), batch.getIuv());
            }
        }
        if (stepExecution != null) {
            long maxId = chunk.getItems().stream()
            		                     .filter(Objects::nonNull)
                                         .mapToLong(RtRetrieveBatch::getRtId)
                                         .max()
                                         .orElseThrow();
            stepExecution.getExecutionContext().putLong(Costanti.LAST_PROCESSED_ID_KEY, maxId);
        }
    }
}
