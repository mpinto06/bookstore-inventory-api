package com.nextep.bookstore.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoNulValidatorTest {

	private final NoNulValidator validator = new NoNulValidator();

	@Test
	void detectsNulAnywhereInTheString() {
		assertTrue(NoNulValidator.containsNul("\0"));
		assertTrue(NoNulValidator.containsNul("\0abc"));
		assertTrue(NoNulValidator.containsNul("a\0b"));
		assertTrue(NoNulValidator.containsNul("abc\0"));
	}

	@Test
	void acceptsNormalTextNullAndEmpty() {
		assertFalse(NoNulValidator.containsNul("Literatura Clásica"));
		assertFalse(NoNulValidator.containsNul(""));
		assertFalse(NoNulValidator.containsNul(null));
		assertFalse(NoNulValidator.containsNul("0"));
		assertFalse(NoNulValidator.containsNul("\\0"));
	}

	@Test
	void constraintValidatorRejectsNulAndAcceptsNull() {
		assertFalse(validator.isValid("a\0b", null));
		assertTrue(validator.isValid("abc", null));
		assertTrue(validator.isValid(null, null));
	}

}
