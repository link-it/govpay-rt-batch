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
 * Mirror read-only di {@code operatori}: risolve {@code rt_recuperi.id_operatore}
 * al principal dell'operatore che ha richiesto il recupero puntuale, per
 * l'header {@code GOVPAY-ON-BEHALF-OF} verso api-pagopa e per il giornale
 * eventi. Schema di proprieta' di console-api; questo progetto la legge
 * soltanto (a differenza del gemello in console-api, nessun campo scrivibile
 * separato: qui basta la relazione).
 */
@Entity
@Table(name = "operatori")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Operatore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utenza", nullable = false)
    private Utenza utenza;
}
