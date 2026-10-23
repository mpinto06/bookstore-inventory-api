package com.nextep.bookstore.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.nextep.bookstore.model.StoredExchangeRate;
import com.nextep.bookstore.util.AbstractIntegrationTest;

class ExchangeRateRepositoryIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ExchangeRateRepository repository;

	@Test
	void upsertAllInsertsNewCurrenciesAndOverwritesExistingOnes() {
		Instant fetchedAt = Instant.parse("2026-10-23T07:00:00Z");

		repository.upsertAll(Map.of("EUR", new BigDecimal("0.85"), "QQQ", new BigDecimal("1.5")),
				LocalDate.of(2026, 10, 23), fetchedAt);

		StoredExchangeRate eur = repository.findByCurrency("EUR").orElseThrow();
		assertThat(eur.rate()).isEqualByComparingTo("0.85");
		assertThat(eur.rateDate()).isEqualTo(LocalDate.of(2026, 10, 23));
		assertThat(eur.fetchedAt().toInstant()).isEqualTo(fetchedAt);
		assertThat(repository.findByCurrency("QQQ")).isPresent();
		assertThat(jdbc.queryForObject("select count(*) from exchange_rates where currency = 'EUR'", Long.class))
				.isEqualTo(1);
		jdbc.update("delete from exchange_rates where currency = 'QQQ'");
	}

	@Test
	void findByCurrencyNormalizesTheCodeAndIsEmptyForNullOrUnknown() {
		assertThat(repository.findByCurrency(" eur ")).map(StoredExchangeRate::currency).contains("EUR");
		assertThat(repository.findByCurrency("ZZZ")).isEmpty();
		assertThat(repository.findByCurrency(null)).isEmpty();
	}

}
