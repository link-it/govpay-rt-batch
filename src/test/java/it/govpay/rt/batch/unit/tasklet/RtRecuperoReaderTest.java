package it.govpay.rt.batch.unit.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.govpay.rt.batch.dto.RtRetrieveContext;
import it.govpay.rt.batch.entity.RtRecupero;
import it.govpay.rt.batch.repository.RtRecuperoRepository;
import it.govpay.rt.batch.tasklet.RtRecuperoReader;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtRecuperoReader")
class RtRecuperoReaderTest {

    @Mock
    private RtRecuperoRepository rtRecuperoRepository;

    private RtRecuperoReader reader;

    private static final String COD_DOMINIO = "12345678901";
    private static final String IUV = "IUV-1";
    private static final String IUR = "IUR-1";

    @BeforeEach
    void setUp() {
        reader = new RtRecuperoReader(rtRecuperoRepository);
    }

    @Nested
    @DisplayName("initToBeRetrieve")
    class InitToBeRetrieveTest {

        @Test
        @DisplayName("should query solo le righe con esito IS NULL, nessuna finestra temporale")
        void shouldQueryOnlyPendingRows() {
            when(rtRecuperoRepository.findByEsitoIsNullOrderByIdAsc()).thenReturn(Collections.emptyList());

            reader.initToBeRetrieve();

            verify(rtRecuperoRepository).findByEsitoIsNullOrderByIdAsc();
        }

        @Test
        @DisplayName("should popolare la lista mappando id/codDominio/iuv/iur")
        void shouldPopulateListFromRepository() {
            RtRecupero riga1 = RtRecupero.builder().id(1L).codDominio(COD_DOMINIO).iuv(IUV).iur(IUR)
                    .dataRichiesta(OffsetDateTime.now()).build();
            RtRecupero riga2 = RtRecupero.builder().id(2L).codDominio(COD_DOMINIO).iuv("IUV-2").iur("IUR-2")
                    .dataRichiesta(OffsetDateTime.now()).build();
            when(rtRecuperoRepository.findByEsitoIsNullOrderByIdAsc()).thenReturn(List.of(riga1, riga2));

            reader.initToBeRetrieve();

            RtRetrieveContext first = reader.read();
            assertNotNull(first);
            assertEquals(1L, first.getRtId());
            assertEquals(COD_DOMINIO, first.getTaxCode());
            assertEquals(IUV, first.getIuv());
            assertEquals(IUR, first.getIur());

            RtRetrieveContext second = reader.read();
            assertNotNull(second);
            assertEquals(2L, second.getRtId());
        }
    }

    @Nested
    @DisplayName("read")
    class ReadTest {

        @Test
        @DisplayName("should ritornare null quando esaurita")
        void shouldReturnNullWhenExhausted() {
            when(rtRecuperoRepository.findByEsitoIsNullOrderByIdAsc()).thenReturn(Collections.emptyList());

            reader.initToBeRetrieve();

            assertNull(reader.read());
        }
    }
}
