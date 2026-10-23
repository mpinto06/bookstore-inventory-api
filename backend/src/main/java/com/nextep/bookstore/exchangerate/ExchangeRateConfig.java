package com.nextep.bookstore.exchangerate;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class ExchangeRateConfig {

	// Inyectable para controlar el TTL de la caché y la pausa tras un fallo en los tests
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
