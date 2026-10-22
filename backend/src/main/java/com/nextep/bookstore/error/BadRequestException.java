package com.nextep.bookstore.error;

/** Entrada del cliente inválida que no cubre Bean Validation (p. ej. el parámetro sort). Responde 400. */
public class BadRequestException extends RuntimeException {

	private final String field;

	public BadRequestException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}

}
