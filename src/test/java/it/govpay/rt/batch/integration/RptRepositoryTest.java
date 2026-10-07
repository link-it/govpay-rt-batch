package it.govpay.rt.batch.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.rt.batch.entity.Rpt;
import it.govpay.rt.batch.entity.Versamento;
import it.govpay.rt.batch.repository.RptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@ActiveProfiles("integration")
@DisplayName("RptRepository Integration Test")
class RptRepositoryTest {

    @Autowired
    private RptRepository rptRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String COD_DOMINIO = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";
    private static final String COD_APPLICAZIONE = "APP-TEST";

    /** Una sola applicazione per test: cod_applicazione e id_utenza sono unique. */
    private ApplicazioneEntity applicazione;

    @Test
    @DisplayName("should risolvere la tripla a cod_versamento_ente e cod_applicazione")
    void shouldResolvePendenza() {
        creaRpt(COD_DOMINIO, IUV, IUR, "PENDENZA-1");
        entityManager.flush();
        entityManager.clear();

        List<Object[]> righe = rptRepository.findRiferimentoPendenza(COD_DOMINIO, IUV, IUR);

        assertEquals(1, righe.size());
        assertEquals("PENDENZA-1", righe.get(0)[0]);
        assertEquals(COD_APPLICAZIONE, righe.get(0)[1]);
    }

    @Test
    @DisplayName("should distinguere le RPT che condividono la coppia (dominio, iuv) ma non il ccp")
    void shouldDiscriminateByCcp() {
        creaRpt(COD_DOMINIO, IUV, IUR, "PENDENZA-CERCATA");
        creaRpt(COD_DOMINIO, IUV, "IUR-ALTRO", "PENDENZA-ALTRA");
        entityManager.flush();
        entityManager.clear();

        List<Object[]> righe = rptRepository.findRiferimentoPendenza(COD_DOMINIO, IUV, IUR);

        assertEquals(1, righe.size(), "la tripla e' la chiave di unique_rpt_id_transazione");
        assertEquals("PENDENZA-CERCATA", righe.get(0)[0]);
    }

    @Test
    @DisplayName("should ritornare vuoto quando nessuna RPT corrisponde alla tripla")
    void shouldReturnEmptyWhenNoMatch() {
        creaRpt(COD_DOMINIO, IUV, IUR, "PENDENZA-1");
        entityManager.flush();
        entityManager.clear();

        assertTrue(rptRepository.findRiferimentoPendenza(COD_DOMINIO, IUV, "IUR-INESISTENTE").isEmpty());
        assertTrue(rptRepository.findRiferimentoPendenza(COD_DOMINIO, "IUV-INESISTENTE", IUR).isEmpty());
        assertTrue(rptRepository.findRiferimentoPendenza("99999999999", IUV, IUR).isEmpty());
    }

    private void creaRpt(String codDominio, String iuv, String ccp, String codVersamentoEnte) {
        Versamento versamento = Versamento.builder()
                .codVersamentoEnte(codVersamentoEnte)
                .applicazione(creaApplicazione())
                .build();
        entityManager.persist(versamento);

        Rpt rpt = Rpt.builder()
                .codDominio(codDominio)
                .iuv(iuv)
                .ccp(ccp)
                .versamento(versamento)
                .build();
        entityManager.persist(rpt);
    }

    private ApplicazioneEntity creaApplicazione() {
        if (applicazione == null) {
            applicazione = ApplicazioneEntity.builder()
                    .codApplicazione(COD_APPLICAZIONE)
                    .autoIuv(false)
                    .firmaRicevuta("0")
                    .trusted(false)
                    .idUtenza(1L)
                    .build();
            entityManager.persist(applicazione);
        }
        return applicazione;
    }
}
