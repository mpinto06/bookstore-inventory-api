package com.nextep.bookstore.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

// Formato único de error de la API. field_errors solo aparece en errores de validación.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
		@Schema(description = "Momento del error (UTC)", example = "2026-10-22T14:19:20.248Z") Instant timestamp,
		@Schema(description = "Código de estado HTTP", example = "400") int status,
		@Schema(description = "Descripción estándar del estado HTTP", example = "Bad Request") String error,
		@Schema(description = "Mensaje para el cliente", example = "La petición contiene datos inválidos") String message,
		@Schema(description = "Ruta de la petición", example = "/books") String path,
		@Schema(description = "Detalle por campo; solo aparece en errores de validación") List<FieldError> fieldErrors) {

	public record FieldError(
			@Schema(description = "Campo o parámetro inválido, en snake_case", example = "cost_usd") String field,
			@Schema(description = "Motivo", example = "cost_usd debe ser mayor o igual a 0.01") String message) {
	}

}
