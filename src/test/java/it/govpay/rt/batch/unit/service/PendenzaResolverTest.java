package it.govpay.rt.batch.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.govpay.rt.batch.dto.RiferimentoPendenza;
import it.govpay.rt.batch.repository.RptRepository;
import it.govpay.rt.batch.service.PendenzaResolver;

@ExtendWith(MockitoExtension.class)
@DisplayName("PendenzaResolver")
class PendenzaResolverTest {

    @Mock
    private RptRepository rptRepository;

    @InjectMocks
    private PendenzaResolver resolver;

    private static final String COD_DOMINIO = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";
    private static final String ID_PENDENZA = "PENDENZA-1";
    private static final String ID_A2A = "APP-TEST";

    @Test
    @DisplayName("should ritornare idPendenza e idA2A della RPT individuata dalla tripla")
    void shouldReturnRiferimentoPendenza() {
        when(rptRepository.findRiferimentoPendenza(COD_DOMINIO, IUV, IUR))
                .thenReturn(List.<Object[]>of(new Object[]{ID_PENDENZA, ID_A2A}));

        Optional<RiferimentoPendenza> esito = resolver.resolve(COD_DOMINIO, IUV, IUR);

        assertTrue(esito.isPresent());
        assertEquals(ID_PENDENZA, esito.get().idPendenza());
        assertEquals(ID_A2A, esito.get().idA2A());
    }

    @Test
    @DisplayName("should cercare per tripla completa: iur e' il ccp della RPT")
    void shouldQueryByFullTriple() {
        when(rptRepository.findRiferimentoPendenza(COD_DOMINIO, IUV, IUR))
                .thenReturn(List.<Object[]>of(new Object[]{ID_PENDENZA, ID_A2A}));

        resolver.resolve(COD_DOMINIO, IUV, IUR);

        verify(rptRepository).findRiferimentoPendenza(COD_DOMINIO, IUV, IUR);
    }

    @Test
    @DisplayName("should ritornare empty quando non esiste una RPT per la tripla")
    void shouldReturnEmptyWhenNoRpt() {
        when(rptRepository.findRiferimentoPendenza(COD_DOMINIO, IUV, IUR))
                .thenReturn(Collections.emptyList());

        assertTrue(resolver.resolve(COD_DOMINIO, IUV, IUR).isEmpty());
    }

    @Test
    @DisplayName("should ritornare empty senza interrogare il DB se manca un elemento della tripla")
    void shouldReturnEmptyWithoutQueryOnNullInput() {
        assertTrue(resolver.resolve(null, IUV, IUR).isEmpty());
        assertTrue(resolver.resolve(COD_DOMINIO, null, IUR).isEmpty());
        assertTrue(resolver.resolve(COD_DOMINIO, IUV, null).isEmpty());

        verify(rptRepository, never()).findRiferimentoPendenza(any(), any(), any());
    }
}
