package it.govpay.rt.batch.service;

import org.springframework.stereotype.Service;

import it.govpay.rt.batch.repository.OperatoreRepository;

/**
 * Risolve {@code rt_recuperi.id_operatore} al principal
 * ({@code utenze.principal}) dell'operatore che ha richiesto il recupero
 * puntuale. Null per le righe della scansione automatica su rendicontazioni,
 * che non hanno un operatore associato, e per un id orfano (riga
 * cancellata nel frattempo).
 */
@Service
public class OperatorePrincipalResolver {

    private final OperatoreRepository operatoreRepository;

    public OperatorePrincipalResolver(OperatoreRepository operatoreRepository) {
        this.operatoreRepository = operatoreRepository;
    }

    public String resolve(Long idOperatore) {
        if (idOperatore == null) {
            return null;
        }
        return operatoreRepository.findByIdConUtenza(idOperatore)
                .map(o -> o.getUtenza().getPrincipal())
                .orElse(null);
    }
}
