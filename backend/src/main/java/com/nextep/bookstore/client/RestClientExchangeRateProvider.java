package com.nextep.bookstore.client;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextep.bookstore.config.ExchangeRateProperties;
import com.nextep.bookstore.exception.ExchangeRateUnavailableException;

// Cliente de https://api.exchangerate-api.com/v4/latest/USD. Los timeouts se aplican en la factoría
// HTTP del JDK: conexión en el HttpClient y lectura (hasta recibir la respuesta) en la factoría.
@Component
class RestClientExchangeRateProvider implements ExchangeRateProvider {

	private static final String BASE_CURRENCY = "USD";

	private final RestClient restClient;

	private final String url;

	RestClientExchangeRateProvider(ExchangeRateProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.api().connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.api().readTimeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.url = properties.api().url();
	}

	@Override
	public ExchangeRateSnapshot fetchLatest() {
		ApiResponse response;
		try {
			response = restClient.get().uri(url).retrieve().body(ApiResponse.class);
		}
		catch (RuntimeException ex) {
			// Red, timeout, status no 2xx, content-type inesperado o JSON ilegible.
			// Sin el cuerpo de la respuesta (puede ser enorme) en el mensaje.
			String detail = ex instanceof RestClientResponseException responseEx
					? "status " + responseEx.getStatusCode().value() : abbreviate(ex.getMessage());
			throw new ExchangeRateUnavailableException("No se pudo obtener la respuesta de la API de tasas ("
					+ ex.getClass().getSimpleName() + ": " + detail + ")", ex);
		}
		if (response == null) {
			throw new ExchangeRateUnavailableException("La API de tasas devolvió una respuesta vacía");
		}
		if (!BASE_CURRENCY.equals(response.base())) {
			throw new ExchangeRateUnavailableException(
					"La API de tasas devolvió base '" + response.base() + "', se esperaba USD");
		}
		if (response.rates() == null || response.rates().isEmpty()) {
			throw new ExchangeRateUnavailableException("La API de tasas devolvió 'rates' vacío");
		}
		if (response.date() == null) {
			throw new ExchangeRateUnavailableException("La API de tasas no devolvió fecha");
		}
		LocalDate date;
		try {
			date = LocalDate.parse(response.date());
		}
		catch (DateTimeParseException ex) {
			throw new ExchangeRateUnavailableException("La API de tasas devolvió una fecha inválida: " + response.date());
		}
		return new ExchangeRateSnapshot(date, response.rates());
	}

	private static String abbreviate(String message) {
		return message == null || message.length() <= 200 ? message : message.substring(0, 200) + "...";
	}

	// Solo los campos usados; provider, terms, WARNING_UPGRADE_TO_V6, time_last_updated... se ignoran.
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ApiResponse(String base, String date, Map<String, BigDecimal> rates) {
	}

}
