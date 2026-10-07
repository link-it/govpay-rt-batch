package it.govpay.rt.batch.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Context information for processing receipt retrieve
 */
@Data
@Builder
public class RtRetrieveContext {
	private Long rtId;
    private String taxCode;
    private String iuv;
    private String iur;
    private String idIntermediario;
    private String idStazione;
    /**
     * {@code versamenti.cod_versamento_ente} della pendenza a cui la ricevuta
     * si riferisce, emesso come {@code idPendenza} negli eventi GDE. Va letto
     * dal reader, prima della chiamata a pagoPA: altrimenti gli eventi di
     * errore (gli unici scritti quando il recupero non va a buon fine) non
     * potrebbero valorizzarlo. Null se la pendenza non e' risolvibile.
     */
    private String idPendenza;
    /**
     * {@code applicazioni.cod_applicazione} dell'applicazione proprietaria
     * della pendenza, emesso come {@code idA2A} negli eventi GDE. Senza di
     * esso {@link #idPendenza} e' ambiguo: e' unico solo per applicazione.
     */
    private String idA2A;
    /**
     * operatori.id di chi ha richiesto il recupero puntuale.
     * Null per le righe della scansione automatica su
     * rendicontazioni, che non hanno un operatore associato. Non ancora emesso
     * negli eventi GDE: {@code NuovoEvento} (govpay-common) non espone un campo
     * dedicato.
     */
    private Long idOperatore;
}
