package it.govpay.rt.batch.tasklet;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.rt.batch.dto.RtRecuperoBatch;
import it.govpay.rt.batch.repository.RtRecuperoRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Writer per il recupero puntuale: elimina la riga su successo,
 * la marca su esito negativo — mai il contrario di {@link RtRetrieveWriter},
 * che invece disabilita sempre la rendicontazione. Non chiama
 * {@code disableRecuperoRt} (keyed sulla rendicontazione, non pertinente qui)
 * e non scrive {@code lastProcessedId}: questo step non ha un proprio
 * watermark, ogni riga e' indipendente.
 *
 * <p>Eliminazione/marcatura avvengono **dopo** il tentativo, nella stessa
 * transazione dell'esito (chunk(1) + {@code @Transactional}): se il batch si
 * interrompe fra l'eliminazione e l'invio la richiesta e' persa; se si
 * interrompe fra l'invio e l'eliminazione, al giro successivo si rimanda la
 * stessa ricevuta e {@code api-pagopa} risponde {@code PAA_RECEIPT_DUPLICATA}
 * (innocuo). Fra i due rischi si sceglie il secondo.
 */
@Component
@Slf4j
public class RtRecuperoWriter implements ItemWriter<RtRecuperoBatch> {

    private final RtRecuperoRepository rtRecuperoRepository;
    private final Clock clock;

    public RtRecuperoWriter(RtRecuperoRepository rtRecuperoRepository, Clock clock) {
        this.rtRecuperoRepository = rtRecuperoRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void write(Chunk<? extends RtRecuperoBatch> chunk) {
        for (RtRecuperoBatch batch : chunk) {
            if (batch == null) {
                log.info("Internal error: no recupero processor output");
                continue;
            }
            if (batch.getEsito() == null) {
                rtRecuperoRepository.deleteById(batch.getId());
                log.info("Recupero puntuale riuscito, riga eliminata: id {} - taxCode {} - iur {} - iuv {}",
                        batch.getId(), batch.getCodDominio(), batch.getIur(), batch.getIuv());
            } else {
                rtRecuperoRepository.marca(batch.getId(), batch.getEsito(), OffsetDateTime.now(clock));
                log.info("Recupero puntuale non riuscito, riga marcata '{}': id {} - taxCode {} - iur {} - iuv {} - {}",
                        batch.getEsito(), batch.getId(), batch.getCodDominio(), batch.getIur(), batch.getIuv(), batch.getMessage());
            }
        }
    }
}
