package it.govpay.rt.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a single payment in a FDR
 */
@Entity
@Table(name = "RENDICONTAZIONI")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rendicontazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fr", nullable = false)
    private Fr fr;

    @Column(name = "id_pagamento")
    private Long idPagamento;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_singolo_versamento")
    private SingoloVersamento singoloVersamento;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "iur", nullable = false, length = 35)
    private String iur;

    @Column(name = "data")
    private LocalDateTime data;

    @Column(name = "esegui_recupero_rt", nullable = false, columnDefinition = "boolean default true")
    private Boolean eseguiRecuperoRt;
}
