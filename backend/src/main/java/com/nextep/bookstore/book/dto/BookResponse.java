package com.nextep.bookstore.book.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BookResponse(
		Long id,
		String title,
		String author,
		String isbn,
		BigDecimal costUsd,
		BigDecimal sellingPriceLocal,
		String sellingCurrency,
		int stockQuantity,
		String category,
		String supplierCountry,
		Instant createdAt,
		Instant updatedAt) {

}
