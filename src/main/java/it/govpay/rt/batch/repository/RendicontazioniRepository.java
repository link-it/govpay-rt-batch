package it.govpay.rt.batch.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.govpay.rt.batch.entity.Rendicontazione;

@Repository
public interface RendicontazioniRepository extends JpaRepository<Rendicontazione, Long> {
	@Query("SELECT r.id, d.codDominio, r.iuv, r.iur " +
	              "FROM Rendicontazione r " +
	                   "JOIN r.singoloVersamento sv " +
	                   "JOIN r.fr f " +
	                   "JOIN f.dominio d " +
	              "WHERE r.singoloVersamento IS NOT NULL AND " +
	                    "r.idPagamento IS NULL AND " +
	                    "r.id > :ultimoIdElaborato AND " +
	                    "r.data > :dataLimite " +
	              "ORDER BY r.id ASC")
    List<Object[]> findRendicontazioneWithNoPagamentoAfterId(
    		@Param("ultimoIdElaborato") Long ultimoIdElaborato,
    		@Param("dataLimite") LocalDateTime dataLimite);

    @Query("SELECT r.id, d.codDominio, r.iuv, r.iur " +
            "FROM Rendicontazione r " +
                 "JOIN r.singoloVersamento sv " +
                 "JOIN r.fr f " +
                 "JOIN f.dominio d " +
            "WHERE r.singoloVersamento IS NOT NULL AND " +
                  "r.idPagamento IS NULL AND " +
                  "r.data > :dataLimite " +
            "ORDER BY r.id ASC")
    List<Object[]> findRendicontazioneWithNoPagamento(@Param("dataLimite") LocalDateTime dataLimite);
}
