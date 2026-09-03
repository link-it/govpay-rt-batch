package it.govpay.rt.batch.tasklet;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.rt.batch.dto.RtRetrieveBatch;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.service.PaForNodeService;
import it.govpay.rt.batch.service.RtApiService;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

/**
 * Processor to fetch receipt from pagoPA API and sent to govpay
 */
@Component
@Slf4j
public class RtRetrieveProcessor implements ItemProcessor<RtRetrieveContext, RtRetrieveBatch> {

    private final RtApiService rtApiService;
    private final PaForNodeService govpayService;
    private final Clock clock;

    public RtRetrieveProcessor(RtApiService rtApiService, PaForNodeService govpayService, Clock clock) {
        this.rtApiService = rtApiService;
        this.govpayService = govpayService;
        this.clock = clock;
    }

    @Override
    public RtRetrieveBatch process(RtRetrieveContext context) throws Exception {
        log.info("Processing rendicontazione {}: {} - {} - {}",
                 context.getRtId(), context.getTaxCode(), context.getIur(), context.getIuv());

        CompletableFuture<HttpStatusCode> statusCodeFuture = new CompletableFuture<>();
        PaSendRTV2Request rtV2request = rtApiService.retrieveReceipt(context, statusCodeFuture);
        if (rtV2request == null) {
        	if (statusCodeFuture.isDone() && statusCodeFuture.get().equals(HttpStatus.NOT_FOUND)) {
                return RtRetrieveBatch.builder()
                                      .rtId(context.getRtId())
                                      .codDominio(context.getTaxCode())
                                      .iur(context.getIur())
                                      .iuv(context.getIuv())
                                      .message("Receipt not found")
                                      .build();
        	}
        	if (statusCodeFuture.isDone() && statusCodeFuture.get().equals(HttpStatus.UNPROCESSABLE_ENTITY)) {
        		// Marca da bollo con allegato mancante (bug pagoPA noto, govpay#843): in pratica
        		// non dovrebbe mai capitare qui, dato che i pagamenti di sole MBT non generano
        		// mai un FdR e quindi non raggiungono mai la scansione automatica.
        		return RtRetrieveBatch.builder()
                                      .rtId(context.getRtId())
                                      .codDominio(context.getTaxCode())
                                      .iur(context.getIur())
                                      .iuv(context.getIuv())
                                      .message("MBD attachment mancante")
                                      .build();
        	}
        	// Non dovrebbe mai arrivare qui in quanto gli altri casi dovrebbero essere antati in eccezione
            return null;
        }
        if (govpayService.sendReceipt(context, rtV2request))
            return RtRetrieveBatch.builder()
                                  .rtId(context.getRtId())
                                  .codDominio(context.getTaxCode())
                                  .iur(context.getIur())
                                  .iuv(context.getIuv())
                                  .retrivedTime(LocalDateTime.now(clock))
                                  .build();
        return RtRetrieveBatch.builder()
                              .rtId(context.getRtId())
                              .codDominio(context.getTaxCode())
                              .iur(context.getIur())
                              .iuv(context.getIuv())
                              .message("Send to govpay failed")
                              .build();
    }
}
