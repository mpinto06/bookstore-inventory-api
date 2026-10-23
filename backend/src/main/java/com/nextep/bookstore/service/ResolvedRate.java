package com.nextep.bookstore.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nextep.bookstore.enums.RateSource;

public record ResolvedRate(BigDecimal rate, LocalDate rateDate, RateSource source) {
}
