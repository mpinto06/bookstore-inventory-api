package com.nextep.bookstore.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoNulValidator implements ConstraintValidator<NoNul, String> {

	// También lo usa el servicio para validar parámetros de consulta, que no pasan por Bean Validation
	public static boolean containsNul(String value) {
		return value != null && value.indexOf('\0') >= 0;
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return !containsNul(value);
	}

}
