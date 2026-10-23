package com.nextep.bookstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

// Comportamiento propio de PostgreSQL (trigger, CHECK, NUMERIC): por eso se prueba contra un Postgres real.
class DatabaseIntegrationTest extends AbstractIntegrationTest {

	private static final String INSERT = """
			insert into books (title, author, isbn, cost_usd, selling_price_local, selling_currency, stock_quantity,
			                   category, supplier_country)
			values ('T', 'A', ?, ?, ?, ?, ?, 'C', 'ES')""";

	@Test
	void triggerRefreshesUpdatedAtOnADirectUpdateAndKeepsCreatedAt() {
		long id = insertBook(ISBN_A, "10.00", "ES");
		OffsetDateTime createdAt = jdbc.queryForObject("select created_at from books where id = ?", OffsetDateTime.class, id);
		OffsetDateTime before = updatedAt(id);

		jdbc.update("update books set title = 'otro' where id = ?", id);

		assertThat(updatedAt(id)).isAfter(before);
		assertThat(jdbc.queryForObject("select created_at from books where id = ?", OffsetDateTime.class, id))
				.isEqualTo(createdAt);
	}

	@Test
	void priceAndCurrencyMustBeBothNullOrBothPresent() {
		assertThatThrownBy(() -> jdbc.update(INSERT, ISBN_A, new BigDecimal("10"), new BigDecimal("5.00"), null, 1))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(INSERT, ISBN_B, new BigDecimal("10"), null, "EUR", 1))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.update(INSERT, ISBN_C, new BigDecimal("10"), new BigDecimal("5.00"), "EUR", 1)).isEqualTo(1);
	}

	@Test
	void costMustBePositiveAndStockNonNegative() {
		assertThatThrownBy(() -> jdbc.update(INSERT, ISBN_A, BigDecimal.ZERO, null, null, 1))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(INSERT, ISBN_B, new BigDecimal("10"), null, null, -1))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sellingPriceColumnFitsTheMaximumCostInAHighRateCurrency() {
		jdbc.update(INSERT, ISBN_A, new BigDecimal("99999999.99"), new BigDecimal("123864999987.61"), "VES", 1);

		assertThat(jdbc.queryForObject("select selling_price_local from books where isbn = ?", BigDecimal.class, ISBN_A))
				.isEqualByComparingTo("123864999987.61");
	}

	@Test
	void exchangeRatesRejectNonPositiveRatesAndTheSeedIsLoaded() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into exchange_rates (currency, rate, rate_date, fetched_at) values ('ZZZ', 0, now(), now())"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("select count(*) from exchange_rates", Long.class)).isGreaterThan(100);
	}

}
