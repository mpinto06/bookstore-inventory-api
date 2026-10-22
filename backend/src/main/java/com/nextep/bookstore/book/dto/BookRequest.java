package com.nextep.bookstore.book.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextep.bookstore.validation.ValidCountryCode;
import com.nextep.bookstore.validation.ValidIsbn;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST y PUT /books. Los campos que el servidor controla (id, selling_price_local,
 * selling_currency, created_at, updated_at) se ignoran si el cliente los envía.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookRequest(

		@NotBlank(message = "title es obligatorio")
		@Size(max = 255, message = "title no puede superar los 255 caracteres")
		@Pattern(regexp = BookRequest.NO_NUL, message = "title contiene caracteres no permitidos")
		String title,

		@NotBlank(message = "author es obligatorio")
		@Size(max = 255, message = "author no puede superar los 255 caracteres")
		@Pattern(regexp = BookRequest.NO_NUL, message = "author contiene caracteres no permitidos")
		String author,

		@NotBlank(message = "isbn es obligatorio")
		@ValidIsbn(message = "isbn debe ser un ISBN-10 o ISBN-13 válido (máx. 32 caracteres, con dígito verificador correcto)")
		String isbn,

		@NotNull(message = "cost_usd es obligatorio")
		@DecimalMin(value = "0.01", message = "cost_usd debe ser mayor o igual a 0.01")
		@Digits(integer = 8, fraction = 2, message = "cost_usd admite como máximo 8 dígitos enteros y 2 decimales")
		BigDecimal costUsd,

		@NotNull(message = "stock_quantity es obligatorio")
		@Min(value = 0, message = "stock_quantity no puede ser negativo")
		@Max(value = 1_000_000, message = "stock_quantity no puede superar 1000000")
		Integer stockQuantity,

		@NotBlank(message = "category es obligatorio")
		@Size(max = 100, message = "category no puede superar los 100 caracteres")
		@Pattern(regexp = BookRequest.NO_NUL, message = "category contiene caracteres no permitidos")
		String category,

		@NotBlank(message = "supplier_country es obligatorio")
		@ValidCountryCode(message = "supplier_country debe ser un código de país ISO 3166-1 alpha-2 válido (p. ej. ES, VE)")
		String supplierCountry) {

	/** PostgreSQL rechaza el carácter NUL en texto; se valida para devolver 400*/
	static final String NO_NUL = "[^\\x00]*";

}
