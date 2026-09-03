package it.govpay.rt.batch.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Esito dell'elaborazione di una riga {@code rt_recuperi}.
 * Distinto da {@link RtRetrieveBatch}: la semantica del writer e'
 * diversa (elimina/marca la riga, non {@code disableRecuperoRt}/watermark).
 *
 * <p>{@code esito == null} significa successo: il writer elimina la riga.
 * Altrimenti {@code esito} e' il valore da scrivere in {@code rt_recuperi.esito}
 * (vedi {@link it.govpay.rt.batch.Costanti}).
 */
@Data
@Builder
public class RtRecuperoBatch {
    private Long id;
    private String codDominio;
    private String iuv;
    private String iur;
    private String esito;
    private String message;
}
