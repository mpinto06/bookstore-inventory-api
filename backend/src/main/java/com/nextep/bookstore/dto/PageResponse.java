package com.nextep.bookstore.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

// Respuesta paginada propia: no se serializa Page de Spring Data para no acoplar el contrato JSON.
public record PageResponse<T>(
		@Schema(description = "Elementos de la página") List<T> content,
		@Schema(description = "Número de página actual, empezando en 0", example = "0") int page,
		@Schema(description = "Tamaño de página solicitado", example = "20") int size,
		@Schema(description = "Total de elementos en todas las páginas", example = "42") long totalElements,
		@Schema(description = "Total de páginas", example = "3") int totalPages) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

}
