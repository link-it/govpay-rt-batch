package it.govpay.rt.batch.tasklet;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.rt.batch.dto.RtRetrieveBatch;
import it.govpay.rt.batch.repository.RendicontazioniRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Writer per la scansione automatica: disabilita {@code esegui_recupero_rt}
 * sugli esiti terminali, la lascia intatta su quelli ritentabili
 * ({@link RtRetrieveBatch#isRetryable()}) cosi' che il prossimo giro
 * schedulato la riprenda (vedi {@link RtRetrieveProcessor} sul 404).
 */
@Component
@Slf4j
public class RtRetrieveWriter implements ItemWriter<RtRetrieveBatch> {

    private final RendicontazioniRepository rendicontazioniRepository;

    public RtRetrieveWriter(RendicontazioniRepository rendicontazioniRepository) {
    	this.rendicontazioniRepository = rendicontazioniRepository;
    }

    @Override
    @Transactional
    public void write(Chunk<? extends RtRetrieveBatch> chunk) {
        for (RtRetrieveBatch batch : chunk) {
            if (batch == null) {
                log.info("Internal error: no retrieve processor output");
                continue;
            }
            if (!batch.isRetryable()) {
                rendicontazioniRepository.disableRecuperoRt(batch.getRtId());
            }
            if (batch.getMessage() != null)
                log.info(batch.getMessage());
            if (batch.getRetrivedTime() != null)
                log.info("Ricevuta recuperata: taxCode {} - iur {} - iuv {} ",
                         batch.getCodDominio(), batch.getIur(), batch.getIuv());
        }
    }
}
