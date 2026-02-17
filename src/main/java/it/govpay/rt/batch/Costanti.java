package it.govpay.rt.batch;
public class Costanti {
	public static final String LAST_PROCESSED_ID_KEY = "lastProcessedId";

    // Pattern date per serializzazione/deserializzazione JSON
    // Pattern con millisecondi variabili (1-9 cifre) per deserializzazione sicura da pagoPA
    public static final String PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI = "yyyy-MM-dd'T'HH:mm:ss[.[SSSSSSSSS][SSSSSSSS][SSSSSSS][SSSSSS][SSSSS][SSSS][SSS][SS][S]]";
    public static final String PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI_XXX = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSSSSS][.SSSSSSSS][.SSSSSSS][.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]XXX";

    // Pattern per serializzazione date alle API esterne (3 cifre millisecondi senza timezone) 
    public static final String PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    // Pattern per serializzazione date al GDE (3 cifre millisecondi con timezone)
    public static final String PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    // Questo è un path template fisso definito dalla specifica OpenAPI di pagoPA.
    // Non sono URI completi (mancano protocollo e host) ma template che vengono
    // combinati con il baseUrl configurabile tramite il Connettore nel DB.
    @SuppressWarnings("java:S1075") // Path template fisso da specifica OpenAPI pagoPA, non URI configurabile
    public static final String PATH_GET_RECEIPT = "/organizations/{organizationfiscalcode}/receipts/{iur}/paymentoptions/{iuv}";

    // Operation ID dalla specifica OpenAPI di pagoPA per il recupero ricevuta
    public static final String OPERATION_GET_RECEIPT = "getOrganizationReceiptIuvIur";

    // Operazione SOAP per invio ricevuta a GovPay
    public static final String OPERATION_SEND_RECEIPT = "paSendRTV2";

    private Costanti() {
        // Costruttore privato per evitare istanziazione
    }

}
