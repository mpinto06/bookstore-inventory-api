package com.nextep.bookstore.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IsbnValidatorTest {

	private final IsbnValidator validator = new IsbnValidator();

	@ParameterizedTest
	@ValueSource(strings = { "0306406152", "030-640-6152", "0 306 40615 2", "080442957X", "080442957x",
			"978-84-376-0494-7", "9788437604947", "978 0 306 40615 7" })
	void acceptsValidIsbns(String isbn) {
		assertTrue(IsbnValidator.isValid(isbn), isbn);
	}

	@ParameterizedTest
	@ValueSource(strings = { "1234567890", "0306406153", "9788437604948", "123456789", "12345678901",
			"978843760494", "03064061X2", "X306406152", "978843760494X", "abcdefghij", "030640615!", "0306\t406152" })
	void rejectsInvalidIsbns(String isbn) {
		assertFalse(IsbnValidator.isValid(isbn), isbn);
	}

	@Test
	void rejectsEmptyAndBlank() {
		assertFalse(IsbnValidator.isValid(""));
		assertFalse(IsbnValidator.isValid("   "));
		assertFalse(IsbnValidator.isValid("- - -"));
	}

	@Test
	void nullIsHandledByNotNullNotByThisConstraint() {
		assertTrue(validator.isValid(null, null));
		assertFalse(IsbnValidator.isValid(null));
		assertNull(IsbnValidator.normalize(null));
	}

	@Test
	void rejectsInputOver32CharsEvenIfItNormalizesToValidIsbn() {
		// 13 dígitos válidos + 20 guiones = 33 caracteres crudos
		String padded = "9788437604947" + "-".repeat(20);
		assertEquals(33, padded.length());
		assertFalse(IsbnValidator.isValid(padded));
		// mismo ISBN con 19 guiones = 32 caracteres: aún permitido
		assertTrue(IsbnValidator.isValid("9788437604947" + "-".repeat(19)));
	}

	@Test
	void rejectsVeryLongInputs() {
		assertFalse(IsbnValidator.isValid("1".repeat(10_000)));
		assertFalse(IsbnValidator.isValid("-".repeat(1_000_000)));
	}

	@Test
	void acceptsManyHyphensWithinLimit() {
		assertTrue(IsbnValidator.isValid("0-3-0-6-4-0-6-1-5-2"));
	}

	@Test
	void normalizeStripsHyphensAndSpacesAndUppercases() {
		assertEquals("9788437604947", IsbnValidator.normalize("978-84-376-0494-7"));
		assertEquals("080442957X", IsbnValidator.normalize(" 0-8044-2957-x "));
	}

}
