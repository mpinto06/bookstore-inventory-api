package com.nextep.bookstore.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.nextep.bookstore.PostgresTestConfiguration;

@SpringBootTest(properties = "exchange-rate.refresh.enabled=false")
@Import(PostgresTestConfiguration.class)
class BookTimestampsTest {

	@Autowired
	BookRepository repository;

	@Autowired
	TransactionTemplate transaction;

	@Test
	void timestampsAreAssignedByTheDatabase() throws InterruptedException {
		Instant before = Instant.now().minusSeconds(1);
		Book saved = repository.saveAndFlush(new Book("Dune", "Frank Herbert", "9780441172719",
				new BigDecimal("10.00"), null, null, 5, "Sci-Fi", "US"));

		assertThat(saved.getCreatedAt()).isAfter(before);
		assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
		Instant createdAt = saved.getCreatedAt();
		Instant firstUpdate = saved.getUpdatedAt();

		Thread.sleep(20);
		Book updated = transaction.execute(status -> {
			Book book = repository.findById(saved.getId()).orElseThrow();
			book.setStockQuantity(9);
			return repository.saveAndFlush(book);
		});

		assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
		assertThat(updated.getUpdatedAt()).isAfter(firstUpdate);
	}

}
