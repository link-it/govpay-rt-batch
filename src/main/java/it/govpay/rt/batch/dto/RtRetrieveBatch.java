package it.govpay.rt.batch.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Batch of receipt retrieve from pagoPA API
 */
@Data
@Builder
public class RtRetrieveBatch {
	private Long rtId;
    private String codDominio;
    private String iuv;
    private String iur;
    private LocalDateTime retrivedTime;
    private String message;
    /**
     * true per un esito transitorio (es. 404 "Receipt not found"): il writer
     * non disabilita {@code esegui_recupero_rt}, la riga resta candidata al
     * prossimo giro schedulato. Default false: ogni altro esito e' terminale.
     */
    @Builder.Default
    private boolean retryable = false;
}
