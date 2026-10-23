package com.nextep.bookstore.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.nextep.bookstore.enums.RateSource;

import io.swagger.v3.oas.annotations.media.Schema;

public record PriceCalculationResponse(
		@Schema(description = "Id del libro", example = "1") Long bookId,
		@Schema(description = "Costo en USD", example = "15.99") BigDecimal costUsd,
		@Schema(description = "Tasa de cambio USD -> moneda local usada", example = "0.85") BigDecimal exchangeRate,
		@Schema(description = "Costo en moneda local: round2(cost_usd * exchange_rate)", example = "13.59") BigDecimal costLocal,
		@Schema(description = "Margen aplicado sobre cost_local, en porcentaje", example = "40") BigDecimal marginPercentage,
		@Schema(description = "Precio de venta: round2(cost_local * (1 + margen/100)); queda guardado en el libro", example = "19.03") BigDecimal sellingPriceLocal,
		@Schema(description = "Moneda ISO 4217 del país del proveedor", example = "EUR") String currency,
		@Schema(description = "Momento del cálculo (UTC)", example = "2026-10-23T00:57:15.967682500Z") Instant calculationTimestamp,
		@Schema(description = "Origen de la tasa: LIVE = API (últimos 15 min); FALLBACK = tasa guardada en la BD porque la API no respondió", example = "LIVE") RateSource rateSource,
		@Schema(description = "Fecha de la tasa usada (indica su antigüedad con FALLBACK)", example = "2026-10-23") LocalDate rateDate) {

}
