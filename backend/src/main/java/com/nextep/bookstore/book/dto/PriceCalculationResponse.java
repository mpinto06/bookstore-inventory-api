package com.nextep.bookstore.book.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.nextep.bookstore.exchangerate.RateSource;

public record PriceCalculationResponse(
		Long bookId,
		BigDecimal costUsd,
		BigDecimal exchangeRate,
		BigDecimal costLocal,
		BigDecimal marginPercentage,
		BigDecimal sellingPriceLocal,
		String currency,
		Instant calculationTimestamp,
		RateSource rateSource,
		LocalDate rateDate) {

}
