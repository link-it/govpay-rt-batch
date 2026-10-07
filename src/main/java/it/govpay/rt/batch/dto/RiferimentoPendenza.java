package it.govpay.rt.batch.dto;

/**
 * Coordinate della pendenza emesse nel giornale eventi: {@code idPendenza}
 * ({@code versamenti.cod_versamento_ente}) e {@code idA2A}
 * ({@code applicazioni.cod_applicazione}). Stanno insieme perche' il primo
 * e' unico solo a parita' di applicazione.
 */
public record RiferimentoPendenza(String idPendenza, String idA2A) {
}
