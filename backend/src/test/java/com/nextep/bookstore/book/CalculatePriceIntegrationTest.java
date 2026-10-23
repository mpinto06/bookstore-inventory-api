package com.nextep.bookstore.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import com.nextep.bookstore.AbstractIntegrationTest;
import com.nextep.bookstore.exchangerate.ExchangeRateSnapshot;
import com.nextep.bookstore.exchangerate.ExchangeRateUnavailableException;

class CalculatePriceIntegrationTest extends AbstractIntegrationTest {

	private void apiReturnsEur085() {
		when(exchangeRateProvider.fetchLatest())
				.thenReturn(new ExchangeRateSnapshot(LocalDate.of(2026, 10, 23), Map.of("EUR", new BigDecimal("0.85"))));
	}

	private void apiIsDown() {
		when(exchangeRateProvider.fetchLatest()).thenThrow(new ExchangeRateUnavailableException("API caída"));
	}

	@Test
	void liveRateCalculatesAndStoresThePrice() throws Exception {
		apiReturnsEur085();
		long id = insertBook(ISBN_A, "15.99", "ES");

		mvc.perform(post("/books/" + id + "/calculate-price")).andExpect(status().isOk())
				.andExpect(jsonPath("$.book_id").value(id)).andExpect(jsonPath("$.cost_usd").value(15.99))
				.andExpect(jsonPath("$.exchange_rate").value(0.85)).andExpect(jsonPath("$.cost_local").value(13.59))
				.andExpect(jsonPath("$.margin_percentage").value(40))
				.andExpect(jsonPath("$.selling_price_local").value(19.03)).andExpect(jsonPath("$.currency").value("EUR"))
				.andExpect(jsonPath("$.calculation_timestamp").isNotEmpty())
				.andExpect(jsonPath("$.rate_source").value("LIVE")).andExpect(jsonPath("$.rate_date").value("2026-10-23"));

		mvc.perform(get("/books/" + id)).andExpect(jsonPath("$.selling_price_local").value(19.03))
				.andExpect(jsonPath("$.selling_currency").value("EUR"));
	}

	@Test
	void apiFailureFallsBackToTheStoredRate() throws Exception {
		apiIsDown();
		long id = insertBook(ISBN_A, "10.00", "ES");

		mvc.perform(post("/books/" + id + "/calculate-price")).andExpect(status().isOk())
				.andExpect(jsonPath("$.rate_source").value("FALLBACK")).andExpect(jsonPath("$.rate_date").value("2026-10-20"))
				.andExpect(jsonPath("$.exchange_rate").value(0.9)).andExpect(jsonPath("$.cost_local").value(9.0))
				.andExpect(jsonPath("$.selling_price_local").value(12.6));
	}

	@Test
	void apiDownAndNoStoredRateIs503() throws Exception {
		apiIsDown();
		jdbc.update("delete from exchange_rates where currency = 'EUR'");
		long id = insertBook(ISBN_A, "10.00", "ES");

		assertApiError(mvc.perform(post("/books/" + id + "/calculate-price")), 503, "/books/" + id + "/calculate-price")
				.andExpect(jsonPath("$.message", containsString("EUR")));
	}

	@Test
	void countryWithoutCurrencyIs422() throws Exception {
		long id = insertBook(ISBN_A, "10.00", "AQ");

		assertApiError(mvc.perform(post("/books/" + id + "/calculate-price")), 422, "/books/" + id + "/calculate-price");
	}

	@Test
	void missingBookIs404() throws Exception {
		assertApiError(mvc.perform(post("/books/999999/calculate-price")), 404, "/books/999999/calculate-price");
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc" })
	void invalidIdIs400(String id) throws Exception {
		assertApiError(mvc.perform(post("/books/" + id + "/calculate-price")), 400, "/books/" + id + "/calculate-price");
	}

	@Test
	void changingTheCostViaPutClearsThePriceAndItCanBeRecalculated() throws Exception {
		apiReturnsEur085();
		long id = insertBook(ISBN_A, "15.99", "ES");
		mvc.perform(post("/books/" + id + "/calculate-price")).andExpect(status().isOk());

		mvc.perform(put("/books/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(bookJson(ISBN_A, "20.00", "5", "ES"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.selling_price_local").value(nullValue()))
				.andExpect(jsonPath("$.selling_currency").value(nullValue()));

		mvc.perform(post("/books/" + id + "/calculate-price")).andExpect(status().isOk())
				.andExpect(jsonPath("$.cost_local").value(17.0)).andExpect(jsonPath("$.selling_price_local").value(23.8));
	}

	@Test
	void recalculatingTheSamePriceLeavesUpdatedAtUntouched() throws Exception {
		apiReturnsEur085();
		long id = insertBook(ISBN_A, "15.99", "ES");
		mvc.perform(post("/books/" + id + "/calculate-price")).andExpect(status().isOk());
		OffsetDateTime afterFirst = updatedAt(id);

		mvc.perform(post("/books/" + id + "/calculate-price")).andExpect(status().isOk());

		// nada cambió en el libro, así que no hay UPDATE y el trigger no toca updated_at
		assertThat(updatedAt(id)).isEqualTo(afterFirst);
	}

	@Test
	void calculatingPricesNeverWritesToExchangeRates() throws Exception {
		long ids = insertBook(ISBN_A, "15.99", "ES");
		long aq = insertBook(ISBN_B, "10.00", "AQ");
		long countBefore = jdbc.queryForObject("select count(*) from exchange_rates", Long.class);
		OffsetDateTime maxBefore = jdbc.queryForObject("select max(fetched_at) from exchange_rates", OffsetDateTime.class);

		apiReturnsEur085();
		mvc.perform(post("/books/" + ids + "/calculate-price")).andExpect(status().isOk());
		mvc.perform(post("/books/" + ids + "/calculate-price")).andExpect(status().isOk());
		clock.advance(Duration.ofHours(1));
		apiIsDown();
		mvc.perform(post("/books/" + ids + "/calculate-price")).andExpect(status().isOk());
		mvc.perform(post("/books/" + aq + "/calculate-price")).andExpect(status().isUnprocessableContent());

		assertThat(jdbc.queryForObject("select count(*) from exchange_rates", Long.class)).isEqualTo(countBefore);
		assertThat(jdbc.queryForObject("select max(fetched_at) from exchange_rates", OffsetDateTime.class))
				.isEqualTo(maxBefore);
	}

}
