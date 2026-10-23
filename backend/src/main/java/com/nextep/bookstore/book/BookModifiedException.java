package com.nextep.bookstore.book;

public class BookModifiedException extends RuntimeException {

	public BookModifiedException(Long id) {
		super("El libro " + id + " cambió mientras se calculaba el precio; intente de nuevo");
	}

}
