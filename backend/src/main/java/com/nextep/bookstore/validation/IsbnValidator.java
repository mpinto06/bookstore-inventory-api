package com.nextep.bookstore.validation;

import java.util.Locale;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IsbnValidator implements ConstraintValidator<ValidIsbn, String> {

	// Longitud máxima de la entrada cruda, antes de normalizar.
	public static final int MAX_RAW_LENGTH = 32;

	// Quita guiones y espacios y pasa a mayúsculas (la X final del ISBN-10).
	public static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		return raw.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
	}

	public static boolean isValid(String raw) {
		if (raw == null || raw.length() > MAX_RAW_LENGTH) {
			return false;
		}
		String isbn = normalize(raw);
		return switch (isbn.length()) {
			case 10 -> isValidIsbn10(isbn);
			case 13 -> isValidIsbn13(isbn);
			default -> false;
		};
	}

	private static boolean isValidIsbn10(String isbn) {
		int sum = 0;
		for (int i = 0; i < 10; i++) {
			char c = isbn.charAt(i);
			int value;
			if (c >= '0' && c <= '9') {
				value = c - '0';
			}
			else if (c == 'X' && i == 9) {
				value = 10;
			}
			else {
				return false;
			}
			sum += value * (10 - i);
		}
		return sum % 11 == 0;
	}

	private static boolean isValidIsbn13(String isbn) {
		int sum = 0;
		for (int i = 0; i < 13; i++) {
			char c = isbn.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
			sum += (c - '0') * (i % 2 == 0 ? 1 : 3);
		}
		return sum % 10 == 0;
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || value.isBlank() || isValid(value);
	}

}
