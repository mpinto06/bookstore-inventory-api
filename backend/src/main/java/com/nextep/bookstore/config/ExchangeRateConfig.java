package com.nextep.bookstore.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class ExchangeRateConfig {

	// Se inyecta para poder controlar en los tests el TTL de la caché y la pausa tras un fallo
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
