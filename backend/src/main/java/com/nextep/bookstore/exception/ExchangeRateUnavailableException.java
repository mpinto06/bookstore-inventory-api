package com.nextep.bookstore.exception;

// La API externa de tasas falló o devolvió una respuesta inválida.
public class ExchangeRateUnavailableException extends RuntimeException {

	public ExchangeRateUnavailableException(String message) {
		super(message);
	}

	public ExchangeRateUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
