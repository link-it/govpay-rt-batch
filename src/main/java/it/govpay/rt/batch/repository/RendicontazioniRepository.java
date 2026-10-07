package it.govpay.rt.batch.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.govpay.rt.batch.entity.Rendicontazione;

@Repository
public interface RendicontazioniRepository extends JpaRepository<Rendicontazione, Long> {

    /**
     * Candidate alla scansione schedulata. Le colonne 5 e 6
     * ({@code cod_versamento_ente}, {@code cod_applicazione}) alimentano
     * {@code idPendenza}/{@code idA2A} degli eventi GDE.
     *
     * <p>I due join aggiunti sono LEFT di proposito, benche' entrambe le FK
     * siano NOT NULL a schema: l'insieme delle righe candidate deve restare
     * esattamente quello di prima, perche' una riga persa qui e' una RT che
     * non verrebbe piu' recuperata. Una pendenza non risolvibile costa un
     * {@code idPendenza} nullo nell'evento, non un recupero mancato.
     */
    @Query("SELECT r.id, d.codDominio, r.iuv, r.iur, v.codVersamentoEnte, a.codApplicazione " +
            "FROM Rendicontazione r " +
                 "JOIN r.singoloVersamento sv " +
                 "LEFT JOIN sv.versamento v " +
                 "LEFT JOIN v.applicazione a " +
                 "JOIN r.fr f " +
                 "JOIN f.dominio d " +
            "WHERE r.singoloVersamento IS NOT NULL AND " +
                  "r.idPagamento IS NULL AND " +
                  "r.eseguiRecuperoRt = true AND " +
                  "r.data > :dataLimite " +
            "ORDER BY r.id ASC")
    List<Object[]> findRendicontazioneWithNoPagamento(@Param("dataLimite") LocalDateTime dataLimite);

    /**
     * Conta le righe candidate ({@code esegui_recupero_rt = true}, con
     * {@code singoloVersamento} e senza {@code idPagamento}) escluse dalla
     * SOLA finestra temporale (stessi criteri di {@link #findRendicontazioneWithNoPagamento},
     * data invertita). Non le rimette in scansione: serve solo a rendere
     * osservabile il fenomeno (govpay-rt-batch#21 punto 2) senza introdurre
     * uno stato terminale in colonna, per cui servirebbe una patch DDL a monte.
     */
    @Query("SELECT COUNT(r) " +
            "FROM Rendicontazione r " +
                 "JOIN r.singoloVersamento sv " +
            "WHERE r.singoloVersamento IS NOT NULL AND " +
                  "r.idPagamento IS NULL AND " +
                  "r.eseguiRecuperoRt = true AND " +
                  "r.data <= :dataLimite")
    long countRendicontazioneEsclusaDallaFinestra(@Param("dataLimite") LocalDateTime dataLimite);

    @Modifying
    @Query("UPDATE Rendicontazione r SET r.eseguiRecuperoRt = false WHERE r.id = :id")
    void disableRecuperoRt(@Param("id") Long id);
}
