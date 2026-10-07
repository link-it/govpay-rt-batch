package it.govpay.rt.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a single payment position item
 */
@Entity
@Table(name = "SINGOLI_VERSAMENTI")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SingoloVersamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Pendenza di cui questa voce fa parte: e' la rotta con cui la scansione
     * schedulata risale a {@code idPendenza}/{@code idA2A} per il giornale
     * eventi, senza una query aggiuntiva (il join e' gia' nella query del
     * reader). In {@code singoli_versamenti} la colonna e' NOT NULL.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_versamento")
    private Versamento versamento;
}
