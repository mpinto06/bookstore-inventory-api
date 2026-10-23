package com.nextep.bookstore.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("pricing")
public record PricingProperties(@DefaultValue("40") BigDecimal marginPercentage) {
}
