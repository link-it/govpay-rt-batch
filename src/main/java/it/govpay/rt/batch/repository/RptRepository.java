package it.govpay.rt.batch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.govpay.rt.batch.entity.Rpt;

@Repository
public interface RptRepository extends JpaRepository<Rpt, Long> {

    /**
     * Riferimento della pendenza associata alla tripla di un recupero puntuale:
     * {@code [cod_versamento_ente, cod_applicazione]}.
     *
     * <p>Il filtro e' sulla tripla completa, con {@code rt_recuperi.iur} come
     * {@code ccp} della RPT: e' esattamente la chiave di
     * {@code unique_rpt_id_transazione (iuv, ccp, cod_dominio)}, quindi la
     * ricerca colpisce l'indice e restituisce al piu' una riga, senza
     * l'ambiguita' delle RPT multiple che si ha fermandosi a (dominio, iuv) --
     * come fa il pre-flight di console-api, che il {@code ccp} non ce l'ha.
     *
     * <p>Resta una {@code List} e non un {@code Optional} di proposito: in caso
     * di piu' righe un {@code Optional} solleverebbe
     * {@code IncorrectResultSizeDataAccessException}, facendo fallire
     * l'elaborazione della riga per un dato accessorio dell'evento.
     */
    @Query("SELECT v.codVersamentoEnte, a.codApplicazione " +
            "FROM Rpt rpt " +
                 "JOIN rpt.versamento v " +
                 "JOIN v.applicazione a " +
            "WHERE rpt.codDominio = :codDominio AND rpt.iuv = :iuv AND rpt.ccp = :iur")
    List<Object[]> findRiferimentoPendenza(@Param("codDominio") String codDominio,
                                           @Param("iuv") String iuv,
                                           @Param("iur") String iur);
}
