package com.nextep.bookstore.book.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

public record BookResponse(
		@Schema(description = "Identificador del libro", example = "1", readOnly = true) Long id,
		@Schema(description = "Título del libro", example = "Don Quijote de la Mancha") String title,
		@Schema(description = "Autor del libro", example = "Miguel de Cervantes") String author,
		@Schema(description = "ISBN normalizado (sin guiones)", example = "9788437604947") String isbn,
		@Schema(description = "Costo en USD", example = "12.50") BigDecimal costUsd,
		@Schema(description = "Precio de venta en la moneda local; null hasta ejecutar calculate-price (y de nuevo tras un PUT que cambie cost_usd o supplier_country)", example = "19.03", nullable = true, readOnly = true) BigDecimal sellingPriceLocal,
		@Schema(description = "Moneda ISO 4217 de selling_price_local; null cuando el precio es null", example = "EUR", nullable = true, readOnly = true) String sellingCurrency,
		@Schema(description = "Unidades en inventario", example = "10") int stockQuantity,
		@Schema(description = "Categoría del libro", example = "Literatura Clásica") String category,
		@Schema(description = "País del proveedor (ISO 3166-1 alpha-2)", example = "ES") String supplierCountry,
		@Schema(description = "Fecha de creación (UTC)", example = "2026-10-22T14:19:20.248Z", readOnly = true) Instant createdAt,
		@Schema(description = "Última modificación (UTC)", example = "2026-10-22T14:19:20.248Z", readOnly = true) Instant updatedAt) {

}
