package it.govpay.rt.batch.unit.tasklet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

import it.govpay.rt.batch.Costanti;
import it.govpay.rt.batch.dto.RtRecuperoBatch;
import it.govpay.rt.batch.repository.RtRecuperoRepository;
import it.govpay.rt.batch.tasklet.RtRecuperoWriter;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtRecuperoWriter")
class RtRecuperoWriterTest {

    @Mock
    private RtRecuperoRepository rtRecuperoRepository;

    private RtRecuperoWriter writer;

    private static final Clock CLOCK = Clock.system(ZoneId.of("Europe/Rome"));
    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "IUV-1";
    private static final String IUR = "IUR-1";

    @BeforeEach
    void setUp() {
        writer = new RtRecuperoWriter(rtRecuperoRepository, CLOCK);
    }

    @Nested
    @DisplayName("write")
    class WriteTest {

        @Test
        @DisplayName("successo (esito null): elimina la riga, non chiama marca")
        void successoEliminaRiga() {
            RtRecuperoBatch batch = RtRecuperoBatch.builder()
                    .id(10L).codDominio(TAX_CODE).iuv(IUV).iur(IUR).build();

            writer.write(new Chunk<>(Arrays.asList(batch)));

            verify(rtRecuperoRepository).deleteById(10L);
            verify(rtRecuperoRepository, never()).marca(any(), any(), any());
        }

        @Test
        @DisplayName("404 (NON_DISPONIBILE): marca la riga, non la elimina")
        void nonDisponibileMarcaRiga() {
            RtRecuperoBatch batch = RtRecuperoBatch.builder()
                    .id(10L).codDominio(TAX_CODE).iuv(IUV).iur(IUR)
                    .esito(Costanti.ESITO_RECUPERO_NON_DISPONIBILE)
                    .message("Ricevuta non disponibile su pagoPA")
                    .build();

            writer.write(new Chunk<>(Arrays.asList(batch)));

            verify(rtRecuperoRepository).marca(eq(10L), eq(Costanti.ESITO_RECUPERO_NON_DISPONIBILE), any());
            verify(rtRecuperoRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("MBT allegato mancante: marca la riga con l'esito dedicato")
        void mbtMarcaRiga() {
            RtRecuperoBatch batch = RtRecuperoBatch.builder()
                    .id(10L).codDominio(TAX_CODE).iuv(IUV).iur(IUR)
                    .esito(Costanti.ESITO_RECUPERO_MBT_ALLEGATO_MANCANTE)
                    .build();

            writer.write(new Chunk<>(Arrays.asList(batch)));

            verify(rtRecuperoRepository).marca(eq(10L), eq(Costanti.ESITO_RECUPERO_MBT_ALLEGATO_MANCANTE), any());
        }

        @Test
        @DisplayName("item null: nessuna interazione col repository per quell'item")
        void itemNullNonInteragisce() {
            writer.write(new Chunk<>(Arrays.asList((RtRecuperoBatch) null)));

            verifyNoInteractions(rtRecuperoRepository);
        }

        @Test
        @DisplayName("piu' righe indipendenti nello stesso chunk: ognuna elaborata per conto proprio")
        void righeIndipendentiNelloStessoChunk() {
            RtRecuperoBatch successo = RtRecuperoBatch.builder().id(1L).codDominio(TAX_CODE).iuv(IUV).iur(IUR).build();
            RtRecuperoBatch marcato = RtRecuperoBatch.builder().id(2L).codDominio(TAX_CODE).iuv("IUV-2").iur("IUR-2")
                    .esito(Costanti.ESITO_RECUPERO_ERRORE).build();

            writer.write(new Chunk<>(Arrays.asList(successo, marcato)));

            verify(rtRecuperoRepository).deleteById(1L);
            verify(rtRecuperoRepository).marca(eq(2L), eq(Costanti.ESITO_RECUPERO_ERRORE), any());
        }
    }
}
