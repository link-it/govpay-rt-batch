package it.govpay.rt.batch.entity;

import it.govpay.common.entity.ApplicazioneEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirror read-only di {@code versamenti}, la pendenza: serve a valorizzare
 * {@code idPendenza} ({@code cod_versamento_ente}) e {@code idA2A}
 * ({@code applicazioni.cod_applicazione}) negli eventi del giornale.
 * I due campi vanno sempre insieme: {@code unique_versamenti_1} e' su
 * {@code (cod_versamento_ente, id_applicazione)}, quindi il solo
 * {@code cod_versamento_ente} non identifica una pendenza.
 *
 * <p>Schema di proprieta' di govpay; questo progetto lo legge soltanto.
 */
@Entity
@Table(name = "versamenti")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Versamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_versamento_ente", nullable = false, length = 35)
    private String codVersamentoEnte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_applicazione", nullable = false)
    private ApplicazioneEntity applicazione;
}
