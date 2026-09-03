package it.govpay.rt.batch.tasklet;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.dto.RtRecuperoBatch;
import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.gde.service.GdeService;
import it.govpay.rt.batch.service.PaForNodeService;
import it.govpay.rt.batch.service.RtApiService;
import lombok.extern.slf4j.Slf4j;

/**
 * Processor per il recupero puntuale: riusa
 * {@link RtApiService#retrieveReceipt} + {@link PaForNodeService#sendReceipt}
 * esattamente come {@link RtRetrieveProcessor}, ma:
 * <ul>
 *   <li>il retry sul 429 e' interno a questo processor (via {@link RetryTemplate}),
 *       non affidato al {@code faultTolerant()} dello step: cosi' un fallimento
 *       definitivo su una riga produce un output "marcato" normale invece di far
 *       fallire l'intero step (che altrimenti smetterebbe di leggere le righe
 *       successive — contrario a "ogni riga elaborata in modo indipendente",);</li>
 *   <li>cattura qui, e solo qui, {@link IllegalStateException} da
 *       {@code RtApiService} (dominio/stazione/intermediario/connettore non
 *       configurati): {@code retrieveReceipt} la lascia propagare invariata per
 *       lo step schedulato ({@link RtRetrieveProcessor}), che deve restare
 *       intatto — vedi il commento su {@code RtApiService.retrieveReceipt};</li>
 *   <li>distingue **due** esiti da {@code statusCodeFuture} (404, marca da
 *       bollo senza allegato) invece del solo NOT_FOUND di {@link RtRetrieveProcessor}.</li>
 * </ul>
 */
@Component
@Slf4j
public class RtRecuperoProcessor implements ItemProcessor<RtRetrieveContext, RtRecuperoBatch> {

    private final RtApiService rtApiService;
    private final PaForNodeService govpayService;
    private final GdeService gdeService;
    private final RetryTemplate retryTemplate;

    public RtRecuperoProcessor(RtApiService rtApiService, PaForNodeService govpayService, GdeService gdeService,
            @Qualifier("rtRecuperoRetryTemplate") RetryTemplate retryTemplate) {
        this.rtApiService = rtApiService;
        this.govpayService = govpayService;
        this.gdeService = gdeService;
        this.retryTemplate = retryTemplate;
    }

    @Override
    public RtRecuperoBatch process(RtRetrieveContext context) throws Exception {
        log.info("Elaborazione recupero puntuale id {}: {} - {} - {}",
                context.getRtId(), context.getTaxCode(), context.getIur(), context.getIuv());

        CompletableFuture<HttpStatusCode> statusCodeFuture = new CompletableFuture<>();
        OffsetDateTime dataStart = OffsetDateTime.now(ZoneOffset.UTC);
        PaSendRTV2Request rtV2request;
        try {
            rtV2request = retryTemplate.execute(ctx -> rtApiService.retrieveReceipt(context, statusCodeFuture));
        } catch (IllegalStateException e) {
            // Dominio/stazione/intermediario/connettore non censiti o non configurati: non e'
            // un errore di trasporto (nessuna chiamata HTTP e' stata tentata da retrieveReceipt),
            // quindi non e' stato ritentato (escluso esplicitamente dalla retry policy). Nessun
            // evento GDE scritto da RtApiService per questo caso (si e' fermata prima), quindi lo
            // scrive questo processor, l'unico chiamante che deve gestirlo.
            OffsetDateTime dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
            log.warn("Configurazione mancante per id {}: {}", context.getRtId(), e.getMessage());
            gdeService.saveGetReceiptConfigurazioneMancante(context, dataStart, dataEnd, e.getMessage());
            return marcato(context, Costanti.ESITO_RECUPERO_ERRORE, "Configurazione mancante: " + e.getMessage());
        } catch (RestClientException e) {
            log.warn("Recupero fallito dopo i tentativi configurati per id {}: {}", context.getRtId(), e.getMessage());
            return marcato(context, Costanti.ESITO_RECUPERO_ERRORE, "Recupero fallito: " + e.getMessage());
        }

        if (rtV2request == null) {
            HttpStatusCode status = statusCodeFuture.isDone() ? statusCodeFuture.get() : null;
            if (HttpStatus.NOT_FOUND.equals(status)) {
                return marcato(context, Costanti.ESITO_RECUPERO_NON_DISPONIBILE, "Ricevuta non disponibile su pagoPA");
            }
            if (HttpStatus.UNPROCESSABLE_ENTITY.equals(status)) {
                return marcato(context, Costanti.ESITO_RECUPERO_MBT_ALLEGATO_MANCANTE, "Marca da bollo con allegato mancante");
            }
            // Non dovrebbe mai arrivare qui: gli altri casi vanno in eccezione (catturata sopra).
            return marcato(context, Costanti.ESITO_RECUPERO_ERRORE, "Esito imprevisto dal recupero");
        }

        if (govpayService.sendReceipt(context, rtV2request)) {
            // PaForNodeService.sendReceipt ritorna true anche su PAA_RECEIPT_DUPLICATA
            // (e' l'esito normale di una richiesta ripetuta, non un errore,
            // evento GDE dedicato scritto li'): la riga va eliminata come per
            // un'acquisizione riuscita, la ricevuta e' comunque presente lato govpay.
            log.debug("Ricevuta recuperata: taxCode {} - iur {} - iuv {}", context.getTaxCode(), context.getIur(), context.getIuv());
            return successo(context);
        }
        return marcato(context, Costanti.ESITO_RECUPERO_ERRORE, "Invio a govpay fallito");
    }

    private static RtRecuperoBatch successo(RtRetrieveContext context) {
        return RtRecuperoBatch.builder()
                .id(context.getRtId())
                .codDominio(context.getTaxCode())
                .iuv(context.getIuv())
                .iur(context.getIur())
                .build();
    }

    private static RtRecuperoBatch marcato(RtRetrieveContext context, String esito, String message) {
        return RtRecuperoBatch.builder()
                .id(context.getRtId())
                .codDominio(context.getTaxCode())
                .iuv(context.getIuv())
                .iur(context.getIur())
                .esito(esito)
                .message(message)
                .build();
    }
}
