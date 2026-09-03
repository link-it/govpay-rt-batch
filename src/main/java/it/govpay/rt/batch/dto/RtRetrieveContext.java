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
     * operatori.id di chi ha richiesto il recupero puntuale.
     * Null per le righe della scansione automatica su
     * rendicontazioni, che non hanno un operatore associato. Non ancora emesso
     * negli eventi GDE: {@code NuovoEvento} (govpay-common) non espone un campo
     * dedicato.
     */
    private Long idOperatore;
}
