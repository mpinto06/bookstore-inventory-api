package com.nextep.bookstore.client;

public interface ExchangeRateProvider {

	// Lanza ExchangeRateUnavailableException ante cualquier fallo o respuesta inválida.
	ExchangeRateSnapshot fetchLatest();

}
