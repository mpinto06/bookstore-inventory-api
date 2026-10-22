package com.nextep.bookstore.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// Formato único de error de la API. field_errors solo aparece en errores de validación.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String error, String message, String path,
		List<FieldError> fieldErrors) {

	public record FieldError(String field, String message) {
	}

}
