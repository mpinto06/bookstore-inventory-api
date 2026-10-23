package com.nextep.bookstore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nextep.bookstore.client.ExchangeRateProvider;
import com.nextep.bookstore.client.ExchangeRateSnapshot;
import com.nextep.bookstore.config.ExchangeRateProperties;
import com.nextep.bookstore.enums.RateSource;
import com.nextep.bookstore.exception.ExchangeRateUnavailableException;
import com.nextep.bookstore.model.StoredExchangeRate;
import com.nextep.bookstore.repository.ExchangeRateRepository;
import com.nextep.bookstore.util.MutableClock;

class ExchangeRateServiceTest {

	private static final LocalDate API_DATE = LocalDate.of(2026, 10, 23);

	private static final LocalDate STORED_DATE = LocalDate.of(2026, 10, 20);

	private final ExchangeRateProvider provider = mock(ExchangeRateProvider.class);

	private final ExchangeRateRepository repository = mock(ExchangeRateRepository.class);

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));

	private ExchangeRateService service;

	@BeforeEach
	void setUp() {
		ExchangeRateProperties properties = new ExchangeRateProperties(
				new ExchangeRateProperties.Api("http://unused", Duration.ofSeconds(2), Duration.ofSeconds(3)),
				new ExchangeRateProperties.Refresh(false, false, "0 0 3 * * *", "America/Caracas"),
				Duration.ofMinutes(15), Duration.ofMinutes(1));
		service = new ExchangeRateService(provider, repository, properties, clock);
	}

	private void apiReturns(Map<String, BigDecimal> rates) {
		when(provider.fetchLatest()).thenReturn(new ExchangeRateSnapshot(API_DATE, rates));
	}

	private void apiFails() {
		when(provider.fetchLatest()).thenThrow(new ExchangeRateUnavailableException("API caída"));
	}

	private void tableHas(String currency, String rate) {
		StoredExchangeRate stored = new StoredExchangeRate(currency, new BigDecimal(rate), STORED_DATE,
				OffsetDateTime.parse("2026-10-20T03:00:00Z"));
		when(repository.findByCurrency(currency)).thenReturn(Optional.of(stored));
	}

	// ---- fetchLatest ----

	@Test
	void fetchLatestDiscardsInvalidRatesWithoutInvalidatingTheRest() {
		Map<String, BigDecimal> rates = new LinkedHashMap<>();
		rates.put("EUR", new BigDecimal("0.85"));
		rates.put("MAX", new BigDecimal("1000000000"));
		rates.put("ZER", BigDecimal.ZERO);
		rates.put("NEG", new BigDecimal("-1.5"));
		rates.put("BIG", new BigDecimal("1000000000.01"));
		rates.put("TIN", new BigDecimal("0.00000000001"));
		rates.put("eur", new BigDecimal("1"));
		rates.put("EURO", new BigDecimal("1"));
		rates.put("NUL", null);
		apiReturns(rates);

		ExchangeRateSnapshot snapshot = service.fetchLatest();

		assertThat(snapshot.date()).isEqualTo(API_DATE);
		assertThat(snapshot.rates()).containsOnlyKeys("EUR", "MAX");
	}

	@Test
	void fetchLatestFailsWhenNoRateIsValid() {
		apiReturns(Map.of("ZER", BigDecimal.ZERO, "NEG", new BigDecimal("-1")));

		assertThatThrownBy(service::fetchLatest).isInstanceOf(ExchangeRateUnavailableException.class);
	}

	// ---- getRate ----

	@Test
	void validCacheDoesNotCallTheProvider() {
		apiReturns(Map.of("EUR", new BigDecimal("0.85")));
		service.fetchLatest();
		clearInvocations(provider);
		clock.advance(Duration.ofMinutes(14));

		ResolvedRate rate = service.getRate("EUR");

		verify(provider, times(0)).fetchLatest();
		assertThat(rate.source()).isEqualTo(RateSource.LIVE);
		assertThat(rate.rate()).isEqualByComparingTo("0.85");
		assertThat(rate.rateDate()).isEqualTo(API_DATE);
	}

	@Test
	void expiredCacheCallsTheProviderOnceAndRenews() {
		apiReturns(Map.of("EUR", new BigDecimal("0.85")));
		service.fetchLatest();
		clock.advance(Duration.ofMinutes(16));
		apiReturns(Map.of("EUR", new BigDecimal("0.90")));
		clearInvocations(provider);

		ResolvedRate first = service.getRate("EUR");
		ResolvedRate second = service.getRate("EUR");

		verify(provider, times(1)).fetchLatest();
		assertThat(first.source()).isEqualTo(RateSource.LIVE);
		assertThat(first.rate()).isEqualByComparingTo("0.90");
		assertThat(second.rate()).isEqualByComparingTo("0.90");
	}

	@Test
	void providerFailureFallsBackToTheStoredRateWithItsOwnDate() {
		apiFails();
		tableHas("EUR", "0.9000000000");

		ResolvedRate rate = service.getRate("EUR");

		assertThat(rate.source()).isEqualTo(RateSource.FALLBACK);
		assertThat(rate.rate()).isEqualTo(new BigDecimal("0.9"));
		assertThat(rate.rateDate()).isEqualTo(STORED_DATE);
	}

	@Test
	void afterAFailureTheApiIsNotCalledUntilTheBackoffEndsAndThenRetriedOnce() {
		apiFails();
		tableHas("EUR", "0.9");
		service.getRate("EUR");
		verify(provider, times(1)).fetchLatest();

		clock.advance(Duration.ofSeconds(30));
		ResolvedRate duringPause = service.getRate("EUR");
		service.getRate("EUR");

		assertThat(duringPause.source()).isEqualTo(RateSource.FALLBACK);
		verify(provider, times(1)).fetchLatest();

		clock.advance(Duration.ofSeconds(31));
		service.getRate("EUR");
		service.getRate("EUR");

		verify(provider, times(2)).fetchLatest();
	}

	@Test
	void unknownCurrencyWithApiDownIsUnavailable() {
		apiFails();
		when(repository.findByCurrency("EUR")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getRate("EUR")).isInstanceOf(ExchangeRateUnavailableException.class)
				.hasMessageContaining("EUR");
	}

	@Test
	void currencyMissingFromTheApiButStoredFallsBack() {
		apiReturns(Map.of("EUR", new BigDecimal("0.85")));
		tableHas("CLP", "974.77");

		ResolvedRate rate = service.getRate("CLP");

		assertThat(rate.source()).isEqualTo(RateSource.FALLBACK);
		assertThat(rate.rate()).isEqualByComparingTo("974.77");
		verify(provider, times(1)).fetchLatest();
		// la pausa también aplica cuando la API respondió sin la moneda
		service.getRate("CLP");
		verify(provider, times(1)).fetchLatest();
	}

	@Test
	void fiftyConcurrentRequestsWithAnExpiredCacheCallTheProviderOnce() throws Exception {
		apiReturns(Map.of("EUR", new BigDecimal("0.85")));
		int threads = 50;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<ResolvedRate>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				results.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return service.getRate("EUR");
				}));
			}
			ready.await();
			start.countDown();
			for (Future<ResolvedRate> result : results) {
				assertThat(result.get().source()).isEqualTo(RateSource.LIVE);
			}
		}
		finally {
			pool.shutdownNow();
		}

		verify(provider, times(1)).fetchLatest();
	}

}
