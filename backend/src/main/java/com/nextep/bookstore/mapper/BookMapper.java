package com.nextep.bookstore.mapper;

import com.nextep.bookstore.dto.BookResponse;
import com.nextep.bookstore.model.Book;

public final class BookMapper {

	private BookMapper() {
	}

	public static BookResponse toResponse(Book book) {
		return new BookResponse(book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(), book.getCostUsd(),
				book.getSellingPriceLocal(), book.getSellingCurrency(), book.getStockQuantity(), book.getCategory(),
				book.getSupplierCountry(), book.getCreatedAt(), book.getUpdatedAt());
	}

}
