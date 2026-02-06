package it.govpay.rt.batch;

public class Costanti {
    public static final String MSG_PAYLOAD_NON_SERIALIZZABILE = "Payload non serializzabile";

    // Questo è un path template fisso definito dalla specifica OpenAPI di pagoPA.
    // Non sono URI completi (mancano protocollo e host) ma template che vengono
    // combinati con il baseUrl configurabile in PagoPAProperties.
    // Soppressione S1075: path template API fissi, non URI configurabili
    public static final String PATH_GET_RECEIPT = "/organizations/{organizationfiscalcode}/receipts/{iur}/paymentoptions/{iuv}";

    // Operazione SOAP per invio ricevuta a GovPay
    public static final String OPERATION_SEND_RECEIPT = "paSendRTV2";

    private Costanti() {
        // Costruttore privato per evitare istanziazione
    }

}
