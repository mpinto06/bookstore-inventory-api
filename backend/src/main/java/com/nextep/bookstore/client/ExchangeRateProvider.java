package com.nextep.bookstore.client;

public interface ExchangeRateProvider {

	// Si algo sale mal o la respuesta no sirve, lanza ExchangeRateUnavailableException.
	ExchangeRateSnapshot fetchLatest();

}
