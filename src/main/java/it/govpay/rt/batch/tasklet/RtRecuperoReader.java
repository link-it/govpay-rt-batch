package it.govpay.rt.batch.tasklet;

import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.entity.RtRecupero;
import it.govpay.rt.batch.repository.RtRecuperoRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Reader per il recupero puntuale: righe di {@code rt_recuperi}
 * con {@code esito IS NULL}. A differenza di {@link RtRetrieveReader} nessuna
 * finestra temporale e nessun {@code lastProcessedId}: una richiesta esplicita
 * dell'operatore vale a prescindere dall'eta' del pagamento, ed e' una
 * sorgente indipendente dal watermark della scansione su {@code rendicontazioni}.
 */
@Component
@StepScope
@Slf4j
public class RtRecuperoReader implements ItemReader<RtRetrieveContext>, StepExecutionListener {

    private final RtRecuperoRepository rtRecuperoRepository;

    private List<RtRetrieveContext> toBeRetrieveList = null;

    public RtRecuperoReader(RtRecuperoRepository rtRecuperoRepository) {
        this.rtRecuperoRepository = rtRecuperoRepository;
    }

    @BeforeStep
    public void initToBeRetrieve() {
        toBeRetrieveList = new ArrayList<>();
        List<RtRecupero> righe = rtRecuperoRepository.findByEsitoIsNullOrderByIdAsc();
        log.info("Trovate {} righe di recupero puntuale da elaborare", righe.size());
        for (RtRecupero riga : righe) {
            log.debug("Recupero puntuale da elaborare id {}, codDominio {}, iuv {}, iur {}",
                    riga.getId(), riga.getCodDominio(), riga.getIuv(), riga.getIur());
            toBeRetrieveList.add(RtRetrieveContext.builder()
                    .rtId(riga.getId())
                    .taxCode(riga.getCodDominio())
                    .iuv(riga.getIuv())
                    .iur(riga.getIur())
                    .idOperatore(riga.getIdOperatore())
                    .build());
        }
    }

    @Override
    public RtRetrieveContext read() {
        if (!toBeRetrieveList.isEmpty())
            return toBeRetrieveList.remove(0);
        log.info("Nessun'altra riga di recupero puntuale da elaborare");
        return null;
    }
}
