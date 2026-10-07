package it.govpay.rt.batch.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import it.govpay.rt.batch.dto.RiferimentoPendenza;
import it.govpay.rt.batch.repository.RptRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Risolve la tripla (cod_dominio, iuv, iur) di un recupero puntuale alle
 * coordinate della pendenza ({@code idPendenza}, {@code idA2A}) da riportare
 * nel giornale eventi, passando per {@code rpt} ({@code iur} e' il {@code ccp}
 * della RPT).
 *
 * <p>Serve solo al recupero puntuale: la scansione schedulata parte da
 * {@code rendicontazioni} e ottiene gli stessi due valori dal join gia'
 * presente nella sua query, senza letture aggiuntive.
 *
 * <p>Una pendenza non risolvibile non e' un errore: l'evento viene scritto
 * ugualmente, senza i due campi. La RT puo' essere richiesta anche quando la
 * RPT e' stata nel frattempo svecchiata.
 */
@Slf4j
@Service
public class PendenzaResolver {

    private final RptRepository rptRepository;

    public PendenzaResolver(RptRepository rptRepository) {
        this.rptRepository = rptRepository;
    }

    public Optional<RiferimentoPendenza> resolve(String codDominio, String iuv, String iur) {
        if (codDominio == null || iuv == null || iur == null) {
            return Optional.empty();
        }
        List<Object[]> righe = rptRepository.findRiferimentoPendenza(codDominio, iuv, iur);
        if (righe.isEmpty()) {
            log.debug("Nessuna RPT per codDominio {}, iuv {} e ccp {}: evento GDE senza idPendenza",
                    codDominio, iuv, iur);
            return Optional.empty();
        }
        Object[] riga = righe.get(0);
        return Optional.of(new RiferimentoPendenza((String) riga[0], (String) riga[1]));
    }
}
