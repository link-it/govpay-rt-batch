package it.govpay.rt.batch.entity;

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
 * Mirror read-only di {@code rpt}, con i soli campi che servono a risalire
 * dalla tripla di un recupero puntuale alla pendenza: nel recupero puntuale
 * la riga di {@code rt_recuperi} porta solo {@code (cod_dominio, iuv, iur)},
 * senza alcun riferimento al versamento (a differenza della scansione
 * schedulata, che parte da {@code rendicontazioni} e ha gia' il
 * {@code singolo_versamento}).
 *
 * <p>{@code rt_recuperi.iur} e' il {@code ccp} della RPT, quindi la tripla
 * identifica esattamente una riga: e' la chiave di
 * {@code unique_rpt_id_transazione (iuv, ccp, cod_dominio)}.
 *
 * <p>Schema di proprieta' di govpay; questo progetto lo legge soltanto.
 */
@Entity
@Table(name = "rpt")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rpt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "ccp", nullable = false, length = 35)
    private String ccp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_versamento", nullable = false)
    private Versamento versamento;
}
