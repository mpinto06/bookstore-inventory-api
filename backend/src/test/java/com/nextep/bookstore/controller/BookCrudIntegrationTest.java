package com.nextep.bookstore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import com.nextep.bookstore.util.AbstractIntegrationTest;

class BookCrudIntegrationTest extends AbstractIntegrationTest {

	// ---- POST ----

	@Test
	void createsABookAndReturnsLocationAndTimestamps() throws Exception {
		mvc.perform(post("/books").contentType(MediaType.APPLICATION_JSON).content(bookJson(ISBN_A, "12.50", "10", "es")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern(".*/books/\\d+")))
				.andExpect(jsonPath("$.created_at").isNotEmpty()).andExpect(jsonPath("$.updated_at").isNotEmpty())
				.andExpect(jsonPath("$.supplier_country").value("ES"))
				.andExpect(jsonPath("$.selling_price_local").value(nullValue()))
				.andExpect(jsonPath("$.selling_currency").value(nullValue()));

		assertThat(jdbc.queryForObject("select supplier_country from books where isbn = ?", String.class, ISBN_A))
				.isEqualTo("ES");
	}

	@ParameterizedTest
	@ValueSource(strings = { ISBN_B, "978-84-376-0494-7" })
	void duplicateIsbnIsA409EvenWithDifferentHyphenation(String secondIsbn) throws Exception {
		insertBook(ISBN_B, "10.00", "ES");

		assertFieldError(assertApiError(
				mvc.perform(post("/books").contentType(MediaType.APPLICATION_JSON)
						.content(bookJson(secondIsbn, "10.00", "5", "ES"))),
				409, "/books"), "isbn");
	}

	static Stream<Arguments> invalidBooks() {
		return Stream.of(
				Arguments.of("cost 0.001", bookJson(ISBN_A, "0.001", "5", "ES"), "cost_usd"),
				Arguments.of("cost 0", bookJson(ISBN_A, "0", "5", "ES"), "cost_usd"),
				Arguments.of("cost -5", bookJson(ISBN_A, "-5", "5", "ES"), "cost_usd"),
				Arguments.of("stock -1", bookJson(ISBN_A, "10", "-1", "ES"), "stock_quantity"),
				Arguments.of("stock 1000001", bookJson(ISBN_A, "10", "1000001", "ES"), "stock_quantity"),
				// desborda Integer: Jackson lo rechaza antes de la validación, así que no hay field_errors
				Arguments.of("stock overflow", bookJson(ISBN_A, "10", "99999999999999", "ES"), null),
				Arguments.of("isbn bad check digit", bookJson("1234567890", "10", "5", "ES"), "isbn"),
				Arguments.of("isbn 200 hyphens", bookJson(ISBN_B + "-".repeat(200), "10", "5", "ES"), "isbn"),
				Arguments.of("country 12", bookJson(ISBN_A, "10", "5", "12"), "supplier_country"),
				Arguments.of("country ZZ", bookJson(ISBN_A, "10", "5", "ZZ"), "supplier_country"),
				Arguments.of("cost not a number", "{\"cost_usd\":\"abc\"}", "cost_usd"),
				Arguments.of("empty body", "", null),
				Arguments.of("malformed json", "{\"title\":", null));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidBooks")
	void invalidBodiesAreA400WithApiErrorShape(String name, String json, String field) throws Exception {
		var result = assertApiError(mvc.perform(post("/books").contentType(MediaType.APPLICATION_JSON).content(json)),
				400, "/books");
		if (field != null) {
			assertFieldError(result, field);
		}
		assertThat(jdbc.queryForObject("select count(*) from books", Long.class)).isZero();
	}

	@Test
	void fieldsTheClientDoesNotControlAreIgnored() throws Exception {
		String json = """
				{"id":999,"title":"T","author":"A","isbn":"%s","cost_usd":10,"stock_quantity":1,"category":"C",
				 "supplier_country":"ES","selling_price_local":5.00,"selling_currency":"USD",
				 "created_at":"2000-01-01T00:00:00Z","updated_at":"2000-01-01T00:00:00Z"}""".formatted(ISBN_A);

		mvc.perform(post("/books").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(999)))
				.andExpect(jsonPath("$.selling_price_local").value(nullValue()))
				.andExpect(jsonPath("$.selling_currency").value(nullValue()))
				.andExpect(jsonPath("$.created_at").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.startsWith("2000"))));
	}

	// ---- GET: ids ----

	@ParameterizedTest
	@ValueSource(strings = { "abc", "0", "-1", "99999999999999999999" })
	void invalidIdsAreA400(String id) throws Exception {
		assertFieldError(assertApiError(mvc.perform(get("/books/" + id)), 400, "/books/" + id), "id");
	}

	@Test
	void missingBookIsA404WithoutFieldErrors() throws Exception {
		assertApiError(mvc.perform(get("/books/999999")), 404, "/books/999999")
				.andExpect(jsonPath("$.field_errors").doesNotExist());
	}

	// ---- GET: paginación y orden ----

	@ParameterizedTest
	@ValueSource(strings = { "page=-1", "size=0", "size=101", "page=abc", "sort=inexistente" })
	void invalidPaginationIsA400(String query) throws Exception {
		assertApiError(mvc.perform(get("/books?" + query)), 400, "/books");
	}

	@Test
	void sortsByCostDescending() throws Exception {
		insertBook(ISBN_A, "5.00", "ES");
		insertBook(ISBN_B, "30.00", "ES");
		insertBook(ISBN_C, "10.00", "ES");

		mvc.perform(get("/books?sort=cost_usd,desc")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].cost_usd").value(30.0))
				.andExpect(jsonPath("$.content[1].cost_usd").value(10.0))
				.andExpect(jsonPath("$.content[2].cost_usd").value(5.0)).andExpect(jsonPath("$.total_elements").value(3));
	}

	// ---- PUT / DELETE ----

	@Test
	void putReplacesTheBookAndChangesUpdatedAt() throws Exception {
		long id = insertBook(ISBN_A, "10.00", "ES");
		OffsetDateTime before = updatedAt(id);

		mvc.perform(put("/books/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(bookJson(ISBN_A, "10.00", "77", "ES"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.stock_quantity").value(77));

		assertThat(updatedAt(id)).isAfter(before);
	}

	@Test
	void putWithAnotherBooksIsbnIsA409() throws Exception {
		long id = insertBook(ISBN_A, "10.00", "ES");
		insertBook(ISBN_B, "10.00", "ES");

		assertFieldError(assertApiError(mvc.perform(put("/books/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(bookJson(ISBN_B, "10.00", "5", "ES"))), 409, "/books/" + id), "isbn");
	}

	@Test
	void putOnAMissingBookIsA404() throws Exception {
		assertApiError(mvc.perform(put("/books/999999").contentType(MediaType.APPLICATION_JSON)
				.content(bookJson(ISBN_A, "10.00", "5", "ES"))), 404, "/books/999999");
	}

	@Test
	void deleteIs204AndRepeatingItIs404() throws Exception {
		long id = insertBook(ISBN_A, "10.00", "ES");

		mvc.perform(delete("/books/" + id)).andExpect(status().isNoContent());
		assertApiError(mvc.perform(delete("/books/" + id)), 404, "/books/" + id);
	}

}
