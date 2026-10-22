package com.nextep.bookstore.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CountryCodeValidatorTest {

	private final CountryCodeValidator validator = new CountryCodeValidator();

	@ParameterizedTest
	@ValueSource(strings = { "ES", "VE", "US", "CO", "es", "Ve" })
	void acceptsRealIsoCodes(String code) {
		assertTrue(CountryCodeValidator.isValid(code), code);
	}

	@ParameterizedTest
	@ValueSource(strings = { "12", "XX", "ZZ", "E", "ESP", "E S", " ES", "ES ", "E1", "--", "ÑÑ" })
	void rejectsNonIsoCodes(String code) {
		assertFalse(CountryCodeValidator.isValid(code), code);
	}

	@Test
	void rejectsEmptyAndBlank() {
		assertFalse(CountryCodeValidator.isValid(""));
		assertFalse(CountryCodeValidator.isValid("  "));
	}

	@Test
	void nullIsHandledByNotNullNotByThisConstraint() {
		assertTrue(validator.isValid(null, null));
		assertFalse(CountryCodeValidator.isValid(null));
		assertNull(CountryCodeValidator.normalize(null));
	}

	@Test
	void rejectsVeryLongInputs() {
		assertFalse(CountryCodeValidator.isValid("E".repeat(100_000)));
		assertFalse(CountryCodeValidator.isValid("ES" + "-".repeat(1_000)));
	}

	@Test
	void normalizeUppercases() {
		assertEquals("ES", CountryCodeValidator.normalize("es"));
	}

}
