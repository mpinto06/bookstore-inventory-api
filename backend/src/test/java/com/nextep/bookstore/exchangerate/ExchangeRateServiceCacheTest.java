package com.nextep.bookstore.exchangerate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

// getRate sin BD: solo caminos que no llegan a la tabla de respaldo.
class ExchangeRateServiceCacheTest {

	private final AtomicInteger calls = new AtomicInteger();

	private ExchangeRateService service(Duration ttl) {
		ExchangeRateProvider provider = () -> {
			calls.incrementAndGet();
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return new ExchangeRateSnapshot(LocalDate.of(2026, 10, 22), Map.of("EUR", new BigDecimal("0.85")));
		};
		ExchangeRateProperties props = new ExchangeRateProperties(
				new ExchangeRateProperties.Api("http://unused", Duration.ofSeconds(2), Duration.ofSeconds(3)),
				new ExchangeRateProperties.Refresh(false, false, "0 0 3 * * *", "America/Caracas"), ttl,
				Duration.ofMinutes(1));
		return new ExchangeRateService(provider, Mockito.mock(JdbcTemplate.class), props);
	}

	@Test
	void secondCallWithinTtlUsesCache() {
		ExchangeRateService service = service(Duration.ofMinutes(15));

		ResolvedRate first = service.getRate("EUR");
		ResolvedRate second = service.getRate("EUR");

		assertThat(calls).hasValue(1);
		assertThat(first.source()).isEqualTo(RateSource.LIVE);
		assertThat(second.rate()).isEqualByComparingTo("0.85");
		assertThat(second.rateDate()).isEqualTo(LocalDate.of(2026, 10, 22));
	}

	@Test
	void concurrentRequestsWithExpiredCacheCallTheApiOnce() throws Exception {
		ExchangeRateService service = service(Duration.ofMinutes(15));
		ExecutorService pool = Executors.newFixedThreadPool(20);
		try {
			java.util.List<Future<ResolvedRate>> results = new java.util.ArrayList<>();
			for (int i = 0; i < 50; i++) {
				results.add(pool.submit(() -> service.getRate("EUR")));
			}
			for (Future<ResolvedRate> result : results) {
				assertThat(result.get().source()).isEqualTo(RateSource.LIVE);
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(calls).hasValue(1);
	}

}
