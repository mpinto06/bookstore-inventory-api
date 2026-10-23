package com.nextep.bookstore.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

// Tasas con base USD (moneda -> unidades por 1 USD) y la fecha que trae la API.
public record ExchangeRateSnapshot(LocalDate date, Map<String, BigDecimal> rates) {
}
