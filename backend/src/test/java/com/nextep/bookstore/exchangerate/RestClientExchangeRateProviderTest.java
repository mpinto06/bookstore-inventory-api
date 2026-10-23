package com.nextep.bookstore.exchangerate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class RestClientExchangeRateProviderTest {

	private HttpServer server;

	private final AtomicReference<Integer> status = new AtomicReference<>(200);

	private final AtomicReference<String> body = new AtomicReference<>();

	private final AtomicReference<Long> delayMillis = new AtomicReference<>(0L);

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			try {
				Thread.sleep(delayMillis.get());
				byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(status.get(), bytes.length);
				exchange.getResponseBody().write(bytes);
			}
			catch (InterruptedException | IOException ignored) {
				// el cliente cortó por timeout
			}
			finally {
				exchange.close();
			}
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private RestClientExchangeRateProvider provider(Duration readTimeout) {
		String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
		return new RestClientExchangeRateProvider(new ExchangeRateProperties(
				new ExchangeRateProperties.Api(url, Duration.ofSeconds(2), readTimeout),
				new ExchangeRateProperties.Refresh(true, true, "0 0 3 * * *", "America/Caracas"),
				Duration.ofMinutes(15), Duration.ofMinutes(1)));
	}

	@Test
	void parsesValidResponseKeepingExactDecimalsAndIgnoringExtraFields() {
		body.set("""
				{"provider":"x","WARNING_UPGRADE_TO_V6":"y","terms":"z","base":"USD","date":"2026-10-22",
				 "time_last_updated":1792627201,"rates":{"USD":1,"VES":884.75,"BHD":0.376}}""");

		ExchangeRateSnapshot snapshot = provider(Duration.ofSeconds(3)).fetchLatest();

		assertThat(snapshot.date()).isEqualTo(LocalDate.of(2026, 10, 22));
		assertThat(snapshot.rates()).containsEntry("VES", new BigDecimal("884.75"))
				.containsEntry("BHD", new BigDecimal("0.376")).hasSize(3);
	}

	@Test
	void rejectsInvalidResponses() {
		RestClientExchangeRateProvider provider = provider(Duration.ofSeconds(3));
		String[] invalidBodies = {
				"<html>Maintenance</html>",
				"{\"base\":\"USD\",\"rates\":{\"EUR\":",
				"{\"base\":\"USD\",\"date\":\"2026-10-22\",\"rates\":{}}",
				"{\"base\":\"EUR\",\"date\":\"2026-10-22\",\"rates\":{\"USD\":1.1}}",
				"{\"base\":\"USD\",\"date\":\"22/10/2026\",\"rates\":{\"EUR\":0.9}}",
				"{\"base\":\"USD\",\"rates\":{\"EUR\":0.9}}" };
		for (String invalid : invalidBodies) {
			body.set(invalid);
			assertThatThrownBy(provider::fetchLatest).as(invalid).isInstanceOf(ExchangeRateUnavailableException.class);
		}
	}

	@Test
	void rejectsNon2xxStatus() {
		status.set(500);
		body.set("{\"base\":\"USD\",\"date\":\"2026-10-22\",\"rates\":{\"EUR\":0.9}}");

		assertThatThrownBy(() -> provider(Duration.ofSeconds(3)).fetchLatest())
				.isInstanceOf(ExchangeRateUnavailableException.class);
	}

	@Test
	void failsWithinReadTimeout() {
		body.set("{\"base\":\"USD\",\"date\":\"2026-10-22\",\"rates\":{\"EUR\":0.9}}");
		delayMillis.set(2_000L);
		long start = System.nanoTime();

		assertThatThrownBy(() -> provider(Duration.ofMillis(500)).fetchLatest())
				.isInstanceOf(ExchangeRateUnavailableException.class);

		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
	}

}
