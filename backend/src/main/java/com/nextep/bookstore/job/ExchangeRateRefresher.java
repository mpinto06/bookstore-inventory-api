package com.nextep.bookstore.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nextep.bookstore.config.ExchangeRateProperties;
import com.nextep.bookstore.service.ExchangeRateService;

// Refresca las tasas al arrancar y cada día (por defecto 03:00 America/Caracas). Si falla, no se
// propaga: la app sigue con las tasas que ya hay en la BD (el snapshot de V2 o el último refresco).
// El upsert es idempotente, así que varias instancias no se pisarían; igual se asume una sola
// (no hay bloqueo distribuido).
@Component
@ConditionalOnProperty(name = "exchange-rate.refresh.enabled", havingValue = "true", matchIfMissing = true)
class ExchangeRateRefresher {

	private static final Logger log = LoggerFactory.getLogger(ExchangeRateRefresher.class);

	private final ExchangeRateService service;

	private final ExchangeRateProperties properties;

	ExchangeRateRefresher(ExchangeRateService service, ExchangeRateProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	// ApplicationReadyEvent ocurre tras Flyway, así que el seed de V2 ya existe.
	@EventListener(ApplicationReadyEvent.class)
	void refreshOnStartup() {
		if (properties.refresh().onStartup()) {
			refreshSafely("arranque");
		}
	}

	@Scheduled(cron = "${exchange-rate.refresh.cron}", zone = "${exchange-rate.refresh.zone}")
	void refreshScheduled() {
		refreshSafely("programado");
	}

	private void refreshSafely(String trigger) {
		try {
			service.refreshAll();
		}
		catch (Exception ex) {
			log.warn("Refresco de tasas ({}) falló, se mantienen las tasas guardadas: {}", trigger, ex.toString());
		}
	}

}
