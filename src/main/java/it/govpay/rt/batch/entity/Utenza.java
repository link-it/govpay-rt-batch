package it.govpay.rt.batch.entity;

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
 * Mirror read-only di {@code utenze}: solo il principal, che serve a
 * risolvere l'operatore che ha richiesto un recupero puntuale. Schema di
 * proprieta' di console-api, che crea e scrive la tabella; questo progetto
 * la legge soltanto.
 */
@Entity
@Table(name = "utenze")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Utenza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "principal", nullable = false, length = 4000)
    private String principal;
}
