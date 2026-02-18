package it.govpay.rt.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a FDR (Flusso di Rendicontazione)
 */
@Entity
@Table(name = "FR")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_dominio", nullable = false)
    private Dominio dominio;
}
