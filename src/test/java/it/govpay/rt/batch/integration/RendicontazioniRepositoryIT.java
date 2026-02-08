package it.govpay.rt.batch.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import it.govpay.rt.batch.entity.Dominio;
import it.govpay.rt.batch.entity.Fr;
import it.govpay.rt.batch.entity.Rendicontazione;
import it.govpay.rt.batch.entity.SingoloVersamento;
import it.govpay.rt.batch.repository.RendicontazioniRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@ActiveProfiles("integration")
@DisplayName("RendicontazioniRepository Integration Test")
class RendicontazioniRepositoryIT {

    @Autowired
    private RendicontazioniRepository rendicontazioniRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456789";
    private static final LocalDateTime DATA_LIMITE = LocalDateTime.now().minusDays(90);

    @BeforeEach
    void setUp() {
        // Clean up before each test
        rendicontazioniRepository.deleteAll();
    }

    @Test
    @DisplayName("should find rendicontazione without pagamento")
    void shouldFindRendicontazioneWithoutPagamento() {
        // Given: a rendicontazione with singoloVersamento but no idPagamento
        createTestData(IUV, IUR, null);
        entityManager.flush();
        entityManager.clear();

        // When: query for rendicontazioni without pagamento
        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamento(DATA_LIMITE);

        // Then: the record is found
        assertEquals(1, results.size());
        Object[] row = results.get(0);
        assertEquals(TAX_CODE, row[1]); // codDominio
        assertEquals(IUV, row[2]);      // iuv
        assertEquals(IUR, row[3]);      // iur
    }

    @Test
    @DisplayName("should not find rendicontazione with pagamento")
    void shouldNotFindRendicontazioneWithPagamento() {
        // Given: a rendicontazione WITH idPagamento set
        createTestData(IUV, IUR, 12345L);
        entityManager.flush();
        entityManager.clear();

        // When: query for rendicontazioni without pagamento
        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamento(DATA_LIMITE);

        // Then: no records found
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("should not find rendicontazione without singoloVersamento")
    void shouldNotFindRendicontazioneWithoutSingoloVersamento() {
        // Given: a rendicontazione WITHOUT singoloVersamento
        Dominio dominio = Dominio.builder().codDominio(TAX_CODE).build();
        entityManager.persist(dominio);

        Fr fr = Fr.builder().dominio(dominio).build();
        entityManager.persist(fr);

        Rendicontazione rnd = Rendicontazione.builder()
                .fr(fr)
                .singoloVersamento(null)  // No singoloVersamento
                .iuv(IUV)
                .iur(IUR)
                .data(LocalDateTime.now())
                .idPagamento(null)
                .build();
        entityManager.persist(rnd);
        entityManager.flush();
        entityManager.clear();

        // When: query for rendicontazioni without pagamento
        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamento(DATA_LIMITE);

        // Then: no records found (singoloVersamento is required)
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("should find rendicontazione after id")
    void shouldFindRendicontazioneAfterId() {
        // Given: multiple rendicontazioni
        Rendicontazione rnd1 = createTestData(IUV + "_1", IUR + "_1", null);
        entityManager.flush();
        Long firstId = rnd1.getId();

        createTestData(TAX_CODE + "_2", IUV + "_2", IUR + "_2", null);
        entityManager.flush();
        entityManager.clear();

        // When: query for rendicontazioni after first id
        List<Object[]> results = rendicontazioniRepository
                .findRendicontazioneWithNoPagamentoAfterId(firstId, DATA_LIMITE);

        // Then: only the second record is found
        assertEquals(1, results.size());
        assertEquals(IUV + "_2", results.get(0)[2]);
    }

    @Test
    @DisplayName("should not find rendicontazione older than data limite")
    void shouldNotFindRendicontazioneOlderThanDataLimite() {
        // Given: a rendicontazione older than data limite
        Dominio dominio = Dominio.builder().codDominio(TAX_CODE).build();
        entityManager.persist(dominio);

        Fr fr = Fr.builder().dominio(dominio).build();
        entityManager.persist(fr);

        SingoloVersamento sv = SingoloVersamento.builder().build();
        entityManager.persist(sv);

        Rendicontazione rnd = Rendicontazione.builder()
                .fr(fr)
                .singoloVersamento(sv)
                .iuv(IUV)
                .iur(IUR)
                .data(LocalDateTime.now().minusDays(100))  // Older than 90 days
                .idPagamento(null)
                .build();
        entityManager.persist(rnd);
        entityManager.flush();
        entityManager.clear();

        // When: query with data limite of 90 days ago
        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamento(DATA_LIMITE);

        // Then: no records found (too old)
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("should return results ordered by id")
    void shouldReturnResultsOrderedById() {
        // Given: multiple rendicontazioni created in reverse order
        createTestData(TAX_CODE + "_3", IUV + "_3", IUR + "_3", null);
        createTestData(TAX_CODE + "_1", IUV + "_1", IUR + "_1", null);
        createTestData(TAX_CODE + "_2", IUV + "_2", IUR + "_2", null);
        entityManager.flush();
        entityManager.clear();

        // When: query for all rendicontazioni
        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamento(DATA_LIMITE);

        // Then: results are ordered by id (ascending)
        assertEquals(3, results.size());
        Long prevId = 0L;
        for (Object[] row : results) {
            Long currentId = (Long) row[0];
            assertTrue(currentId > prevId, "Results should be ordered by id ascending");
            prevId = currentId;
        }
    }

    private Rendicontazione createTestData(String iuv, String iur, Long idPagamento) {
        return createTestData(TAX_CODE, iuv, iur, idPagamento);
    }

    private Rendicontazione createTestData(String taxCode, String iuv, String iur, Long idPagamento) {
        Dominio dominio = Dominio.builder().codDominio(taxCode).build();
        entityManager.persist(dominio);

        Fr fr = Fr.builder().dominio(dominio).build();
        entityManager.persist(fr);

        SingoloVersamento sv = SingoloVersamento.builder().build();
        entityManager.persist(sv);

        Rendicontazione rnd = Rendicontazione.builder()
                .fr(fr)
                .singoloVersamento(sv)
                .iuv(iuv)
                .iur(iur)
                .data(LocalDateTime.now())
                .idPagamento(idPagamento)
                .build();
        entityManager.persist(rnd);

        return rnd;
    }
}
