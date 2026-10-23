package com.nextep.bookstore.exchangerate;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("exchange-rate")
public record ExchangeRateProperties(Api api, Refresh refresh,
		@DefaultValue("PT15M") Duration cacheTtl,
		@DefaultValue("PT1M") Duration failureBackoff) {

	public record Api(
			@DefaultValue("https://api.exchangerate-api.com/v4/latest/USD") String url,
			@DefaultValue("3s") Duration connectTimeout,
			@DefaultValue("4s") Duration readTimeout) {
	}

	public record Refresh(
			@DefaultValue("true") boolean enabled,
			@DefaultValue("true") boolean onStartup,
			@DefaultValue("0 0 3 * * *") String cron,
			@DefaultValue("America/Caracas") String zone) {
	}

}
