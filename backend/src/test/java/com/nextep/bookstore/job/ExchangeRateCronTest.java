package com.nextep.bookstore.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.support.CronExpression;

// Lee los valores reales de application.properties (sin variables de entorno del sistema), de modo que
// el test falla si alguien cambia el cron o la zona por defecto.
class ExchangeRateCronTest {

	@Test
	void defaultCronRunsAt0300CaracasWhichIs0700Utc() throws IOException {
		Properties properties = PropertiesLoaderUtils.loadAllProperties("application.properties");
		MockEnvironment environment = new MockEnvironment();
		properties.forEach((key, value) -> environment.setProperty((String) key, (String) value));
		String cron = environment.resolveRequiredPlaceholders("${exchange-rate.refresh.cron}");
		String zone = environment.resolveRequiredPlaceholders("${exchange-rate.refresh.zone}");

		ZonedDateTime from = ZonedDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneId.of(zone));
		ZonedDateTime next = CronExpression.parse(cron).next(from);

		assertThat(zone).isEqualTo("America/Caracas");
		assertThat(next.toLocalDateTime().toString()).isEqualTo("2026-10-23T03:00");
		assertThat(next.getOffset().getId()).isEqualTo("-04:00");
		assertThat(next.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime().toString())
				.isEqualTo("2026-10-23T07:00");
	}

}
