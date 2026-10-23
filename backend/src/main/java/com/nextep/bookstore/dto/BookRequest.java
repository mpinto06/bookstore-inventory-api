package com.nextep.bookstore.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextep.bookstore.validation.NoNul;
import com.nextep.bookstore.validation.ValidCountryCode;
import com.nextep.bookstore.validation.ValidIsbn;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

// Cuerpo de POST y PUT /books. Los campos que el servidor controla (id, selling_price_local,
// selling_currency, created_at, updated_at) se ignoran si el cliente los envía.
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookRequest(

		@Schema(description = "Título del libro", example = "Don Quijote de la Mancha")
		@NotBlank(message = "title es obligatorio")
		@Size(max = 255, message = "title no puede superar los 255 caracteres")
		@NoNul(message = "title contiene caracteres no permitidos")
		String title,

		@Schema(description = "Autor del libro", example = "Miguel de Cervantes")
		@NotBlank(message = "author es obligatorio")
		@Size(max = 255, message = "author no puede superar los 255 caracteres")
		@NoNul(message = "author contiene caracteres no permitidos")
		String author,

		@Schema(description = "ISBN-10 o ISBN-13 con o sin guiones, máx. 32 caracteres y dígito verificador válido. Se guarda sin guiones y es único", example = "978-84-376-0494-7", maxLength = 32)
		@NotBlank(message = "isbn es obligatorio")
		@ValidIsbn(message = "isbn debe ser un ISBN-10 o ISBN-13 válido (máx. 32 caracteres, con dígito verificador correcto)")
		String isbn,

		@Schema(description = "Costo en USD (máx. 8 dígitos enteros y 2 decimales)", example = "12.50")
		@NotNull(message = "cost_usd es obligatorio")
		@DecimalMin(value = "0.01", message = "cost_usd debe ser mayor o igual a 0.01")
		@Digits(integer = 8, fraction = 2, message = "cost_usd admite como máximo 8 dígitos enteros y 2 decimales")
		BigDecimal costUsd,

		@Schema(description = "Unidades en inventario", example = "10")
		@NotNull(message = "stock_quantity es obligatorio")
		@Min(value = 0, message = "stock_quantity no puede ser negativo")
		@Max(value = BookRequest.MAX_STOCK, message = "stock_quantity no puede superar 1000000")
		Integer stockQuantity,

		@Schema(description = "Categoría del libro", example = "Literatura Clásica")
		@NotBlank(message = "category es obligatorio")
		@Size(max = BookRequest.MAX_CATEGORY_LENGTH, message = "category no puede superar los 100 caracteres")
		@NoNul(message = "category contiene caracteres no permitidos")
		String category,

		@Schema(description = "País del proveedor: código ISO 3166-1 alpha-2 real, sin distinguir mayúsculas (se guarda en mayúsculas). Define la moneda del precio de venta", example = "ES", minLength = 2, maxLength = 2)
		@NotBlank(message = "supplier_country es obligatorio")
		@ValidCountryCode(message = "supplier_country debe ser un código de país ISO 3166-1 alpha-2 válido (p. ej. ES, VE)")
		String supplierCountry) {

	public static final int MAX_STOCK = 1_000_000;

	public static final int MAX_CATEGORY_LENGTH = 100;

}
