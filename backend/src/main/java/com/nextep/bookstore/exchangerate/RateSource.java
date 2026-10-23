package com.nextep.bookstore.exchangerate;

public enum RateSource {

	// Datos de la API de los últimos minutos (caché dentro del TTL o consulta reciente)
	LIVE,

	// Tasa guardada en exchange_rates porque la API no respondió o no trajo la moneda
	FALLBACK

}
