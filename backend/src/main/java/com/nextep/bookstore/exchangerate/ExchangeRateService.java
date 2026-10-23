package com.nextep.bookstore.exchangerate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeRateService {

	private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

	// rate es NUMERIC(20,10); este tope es el mismo que se aplicó al snapshot de V2.
	private static final BigDecimal MAX_RATE = BigDecimal.valueOf(1_000_000_000L);

	private static final int RATE_SCALE = 10;

	private static final int BATCH_SIZE = 200;

	private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

	private static final String UPSERT = """
			INSERT INTO exchange_rates (currency, rate, rate_date, fetched_at) VALUES (?, ?, ?, ?)
			ON CONFLICT (currency) DO UPDATE
			SET rate = EXCLUDED.rate, rate_date = EXCLUDED.rate_date, fetched_at = EXCLUDED.fetched_at
			""";

	private final ExchangeRateProvider provider;

	private final JdbcTemplate jdbc;

	private final ExchangeRateProperties properties;

	// Estado en memoria (sin tabla ni librerías): último resultado de fetchLatest() y último fallo.
	private final ReentrantLock refreshLock = new ReentrantLock();

	private volatile CachedRates cache;

	private volatile Instant lastFailure;

	public ExchangeRateService(ExchangeRateProvider provider, JdbcTemplate jdbc, ExchangeRateProperties properties) {
		this.provider = provider;
		this.jdbc = jdbc;
		this.properties = properties;
	}

	private record CachedRates(ExchangeRateSnapshot snapshot, Instant obtainedAt) {
	}

	// Pide las tasas al proveedor y devuelve la fecha de la API con las tasas válidas. No escribe en la BD.
	// Lanza ExchangeRateUnavailableException si el proveedor falla o no queda ninguna tasa válida.
	public ExchangeRateSnapshot fetchLatest() {
		ExchangeRateSnapshot fetched = provider.fetchLatest();
		Map<String, BigDecimal> valid = new LinkedHashMap<>();
		for (Map.Entry<String, BigDecimal> entry : fetched.rates().entrySet()) {
			if (isValid(entry.getKey(), entry.getValue())) {
				valid.put(entry.getKey(), entry.getValue());
			}
		}
		int discarded = fetched.rates().size() - valid.size();
		if (valid.isEmpty()) {
			throw new ExchangeRateUnavailableException("La API de tasas no devolvió ninguna tasa válida");
		}
		if (discarded > 0) {
			log.warn("Se descartaron {} tasas inválidas de la API de tasas de cambio", discarded);
		}
		ExchangeRateSnapshot snapshot = new ExchangeRateSnapshot(fetched.date(), Map.copyOf(valid));
		this.cache = new CachedRates(snapshot, Instant.now());
		this.lastFailure = null;
		return snapshot;
	}

	// Tasa USD -> moneda para calcular precios. NUNCA escribe en exchange_rates. Orden: caché en memoria
	// (dentro del TTL) -> API (un solo hilo a la vez; tras un fallo, pausa sin llamar a la API) ->
	// última tasa guardada en la tabla (FALLBACK) -> ExchangeRateUnavailableException.
	public ResolvedRate getRate(String currency) {
		Instant now = Instant.now();
		ResolvedRate cached = fromCache(currency, now);
		if (cached != null) {
			return cached;
		}
		if (!inBackoff(now)) {
			refreshLock.lock();
			try {
				// otro hilo pudo refrescar el caché (o fallar) mientras esperábamos el bloqueo
				now = Instant.now();
				cached = fromCache(currency, now);
				if (cached != null) {
					return cached;
				}
				if (!inBackoff(now)) {
					try {
						ExchangeRateSnapshot snapshot = fetchLatest();
						BigDecimal rate = snapshot.rates().get(currency);
						if (rate != null) {
							return new ResolvedRate(rate, snapshot.date(), RateSource.LIVE);
						}
						registerFailure("la API no trae la moneda " + currency);
					}
					catch (ExchangeRateUnavailableException ex) {
						registerFailure(ex.getMessage());
					}
				}
			}
			finally {
				refreshLock.unlock();
			}
		}
		return fallback(currency);
	}

	private ResolvedRate fromCache(String currency, Instant now) {
		CachedRates current = cache;
		if (current == null || Duration.between(current.obtainedAt(), now).compareTo(properties.cacheTtl()) >= 0) {
			return null;
		}
		BigDecimal rate = current.snapshot().rates().get(currency);
		return rate == null ? null : new ResolvedRate(rate, current.snapshot().date(), RateSource.LIVE);
	}

	private boolean inBackoff(Instant now) {
		Instant failure = lastFailure;
		return failure != null && Duration.between(failure, now).compareTo(properties.failureBackoff()) < 0;
	}

	private void registerFailure(String reason) {
		lastFailure = Instant.now();
		log.warn("No se pudo obtener la tasa de la API ({}); se usan las tasas guardadas durante {}", reason,
				properties.failureBackoff());
	}

	private ResolvedRate fallback(String currency) {
		StoredExchangeRate stored = findLatest(currency).orElseThrow(() -> new ExchangeRateNotFoundException(currency));
		// NUMERIC(20,10) devuelve ceros a la derecha; se normaliza sin pasar a notación científica
		BigDecimal rate = stored.rate().stripTrailingZeros();
		if (rate.scale() < 0) {
			rate = rate.setScale(0);
		}
		return new ResolvedRate(rate, stored.rateDate(), RateSource.FALLBACK);
	}

	// Única vía que escribe en exchange_rates (solo la llaman el refresco al arrancar y el programado):
	// upsert por lotes, atómico, de las tasas válidas. Si falla, la excepción se propaga.
	@Transactional
	public ExchangeRateSnapshot refreshAll() {
		ExchangeRateSnapshot latest = fetchLatest();
		Map<String, BigDecimal> valid = latest.rates();
		Timestamp fetchedAt = Timestamp.from(Instant.now());
		Date rateDate = Date.valueOf(latest.date());
		jdbc.batchUpdate(UPSERT, List.copyOf(valid.entrySet()), BATCH_SIZE, (ps, entry) -> {
			ps.setString(1, entry.getKey());
			ps.setBigDecimal(2, entry.getValue());
			ps.setDate(3, rateDate);
			ps.setTimestamp(4, fetchedAt);
		});

		log.info("Tasas de cambio actualizadas: {} monedas, fecha de la API {}", valid.size(), latest.date());
		return latest;
	}

	// Última tasa guardada (USD -> moneda) de una moneda, si existe.
	public Optional<StoredExchangeRate> findLatest(String currency) {
		if (currency == null) {
			return Optional.empty();
		}
		return jdbc.query("SELECT currency, rate, rate_date, fetched_at FROM exchange_rates WHERE currency = ?",
				(rs, i) -> new StoredExchangeRate(rs.getString("currency"), rs.getBigDecimal("rate"),
						rs.getDate("rate_date").toLocalDate(), rs.getObject("fetched_at", OffsetDateTime.class)),
				currency.trim().toUpperCase(Locale.ROOT)).stream().findFirst();
	}

	// Positiva, hasta 1.000.000.000 y que no se redondee a 0 en NUMERIC(20,10) (violaría el CHECK).
	private static boolean isValid(String currency, BigDecimal rate) {
		return currency != null && CURRENCY.matcher(currency).matches() && rate != null && rate.signum() > 0
				&& rate.compareTo(MAX_RATE) <= 0 && rate.setScale(RATE_SCALE, RoundingMode.HALF_UP).signum() > 0;
	}

}
