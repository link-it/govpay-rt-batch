package it.govpay.rt.batch.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class LocalDateAdapter extends XmlAdapter<String, LocalDate> {

	@Override
	public LocalDate unmarshal(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return LocalDate.parse(value, DateTimeFormatter.ISO_DATE);
	}

	@Override
	public String marshal(LocalDate value) {
		if (value == null) {
			return null;
		}
		return value.format(DateTimeFormatter.ISO_DATE);
	}
}
