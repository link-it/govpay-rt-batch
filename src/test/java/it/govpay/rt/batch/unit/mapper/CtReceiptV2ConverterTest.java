package it.govpay.rt.batch.unit.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import it.gov.pagopa.pagopa_api.pa.pafornode.CtReceiptV2;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.StEntityUniqueIdentifierType;
import it.gov.pagopa.pagopa_api.xsd.common_types.v1_0.StOutcome;
import it.govpay.rt.batch.service.mapper.CtReceiptV2Converter;
import it.govpay.rt.client.model.CtReceiptModelResponse;
import it.govpay.rt.client.model.Debtor;
import it.govpay.rt.client.model.MapEntry;
import it.govpay.rt.client.model.Payer;
import it.govpay.rt.client.model.TransferPA;

@DisplayName("CtReceiptV2Converter")
class CtReceiptV2ConverterTest {

    private static final String COD_INTERMEDIARIO = "12345678901";
    private static final String COD_STAZIONE = "12345678901_01";
    private static final String COD_DOMINIO = "12345678901";

    private CtReceiptModelResponse response;

    @BeforeEach
    void setUp() {
        response = createBasicResponse();
    }

    private CtReceiptModelResponse createBasicResponse() {
        CtReceiptModelResponse r = new CtReceiptModelResponse();
        r.setReceiptId("receipt-123");
        r.setNoticeNumber("302000000000000001");
        r.setFiscalCode(COD_DOMINIO);
        r.setOutcome("OK");
        r.setCreditorReferenceId("01234567890123456");
        r.setPaymentAmount(new BigDecimal("100.50"));
        r.setDescription("Test payment");
        r.setCompanyName("Test Company");
        r.setIdPSP("AGID_01");
        r.setIdChannel("AGID_01_ONUS");
        r.setPspCompanyName("PSP Company");
        r.setPaymentMethod("CARD");
        r.setFee(new BigDecimal("1.50"));
        r.setApplicationDate(LocalDate.of(2024, 1, 15));
        r.setTransferDate(LocalDate.of(2024, 1, 16));
        return r;
    }

    @Nested
    @DisplayName("toPaSendRTV2Request")
    class ToPaSendRTV2RequestTest {

        @Test
        @DisplayName("should create request with correct header fields")
        void shouldCreateRequestWithCorrectHeaderFields() {
            PaSendRTV2Request result = CtReceiptV2Converter.toPaSendRTV2Request(
                    COD_INTERMEDIARIO, COD_STAZIONE, COD_DOMINIO, response);

            assertNotNull(result);
            assertEquals(COD_INTERMEDIARIO, result.getIdBrokerPA());
            assertEquals(COD_STAZIONE, result.getIdStation());
            assertEquals(COD_DOMINIO, result.getIdPA());
            assertNotNull(result.getReceipt());
        }

        @Test
        @DisplayName("should convert receipt data correctly")
        void shouldConvertReceiptDataCorrectly() {
            PaSendRTV2Request result = CtReceiptV2Converter.toPaSendRTV2Request(
                    COD_INTERMEDIARIO, COD_STAZIONE, COD_DOMINIO, response);

            CtReceiptV2 receipt = result.getReceipt();
            assertEquals("receipt-123", receipt.getReceiptId());
            assertEquals("302000000000000001", receipt.getNoticeNumber());
            assertEquals(COD_DOMINIO, receipt.getFiscalCode());
            assertEquals(StOutcome.OK, receipt.getOutcome());
            assertEquals(new BigDecimal("100.50"), receipt.getPaymentAmount());
        }
    }

    @Nested
    @DisplayName("toCtReceiptV2")
    class ToCtReceiptV2Test {

        @Test
        @DisplayName("should convert basic fields")
        void shouldConvertBasicFields() {
            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertNotNull(result);
            assertFalse(result.isStandIn());
            assertEquals("receipt-123", result.getReceiptId());
            assertEquals(StOutcome.OK, result.getOutcome());
            assertEquals(new BigDecimal("1.50"), result.getFee());
        }

        @Test
        @DisplayName("should handle null debtor")
        void shouldHandleNullDebtor() {
            response.setDebtor(null);

            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertNull(result.getDebtor());
        }

