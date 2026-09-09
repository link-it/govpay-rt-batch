package it.govpay.rt.batch.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import it.govpay.rt.batch.entity.Operatore;
import it.govpay.rt.batch.entity.Utenza;
import it.govpay.rt.batch.repository.OperatoreRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@ActiveProfiles("integration")
@DisplayName("OperatoreRepository Integration Test")
class OperatoreRepositoryTest {

    @Autowired
    private OperatoreRepository operatoreRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("should resolve principal via the utenza join")
    void shouldResolvePrincipalViaUtenzaJoin() {
        Utenza utenza = Utenza.builder().principal("u-operatore").build();
        entityManager.persist(utenza);
        Operatore operatore = Operatore.builder().utenza(utenza).build();
        entityManager.persist(operatore);
        entityManager.flush();
        entityManager.clear();

        Optional<Operatore> risultato = operatoreRepository.findByIdConUtenza(operatore.getId());

        assertTrue(risultato.isPresent());
        assertEquals("u-operatore", risultato.get().getUtenza().getPrincipal());
    }

    @Test
    @DisplayName("should return empty when the id does not exist")
    void shouldReturnEmptyWhenIdDoesNotExist() {
        Optional<Operatore> risultato = operatoreRepository.findByIdConUtenza(-1L);

        assertTrue(risultato.isEmpty());
    }
}
