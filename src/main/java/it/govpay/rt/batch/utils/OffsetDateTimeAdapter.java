package it.govpay.rt.batch.utils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class OffsetDateTimeAdapter extends XmlAdapter<String, OffsetDateTime> {

	@Override
	public OffsetDateTime unmarshal(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
	}

	@Override
	public String marshal(OffsetDateTime value) {
		if (value == null) {
			return null;
		}
		return value.format(DateTimeFormatter.ISO_DATE_TIME);
	}
}
