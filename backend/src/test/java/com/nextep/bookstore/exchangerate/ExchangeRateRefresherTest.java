package com.nextep.bookstore.exchangerate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ExchangeRateRefresherTest {

	private final ExchangeRateService service = mock(ExchangeRateService.class);

	private ExchangeRateRefresher refresher(boolean onStartup) {
		return new ExchangeRateRefresher(service, new ExchangeRateProperties(
				new ExchangeRateProperties.Api("http://unused", Duration.ofSeconds(2), Duration.ofSeconds(3)),
				new ExchangeRateProperties.Refresh(true, onStartup, "0 0 3 * * *", "America/Caracas"),
				Duration.ofMinutes(15), Duration.ofMinutes(1)));
	}

	@Test
	void startupRefreshNeverPropagatesFailures() {
		when(service.refreshAll()).thenThrow(new IllegalStateException("fallo inesperado"));

		assertThatCode(() -> refresher(true).refreshOnStartup()).doesNotThrowAnyException();

		verify(service, times(1)).refreshAll();
	}

	@Test
	void startupRefreshIsSkippedWhenOnStartupIsFalse() {
		refresher(false).refreshOnStartup();

		verify(service, never()).refreshAll();
	}

	@Test
	void scheduledRefreshCallsRefreshAllAndNeverPropagatesFailures() {
		when(service.refreshAll()).thenThrow(new ExchangeRateUnavailableException("API caída"));

		assertThatCode(() -> refresher(false).refreshScheduled()).doesNotThrowAnyException();

		verify(service, times(1)).refreshAll();
	}

}
