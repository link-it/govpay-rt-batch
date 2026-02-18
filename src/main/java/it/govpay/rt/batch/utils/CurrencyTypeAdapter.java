package it.govpay.rt.batch.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class CurrencyTypeAdapter extends XmlAdapter<String, Long> {
	private static BigDecimal centMultiplier = new BigDecimal( 100 );
	private static DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols(Locale.US);
	private static DecimalFormat df = new DecimalFormat("#.00", decimalSymbols);

	public static Long convertBigDecimalCents(BigDecimal bigCurrency) {
		if (bigCurrency == null)
			return null;
		return bigCurrency.multiply( centMultiplier ).longValue();
	}

	@Override
	public Long unmarshal( String xmlCurrency ) {
		if (xmlCurrency != null) {
			BigDecimal bigCurrency = new BigDecimal(xmlCurrency);
			return convertBigDecimalCents(bigCurrency);
		}
		return null;
	}

	@Override
	public String marshal(Long currency) {
		if (currency == null)
			return null;
		BigDecimal bigValue = new BigDecimal( currency );
		return df.format(bigValue.divide(centMultiplier).doubleValue());
	}
}
