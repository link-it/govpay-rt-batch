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
}
