package com.nextep.bookstore.book.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** Respuesta paginada propia: no se serializa Page de Spring Data para no acoplar el contrato JSON. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

}
