package com.nextep.bookstore.util;

import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.nextep.bookstore.exception.BadRequestException;

// Construye el Pageable de los endpoints paginados de /books (listado, búsqueda y stock bajo). El sort llega en snake_case ("campo,asc|desc") y se traduce, vía
// lista blanca, a propiedades de la entidad; nada de lo que escriba el cliente llega a Spring Data sin
// validar (un nombre desconocido lanzaría PropertyReferenceException, es decir, un 500).
public final class BookPaging {

	public static final String DEFAULT_SIZE = "20";
	static final int MAX_SIZE = 100;

	// clave = nombre snake_case del cliente; valor = propiedad de Book (visible para los tests).
	static final Map<String, String> SORTABLE = Map.of(
			"id", "id",
			"title", "title",
			"author", "author",
			"category", "category",
			"cost_usd", "costUsd",
			"stock_quantity", "stockQuantity",
			"created_at", "createdAt");

	private BookPaging() {
	}

	// Orden por defecto: id ascendente.
	public static Pageable pageable(int page, int size, String sort) {
		return pageable(page, size, sort, Sort.by("id"));
	}

	// defaultSort se usa cuando el cliente no envía sort.
	public static Pageable pageable(int page, int size, String sort, Sort defaultSort) {
		if (page < 0) {
			throw new BadRequestException("page", "page debe ser mayor o igual a 0");
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new BadRequestException("size", "size debe estar entre 1 y " + MAX_SIZE);
		}
		PageRequest pageRequest = PageRequest.of(page, size, parseSort(sort, defaultSort));
		if (pageRequest.getOffset() > Integer.MAX_VALUE) {
			throw new BadRequestException("page", "page demasiado grande: el desplazamiento (page * size) no puede superar "
					+ Integer.MAX_VALUE);
		}
		return pageRequest;
	}

	private static Sort parseSort(String sort, Sort defaultSort) {
		if (sort == null || sort.isBlank()) {
			return defaultSort;
		}
		String[] parts = sort.split(",", -1);
		if (parts.length > 2) {
			throw invalidSort();
		}
		String property = SORTABLE.get(parts[0].trim());
		if (property == null) {
			throw invalidSort();
		}
		String direction = parts.length == 2 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
		Sort.Order order = switch (direction) {
			case "asc" -> Sort.Order.asc(property);
			case "desc" -> Sort.Order.desc(property);
			default -> throw invalidSort();
		};
		// id como desempate para que la paginación sea estable
		return property.equals("id") ? Sort.by(order) : Sort.by(order, Sort.Order.asc("id"));
	}

	private static BadRequestException invalidSort() {
		return new BadRequestException("sort", "sort debe tener el formato campo,asc|desc con campo en: "
				+ String.join(", ", SORTABLE.keySet().stream().sorted().toList()));
	}

}
