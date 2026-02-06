package it.govpay.rt.batch.tasklet;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.govpay.rt.batch.config.PagoPAProperties;
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
    private final PagoPAProperties pagoPAProperties;
    private final long lastProcessedId;

    private List<RtRetrieveContext> toBeRetrieveList = null;

    public RtRetrieveReader(
    		RendicontazioniRepository rndRepository,
    		PagoPAProperties pagoPAProperties,
    		@Value("#{jobExecutionContext['lastProcessedId'] ?: 0}") long lastProcessedId) {
        this.rndRepository = rndRepository;
        this.pagoPAProperties = pagoPAProperties;
        this.lastProcessedId = lastProcessedId;
    }

    @BeforeStep
    public void initToBeRetrieve() {
		toBeRetrieveList = new ArrayList<>();
		LocalDateTime dataLimite = LocalDateTime.now().minusDays(pagoPAProperties.getFinestraTemporale());
		List<Object[]> rndInfos = lastProcessedId > 0L ? rndRepository.findRendicontazioneWithNoPagamentoAfterId(lastProcessedId, dataLimite )
		                                               : rndRepository.findRendicontazioneWithNoPagamento(dataLimite);
		log.info("Trovate {} ricevute da recuperare", rndInfos.size());
		for (Object[] rndInfo : rndInfos) {
			log.debug("Ricevuta da recuperare id {}, taxCode {}, iur {}, iuv {}", rndInfo[0], rndInfo[1], rndInfo[2], rndInfo[3]);
			RtRetrieveContext rtRetrieveCtx = RtRetrieveContext.builder()
			                                                   .rtId(convertToLong(rndInfo[0]))
			                                                   .taxCode((String)rndInfo[1])
			                                                   .iur((String)rndInfo[2])
			                                                   .iuv((String)rndInfo[3])
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
