package com.nextep.bookstore.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.nextep.bookstore.util.AbstractIntegrationTest;

class BookQueryIntegrationTest extends AbstractIntegrationTest {

	// ---- search ----

	@Test
	void searchMatchesTheWholeCategoryIgnoringCase() throws Exception {
		insertBook("Uno", ISBN_A, "10.00", 5, "Literatura Clásica", "ES");
		insertBook("Dos", ISBN_B, "10.00", 5, "Literatura Clásica", "ES");
		insertBook("Tres", ISBN_C, "10.00", 5, "Ciencia", "ES");

		mvc.perform(get("/books/search").param("category", "literatura clásica")).andExpect(status().isOk())
				.andExpect(jsonPath("$.total_elements").value(2)).andExpect(jsonPath("$.content", hasSize(2)));
		// coincidencia exacta, no por fragmento
		mvc.perform(get("/books/search").param("category", "Literatura")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content", empty()));
	}

	@Test
	void searchWithAnUnknownCategoryIsAnEmpty200() throws Exception {
		insertBook("Uno", ISBN_A, "10.00", 5, "Ciencia", "ES");

		mvc.perform(get("/books/search").param("category", "Fantasía")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content", empty())).andExpect(jsonPath("$.total_elements").value(0));
	}

	@Test
	void searchWithoutCategoryIsA400OnTheSearchRouteNotAnIdMismatch() throws Exception {
		assertFieldError(assertApiError(mvc.perform(get("/books/search")), 400, "/books/search"), "category");
	}

	static Stream<String> blankOrTooLongCategories() {
		return Stream.of("", "x".repeat(101));
	}

	@ParameterizedTest
	@MethodSource("blankOrTooLongCategories")
	void searchWithBlankOrTooLongCategoryIsA400(String category) throws Exception {
		assertApiError(mvc.perform(get("/books/search").param("category", category)), 400, "/books/search");
	}

	// ---- low-stock ----

	@Test
	void lowStockDefaultsToThresholdTenInclusiveOrderedByLeastStockFirst() throws Exception {
		insertBook("Diez", ISBN_A, "10.00", 10, "C", "ES");
		insertBook("Once", ISBN_B, "10.00", 11, "C", "ES");
		insertBook("Tres", ISBN_C, "10.00", 3, "C", "ES");

		mvc.perform(get("/books/low-stock")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].stock_quantity", contains(3, 10)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "-1", "1000001", "abc" })
	void lowStockWithAnInvalidThresholdIsA400(String threshold) throws Exception {
		assertApiError(mvc.perform(get("/books/low-stock?threshold=" + threshold)), 400, "/books/low-stock");
	}

	// ---- categories ----

	@Test
	void categoriesAreDistinctAndAlphabetical() throws Exception {
		insertBook("Uno", ISBN_A, "10.00", 5, "Zeta", "ES");
		insertBook("Dos", ISBN_B, "10.00", 5, "Alfa", "ES");
		insertBook("Tres", ISBN_C, "10.00", 5, "Alfa", "ES");

		mvc.perform(get("/books/categories")).andExpect(status().isOk()).andExpect(jsonPath("$", contains("Alfa", "Zeta")));
	}

}
