package com.nextep.bookstore;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.nextep.bookstore.exchangerate.ExchangeRateProvider;

// Base de los tests de integración: un único contenedor PostgreSQL 18 para toda la ejecución (singleton, se
// arranca una vez y lo recoge Ryuk al terminar la JVM), el mismo contexto de Spring para todas las clases, el
// refresco de tasas desactivado y el proveedor de tasas sustituido por un mock (ningún test llama a internet).
@SpringBootTest(properties = "exchange-rate.refresh.enabled=false")
@AutoConfigureMockMvc
@Import(AbstractIntegrationTest.TestClockConfig.class)
public abstract class AbstractIntegrationTest {

	public static final String ISBN_A = "9780306406157";

	public static final String ISBN_B = "9788437604947";

	public static final String ISBN_C = "9780441172719";

	public static final String ISBN_D = "9780000000019";

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

	static {
		POSTGRES.start();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
		}

	}

	@MockitoBean
	protected ExchangeRateProvider exchangeRateProvider;

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected MutableClock clock;

	// Datos limpios en cada test: se borran los libros y se restauran las tasas que usan los tests; la caché de
	// tasas y la pausa tras un fallo (estado en memoria del servicio) quedan vencidas al avanzar el reloj.
	@BeforeEach
	void resetState() {
		jdbc.update("delete from books");
		restoreRate("EUR", "0.9");
		restoreRate("CLP", "900");
		clock.advance(Duration.ofHours(1));
	}

	protected void restoreRate(String currency, String rate) {
		jdbc.update("""
				insert into exchange_rates (currency, rate, rate_date, fetched_at) values (?, ?, ?, ?)
				on conflict (currency) do update
				set rate = excluded.rate, rate_date = excluded.rate_date, fetched_at = excluded.fetched_at
				""", currency, new BigDecimal(rate), LocalDate.of(2026, 10, 20), OffsetDateTime.parse("2026-10-20T03:00:00Z"));
	}

	protected long insertBook(String title, String isbn, String cost, int stock, String category, String country) {
		return jdbc.queryForObject("""
				insert into books (title, author, isbn, cost_usd, stock_quantity, category, supplier_country)
				values (?, 'Autor', ?, ?, ?, ?, ?) returning id
				""", Long.class, title, isbn, new BigDecimal(cost), stock, category, country);
	}

	protected long insertBook(String isbn, String cost, String country) {
		return insertBook("Titulo " + isbn, isbn, cost, 5, "Categoria", country);
	}

	protected OffsetDateTime updatedAt(long bookId) {
		return jdbc.queryForObject("select updated_at from books where id = ?", OffsetDateTime.class, bookId);
	}

	protected static String bookJson(String isbn, String cost, String stock, String country) {
		return """
				{"title":"Titulo","author":"Autor","isbn":"%s","cost_usd":%s,"stock_quantity":%s,
				 "category":"Categoria","supplier_country":"%s"}""".formatted(isbn, cost, stock, country);
	}

	// Forma de ApiError común a todos los errores
	protected static ResultActions assertApiError(ResultActions result, int status, String path) throws Exception {
		return result.andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.error").value(notNullValue())).andExpect(jsonPath("$.message").value(notNullValue()))
				.andExpect(jsonPath("$.timestamp").value(notNullValue())).andExpect(jsonPath("$.path").value(path));
	}

	protected static ResultActions assertFieldError(ResultActions result, String field) throws Exception {
		return result.andExpect(jsonPath("$.field_errors[*].field").value(hasItem(field)));
	}

}
