package it.govpay.rt.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a domain (creditor institution)
 */
@Entity
@Table(name = "DOMINI")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dominio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_dominio", nullable = false, unique = true, length = 35)
    private String codDominio;


}
