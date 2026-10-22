package com.nextep.bookstore.book;

public class DuplicateIsbnException extends RuntimeException {

	public DuplicateIsbnException(String isbn) {
		super("Ya existe un libro con el ISBN " + isbn);
	}

}