        @Test
        @DisplayName("should convert debtor with F type identifier")
        void shouldConvertDebtorWithFTypeIdentifier() {
            Debtor debtor = new Debtor();
            debtor.setFullName("Mario Rossi");
            debtor.setEntityUniqueIdentifierType(Debtor.EntityUniqueIdentifierTypeEnum.F);
            debtor.setEntityUniqueIdentifierValue("RSSMRA80A01H501U");
            debtor.setEmail("mario@test.it");
            debtor.setCity("Roma");
            debtor.setPostalCode("00100");
            debtor.setCountry("IT");
            response.setDebtor(debtor);

            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertNotNull(result.getDebtor());
            assertEquals("Mario Rossi", result.getDebtor().getFullName());
            assertEquals(StEntityUniqueIdentifierType.F,
                    result.getDebtor().getUniqueIdentifier().getEntityUniqueIdentifierType());
            assertEquals("RSSMRA80A01H501U",
                    result.getDebtor().getUniqueIdentifier().getEntityUniqueIdentifierValue());
        }

        @Test
        @DisplayName("should convert debtor with G type identifier")
        void shouldConvertDebtorWithGTypeIdentifier() {
            Debtor debtor = new Debtor();
            debtor.setFullName("Test SRL");
            debtor.setEntityUniqueIdentifierType(Debtor.EntityUniqueIdentifierTypeEnum.G);
            debtor.setEntityUniqueIdentifierValue("12345678901");
            response.setDebtor(debtor);

            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertEquals(StEntityUniqueIdentifierType.G,
                    result.getDebtor().getUniqueIdentifier().getEntityUniqueIdentifierType());
        }

        @Test
        @DisplayName("should handle null payer")
        void shouldHandleNullPayer() {
            response.setPayer(null);

            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertNull(result.getPayer());
        }

        @Test
        @DisplayName("should convert payer correctly")
        void shouldConvertPayerCorrectly() {
            Payer payer = new Payer();
            payer.setFullName("Luigi Verdi");
            payer.setEntityUniqueIdentifierType(Payer.EntityUniqueIdentifierTypeEnum.F);
            payer.setEntityUniqueIdentifierValue("VRDLGU80A01H501U");
            response.setPayer(payer);

            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertNotNull(result.getPayer());
            assertEquals("Luigi Verdi", result.getPayer().getFullName());
        }

        @Test
        @DisplayName("should handle null transfer list")
        void shouldHandleNullTransferList() {
            response.setTransferList(null);

            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertNull(result.getTransferList());
        }

        @Test
        @DisplayName("should convert transfer list")
        void shouldConvertTransferList() {
            TransferPA transfer = new TransferPA();
            transfer.setIdTransfer(1);
            transfer.setTransferAmount(new BigDecimal("100.50"));
            transfer.setFiscalCodePA(COD_DOMINIO);
            transfer.setIban("IT60X0542811101000000123456");
            transfer.setRemittanceInformation("Test payment");
            transfer.setTransferCategory("0101001IM");
            response.setTransferList(Arrays.asList(transfer));

            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertNotNull(result.getTransferList());
            assertEquals(1, result.getTransferList().getTransfer().size());
            assertEquals(1, result.getTransferList().getTransfer().get(0).getIdTransfer());
            assertEquals(new BigDecimal("100.50"),
                    result.getTransferList().getTransfer().get(0).getTransferAmount());
        }

        @Test
        @DisplayName("should convert transfer with MBD attachment")
        void shouldConvertTransferWithMbdAttachment() {
            TransferPA transfer = new TransferPA();
            transfer.setIdTransfer(1);
            transfer.setTransferAmount(new BigDecimal("100.50"));
            transfer.setFiscalCodePA(COD_DOMINIO);
            transfer.setMbdAttachment("base64encodeddata");
            response.setTransferList(Arrays.asList(transfer));

            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertNotNull(result.getTransferList().getTransfer().get(0).getMBDAttachment());
        }

        @Test
        @DisplayName("should handle null metadata")
        void shouldHandleNullMetadata() {
            response.setMetadata(null);

            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertNull(result.getMetadata());
        }

        @Test
        @DisplayName("should convert metadata")
        void shouldConvertMetadata() {
            MapEntry entry1 = new MapEntry();
            entry1.setKey("key1");
            entry1.setValue("value1");
            MapEntry entry2 = new MapEntry();
            entry2.setKey("key2");
            entry2.setValue("value2");
            response.setMetadata(Arrays.asList(entry1, entry2));

            CtReceiptV2 result = CtReceiptV2Converter.toCtReceiptV2(response);

            assertNotNull(result.getMetadata());
            assertEquals(2, result.getMetadata().getMapEntry().size());
            assertEquals("key1", result.getMetadata().getMapEntry().get(0).getKey());
            assertEquals("value1", result.getMetadata().getMapEntry().get(0).getValue());
        }
    }
}
