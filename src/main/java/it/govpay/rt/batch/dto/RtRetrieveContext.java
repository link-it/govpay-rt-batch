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
}
