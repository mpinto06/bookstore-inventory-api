package com.nextep.bookstore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "exchange-rate.refresh.enabled=false")
@Import(PostgresTestConfiguration.class)
class BookstoreInventoryApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
