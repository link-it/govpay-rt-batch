package it.govpay.rt.batch.tasklet;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.repository.RendicontazioniRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Reader for receipt to be retrive.
 */
@Component
@StepScope
@Slf4j
public class RtRetrieveReader implements ItemReader<RtRetrieveContext>, StepExecutionListener {

    private final RendicontazioniRepository rndRepository;
    private final Clock clock;
    private final int finestraTemporale;

    private List<RtRetrieveContext> toBeRetrieveList = null;

    public RtRetrieveReader(
    		RendicontazioniRepository rndRepository,
    		Clock clock,
    		@Value("${govpay.batch.finestra-temporale:90}") int finestraTemporale) {
        this.rndRepository = rndRepository;
        this.clock = clock;
        this.finestraTemporale = finestraTemporale;
    }

    @BeforeStep
    public void initToBeRetrieve() {
		toBeRetrieveList = new ArrayList<>();
		LocalDateTime dataLimite = LocalDateTime.now(clock).minusDays(finestraTemporale);
		List<Object[]> rndInfos = rndRepository.findRendicontazioneWithNoPagamento(dataLimite);
		log.info("Trovate {} ricevute da recuperare", rndInfos.size());

		long escluseDallaFinestra = rndRepository.countRendicontazioneEsclusaDallaFinestra(dataLimite);
		if (escluseDallaFinestra > 0) {
			log.warn("{} rendicontazioni con esegui_recupero_rt=true escluse dalla scansione perche' "
					+ "fuori dalla finestra temporale di {} giorni: restano candidate a DB ma non verranno "
					+ "piu' ritentate automaticamente, richiedono analisi manuale", escluseDallaFinestra, finestraTemporale);
		}
		for (Object[] rndInfo : rndInfos) {
			log.debug("Ricevuta da recuperare id {}, taxCode {}, iuv {}, iur {}", rndInfo[0], rndInfo[1], rndInfo[2], rndInfo[3]);
			RtRetrieveContext rtRetrieveCtx = RtRetrieveContext.builder()
			                                                   .rtId(convertToLong(rndInfo[0]))
			                                                   .taxCode((String)rndInfo[1])
			                                                   .iuv((String)rndInfo[2])
			                                                   .iur((String)rndInfo[3])
			                                                   .build();
			toBeRetrieveList.add(rtRetrieveCtx);
		}
    }

    private Long convertToLong(Object object) {
    	if (object instanceof Long longId)
    		return longId;
    	if (object instanceof BigInteger bigId)
    		return bigId.longValue();
    	throw new IllegalArgumentException("Class not convert to long" + object.getClass().getName());
	}

	@Override
    public RtRetrieveContext read() {
		log.info("Start read rt retrieve item");
    	if (!toBeRetrieveList.isEmpty())
    		return toBeRetrieveList.remove(0);
        log.info("Nessun altra ricevuta da recuperare");
        return null;
    }
}
