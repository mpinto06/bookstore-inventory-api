package com.nextep.bookstore.book;

import com.nextep.bookstore.book.dto.BookResponse;

final class BookMapper {

	private BookMapper() {
	}

	static BookResponse toResponse(Book book) {
		return new BookResponse(book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(), book.getCostUsd(),
				book.getSellingPriceLocal(), book.getSellingCurrency(), book.getStockQuantity(), book.getCategory(),
				book.getSupplierCountry(), book.getCreatedAt(), book.getUpdatedAt());
	}

}
