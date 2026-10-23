package com.nextep.bookstore.exception;

public class NoLocalCurrencyException extends RuntimeException {

	public NoLocalCurrencyException(String country) {
		super("El país del proveedor '" + country + "' no tiene una moneda asociada; no se puede calcular el precio");
	}

}
