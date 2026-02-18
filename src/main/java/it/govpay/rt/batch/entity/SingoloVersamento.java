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

}
