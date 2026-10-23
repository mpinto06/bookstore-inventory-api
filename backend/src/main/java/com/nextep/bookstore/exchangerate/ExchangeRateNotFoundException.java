package com.nextep.bookstore.exchangerate;

// No hay ni tasa en vivo ni tasa guardada para la moneda. El mensaje es seguro para mostrar al cliente.
public class ExchangeRateNotFoundException extends ExchangeRateUnavailableException {

	public ExchangeRateNotFoundException(String currency) {
		super("No hay tasa de cambio disponible para la moneda " + currency + "; intente más tarde");
	}

}
