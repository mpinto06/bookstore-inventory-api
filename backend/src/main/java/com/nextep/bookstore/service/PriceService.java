package com.nextep.bookstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.nextep.bookstore.config.PricingProperties;
import com.nextep.bookstore.dto.PriceCalculationResponse;
import com.nextep.bookstore.exception.BadRequestException;
import com.nextep.bookstore.exception.BookModifiedException;
import com.nextep.bookstore.exception.BookNotFoundException;
import com.nextep.bookstore.exception.NoLocalCurrencyException;
import com.nextep.bookstore.model.Book;
import com.nextep.bookstore.repository.BookRepository;

// Sin @Transactional a nivel de clase: la tasa puede requerir una llamada HTTP y no debe haber una
// transacción (ni una conexión de BD) abierta durante ella. Solo la escritura final va en una transacción corta.
@Service
public class PriceService {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final BookRepository repository;

	private final ExchangeRateService exchangeRates;

	private final PricingProperties pricing;

	private final TransactionTemplate transaction;

	public PriceService(BookRepository repository, ExchangeRateService exchangeRates, PricingProperties pricing,
			TransactionTemplate transaction) {
		this.repository = repository;
		this.exchangeRates = exchangeRates;
		this.pricing = pricing;
		this.transaction = transaction;
	}

	public PriceCalculationResponse calculate(Long id) {
		if (id <= 0) {
			throw new BadRequestException("id", "id debe ser un número positivo");
		}
		Book book = repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
		String country = book.getSupplierCountry();
		BigDecimal costUsd = book.getCostUsd();
		String currency = localCurrency(country);

		ResolvedRate rate = exchangeRates.getRate(currency);

		BigDecimal costLocal = round2(costUsd.multiply(rate.rate()));
		BigDecimal multiplier = BigDecimal.ONE.add(pricing.marginPercentage().divide(HUNDRED));
		BigDecimal sellingPrice = round2(costLocal.multiply(multiplier));

		transaction.executeWithoutResult(status -> {
			Book current = repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
			if (current.getCostUsd().compareTo(costUsd) != 0 || !current.getSupplierCountry().equals(country)) {
				throw new BookModifiedException(id);
			}
			current.applyPrice(sellingPrice, currency);
			repository.saveAndFlush(current);
		});

		return new PriceCalculationResponse(id, costUsd, rate.rate(), costLocal, pricing.marginPercentage(),
				sellingPrice, currency, Instant.now(), rate.source(), rate.rateDate());
	}

	private static String localCurrency(String country) {
		try {
			Currency currency = Currency.getInstance(Locale.of("", country));
			if (currency != null) {
				return currency.getCurrencyCode();
			}
		}
		catch (IllegalArgumentException ex) {
			// país que el JDK no reconoce: se trata igual que uno sin moneda
		}
		throw new NoLocalCurrencyException(country);
	}

	private static BigDecimal round2(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

}
