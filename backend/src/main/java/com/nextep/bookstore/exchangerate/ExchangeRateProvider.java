package com.nextep.bookstore.exchangerate;

public interface ExchangeRateProvider {

	// Lanza ExchangeRateUnavailableException ante cualquier fallo o respuesta inválida.
	ExchangeRateSnapshot fetchLatest();

}
