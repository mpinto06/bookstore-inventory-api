package com.nextep.bookstore.exchangerate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

class ExchangeRateCronTest {

	@Test
	void defaultCronRunsAt0300CaracasWhichIs0700Utc() {
		ExchangeRateProperties.Refresh defaults = new ExchangeRateProperties.Refresh(true, true, "0 0 3 * * *",
				"America/Caracas");
		ZonedDateTime from = ZonedDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneId.of(defaults.zone()));

		ZonedDateTime next = CronExpression.parse(defaults.cron()).next(from);

		assertThat(next.toLocalDateTime().toString()).isEqualTo("2026-10-23T03:00");
		assertThat(next.getOffset().getId()).isEqualTo("-04:00");
		assertThat(next.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime().toString()).isEqualTo("2026-10-23T07:00");
	}

}
