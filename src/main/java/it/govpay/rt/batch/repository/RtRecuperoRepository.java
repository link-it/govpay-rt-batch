package it.govpay.rt.batch.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.govpay.rt.batch.entity.RtRecupero;

@Repository
public interface RtRecuperoRepository extends JpaRepository<RtRecupero, Long> {

    /**
     * Righe da elaborare: {@code esito IS NULL}. Nessuna finestra temporale
     * (a differenza di {@link it.govpay.rt.batch.repository.RendicontazioniRepository}):
     * una richiesta esplicita dell'operatore vale a prescindere dall'eta' del pagamento.
     */
    List<RtRecupero> findByEsitoIsNullOrderByIdAsc();

    @Modifying
    @Query("UPDATE RtRecupero r SET r.esito = :esito, r.dataUltimoTentativo = :dataUltimoTentativo WHERE r.id = :id")
    void marca(@Param("id") Long id, @Param("esito") String esito,
               @Param("dataUltimoTentativo") OffsetDateTime dataUltimoTentativo);

    /**
     * Righe marcate piu' vecchie della soglia di retention: {@code esito}
     * valorizzato e {@code data_ultimo_tentativo} precedente al limite.
     */
    @Modifying
    @Query("DELETE FROM RtRecupero r WHERE r.esito IS NOT NULL AND r.dataUltimoTentativo < :soglia")
    int eliminaMarcatePrimaDi(@Param("soglia") OffsetDateTime soglia);
}
