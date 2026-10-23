package com.nextep.bookstore.exception;

public class BookNotFoundException extends RuntimeException {

	public BookNotFoundException(Long id) {
		super("No existe un libro con id " + id);
	}

}
