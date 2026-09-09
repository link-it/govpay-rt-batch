package it.govpay.rt.batch.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.govpay.rt.batch.entity.Operatore;

@Repository
public interface OperatoreRepository extends JpaRepository<Operatore, Long> {

    @Query("SELECT o FROM Operatore o JOIN FETCH o.utenza WHERE o.id = :id")
    Optional<Operatore> findByIdConUtenza(@Param("id") Long id);
}
