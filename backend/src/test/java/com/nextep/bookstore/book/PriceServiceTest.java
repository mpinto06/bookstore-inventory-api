package com.nextep.bookstore.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nextep.bookstore.book.dto.PriceCalculationResponse;
import com.nextep.bookstore.exchangerate.ExchangeRateService;
import com.nextep.bookstore.exchangerate.RateSource;
import com.nextep.bookstore.exchangerate.ResolvedRate;

class PriceServiceTest {

	private final BookRepository repository = mock(BookRepository.class);

	private final ExchangeRateService rates = mock(ExchangeRateService.class);

	private final TransactionTemplate transaction = new TransactionTemplate(mock(PlatformTransactionManager.class));

	private PriceService service(String margin) {
		return new PriceService(repository, rates, new PricingProperties(new BigDecimal(margin)), transaction);
	}

	private void givenBook(String cost, String country, String currency, String rate) {
		Book book = new Book("Titulo", "Autor", "9780306406157", new BigDecimal(cost), null, null, 1, "Cat", country);
		when(repository.findById(1L)).thenReturn(Optional.of(book));
		if (currency != null) {
			when(rates.getRate(currency))
					.thenReturn(new ResolvedRate(new BigDecimal(rate), LocalDate.of(2026, 10, 22), RateSource.LIVE));
		}
	}

	@Test
	void pdfExample() {
		givenBook("15.99", "ES", "EUR", "0.85");

		PriceCalculationResponse response = service("40").calculate(1L);

		assertThat(response.costLocal()).isEqualTo(new BigDecimal("13.59"));
		assertThat(response.sellingPriceLocal()).isEqualTo(new BigDecimal("19.03"));
		assertThat(response.currency()).isEqualTo("EUR");
		assertThat(response.marginPercentage()).isEqualByComparingTo("40");
		assertThat(response.rateSource()).isEqualTo(RateSource.LIVE);
		ArgumentCaptor<Book> saved = ArgumentCaptor.forClass(Book.class);
		verify(repository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getSellingPriceLocal()).isEqualTo(new BigDecimal("19.03"));
		assertThat(saved.getValue().getSellingCurrency()).isEqualTo("EUR");
	}

	// cost, rate, margin, cost_local esperado, precio esperado. Con HALF_UP el 5 final sube; con DOWN o HALF_EVEN no.
	@ParameterizedTest
	@CsvSource({
			"1.00, 0.125, 0, 0.13, 0.13",
			"0.05, 1, 30, 0.05, 0.07" })
	void roundsHalfUpAtTheBoundary(String cost, String rate, String margin, String costLocal, String price) {
		givenBook(cost, "ES", "EUR", rate);

		PriceCalculationResponse response = service(margin).calculate(1L);

		assertThat(response.costLocal()).isEqualTo(new BigDecimal(costLocal));
		assertThat(response.sellingPriceLocal()).isEqualTo(new BigDecimal(price));
	}

	@Test
	void maximumCostWithHighRateFitsNumeric20x2() {
		givenBook("99999999.99", "VE", "VES", "884.75");

		PriceCalculationResponse response = service("40").calculate(1L);

		assertThat(response.costLocal()).isEqualTo(new BigDecimal("88474999991.15"));
		assertThat(response.sellingPriceLocal()).isEqualTo(new BigDecimal("123864999987.61"));
		// NUMERIC(20,2): como máximo 20 dígitos en total
		assertThat(response.sellingPriceLocal().precision()).isLessThanOrEqualTo(20);
	}

	@Test
	void localCurrencyComesFromTheJdkForTheSupplierCountry() {
		givenBook("10.00", "VE", "VES", "884.75");

		assertThat(service("40").calculate(1L).currency())
				.isEqualTo(Currency.getInstance(Locale.of("", "VE")).getCurrencyCode()).isEqualTo("VES");
	}

	@Test
	void countryWithoutCurrencyIsRejectedBeforeAnyRateLookup() {
		givenBook("10.00", "AQ", null, null);

		assertThatThrownBy(() -> service("40").calculate(1L)).isInstanceOf(NoLocalCurrencyException.class);

		verify(rates, never()).getRate(anyString());
		verify(repository, never()).saveAndFlush(any());
	}

}
