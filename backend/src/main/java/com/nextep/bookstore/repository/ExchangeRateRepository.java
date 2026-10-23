package com.nextep.bookstore.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nextep.bookstore.model.StoredExchangeRate;

// Acceso a exchange_rates con JdbcTemplate: el upsert ON CONFLICT es propio de PostgreSQL y no hace falta una entidad JPA.
@Repository
public class ExchangeRateRepository {

	private static final int BATCH_SIZE = 200;

	private static final String UPSERT = """
			INSERT INTO exchange_rates (currency, rate, rate_date, fetched_at) VALUES (?, ?, ?, ?)
			ON CONFLICT (currency) DO UPDATE
			SET rate = EXCLUDED.rate, rate_date = EXCLUDED.rate_date, fetched_at = EXCLUDED.fetched_at
			""";

	private static final String FIND_BY_CURRENCY = """
			SELECT currency, rate, rate_date, fetched_at FROM exchange_rates WHERE currency = ?
			""";

	private final JdbcTemplate jdbc;

	public ExchangeRateRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// Upsert por lotes; la transacción la abre quien llama.
	public void upsertAll(Map<String, BigDecimal> rates, LocalDate rateDate, Instant fetchedAt) {
		Date date = Date.valueOf(rateDate);
		Timestamp timestamp = Timestamp.from(fetchedAt);
		jdbc.batchUpdate(UPSERT, List.copyOf(rates.entrySet()), BATCH_SIZE, (ps, entry) -> {
			ps.setString(1, entry.getKey());
			ps.setBigDecimal(2, entry.getValue());
			ps.setDate(3, date);
			ps.setTimestamp(4, timestamp);
		});
	}

	// Última tasa guardada (USD -> moneda) de una moneda, si existe.
	public Optional<StoredExchangeRate> findByCurrency(String currency) {
		if (currency == null) {
			return Optional.empty();
		}
		return jdbc.query(FIND_BY_CURRENCY,
				(rs, i) -> new StoredExchangeRate(rs.getString("currency"), rs.getBigDecimal("rate"),
						rs.getDate("rate_date").toLocalDate(), rs.getObject("fetched_at", OffsetDateTime.class)),
				currency.trim().toUpperCase(Locale.ROOT)).stream().findFirst();
	}

}
