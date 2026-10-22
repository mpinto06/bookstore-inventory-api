package com.nextep.bookstore.validation;

import java.util.Locale;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CountryCodeValidator implements ConstraintValidator<ValidCountryCode, String> {

	private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

	// Pasa a mayúsculas.
	public static String normalize(String raw) {
		return raw == null ? null : raw.toUpperCase(Locale.ROOT);
	}

	public static boolean isValid(String raw) {
		return raw != null && ISO_COUNTRIES.contains(normalize(raw));
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || value.isBlank() || isValid(value);
	}

}
