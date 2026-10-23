package com.nextep.bookstore.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record StoredExchangeRate(String currency, BigDecimal rate, LocalDate rateDate, OffsetDateTime fetchedAt) {
}
