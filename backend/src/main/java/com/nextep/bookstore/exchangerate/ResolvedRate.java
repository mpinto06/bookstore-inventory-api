package com.nextep.bookstore.exchangerate;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResolvedRate(BigDecimal rate, LocalDate rateDate, RateSource source) {
}
