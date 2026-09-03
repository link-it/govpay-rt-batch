package it.govpay.rt.batch.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirror di {@code rt_recuperi}, tabella di appoggio per il recupero puntuale
 * di una RT su richiesta dell'operatore. Lo schema e' di proprieta' di {@code console-api}, che la crea
 * (DDL sui 5 dialetti) e la scrive; questo progetto la legge ed elimina/marca.
 * Nessun vincolo unique sulla tripla (cod_dominio, iuv, iur): richieste
 * ripetute su una tripla ancora pendente restano indipendenti, righe
 * multiple sono legittime.
 */
@Entity
@Table(name = "rt_recuperi")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RtRecupero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "iur", nullable = false, length = 35)
    private String iur;

    @Column(name = "data_richiesta", nullable = false)
    private OffsetDateTime dataRichiesta;

    @Column(name = "id_operatore")
    private Long idOperatore;

    @Column(name = "esito", length = 35)
    private String esito;

    @Column(name = "data_ultimo_tentativo")
    private OffsetDateTime dataUltimoTentativo;
}
