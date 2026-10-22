package com.nextep.bookstore.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.nextep.bookstore.error.BadRequestException;

class BookPagingTest {

	// Evita que la lista blanca se desincronice de la entidad: PageRequest no valida que la propiedad exista,
	// así que un renombre en Book solo fallaría (500) al ejecutar la consulta.
	@Test
	void everySortableValueIsARealPropertyOfBook() throws NoSuchFieldException {
		for (var entry : BookPaging.SORTABLE.entrySet()) {
			assertNotNull(Book.class.getDeclaredField(entry.getValue()),
					entry.getKey() + " apunta a una propiedad que no existe en Book: " + entry.getValue());
		}
	}

	@Test
	void everySortableKeyProducesThatPropertyAsFirstOrder() {
		for (var entry : BookPaging.SORTABLE.entrySet()) {
			Pageable pageable = BookPaging.pageable(0, 20, entry.getKey());
			assertEquals(entry.getValue(), pageable.getSort().iterator().next().getProperty(), entry.getKey());
		}
	}

	@Test
	void directionDefaultsToAscAndAcceptsAnyCaseAndSpaces() {
		assertEquals(Sort.Direction.ASC, firstOrder("title").getDirection());
		assertEquals(Sort.Direction.ASC, firstOrder("title,asc").getDirection());
		assertEquals(Sort.Direction.DESC, firstOrder("cost_usd,desc").getDirection());
		assertEquals(Sort.Direction.DESC, firstOrder(" cost_usd , DESC ").getDirection());
		assertEquals("costUsd", firstOrder("cost_usd,desc").getProperty());
	}

	@Test
	void sortingByAnotherFieldAddsIdAsTiebreaker() {
		List<Sort.Order> orders = BookPaging.pageable(0, 20, "category,desc").getSort().toList();
		assertEquals(List.of(Sort.Order.desc("category"), Sort.Order.asc("id")), orders);
	}

	@Test
	void sortingByIdHasNoExtraTiebreaker() {
		assertEquals(List.of(Sort.Order.desc("id")), BookPaging.pageable(0, 20, "id,desc").getSort().toList());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void absentSortUsesTheDefault(String sort) {
		assertEquals(Sort.by("id"), BookPaging.pageable(0, 20, sort).getSort());
		Sort custom = Sort.by("stockQuantity", "id");
		assertEquals(custom, BookPaging.pageable(0, 20, sort, custom).getSort());
	}

	@Test
	void explicitSortOverridesTheDefault() {
		Sort custom = Sort.by("stockQuantity", "id");
		assertEquals("title", BookPaging.pageable(0, 20, "title", custom).getSort().iterator().next().getProperty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "inexistente", "costUsd", "COST_USD", "Title", "title,sideways", "title,asc,id",
			"title,", ",asc", ",", "title;drop", "id,asc,desc", "password" })
	void invalidSortIsABadRequestOnTheSortField(String sort) {
		BadRequestException ex = assertThrows(BadRequestException.class, () -> BookPaging.pageable(0, 20, sort));
		assertEquals("sort", ex.getField());
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, Integer.MIN_VALUE })
	void negativePageIsRejected(int page) {
		assertEquals("page", assertThrows(BadRequestException.class, () -> BookPaging.pageable(page, 20, null))
				.getField());
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 101, Integer.MAX_VALUE, Integer.MIN_VALUE })
	void sizeOutsideOneToHundredIsRejected(int size) {
		assertEquals("size", assertThrows(BadRequestException.class, () -> BookPaging.pageable(0, size, null))
				.getField());
	}

	@Test
	void sizeLimitsAreInclusive() {
		assertEquals(1, BookPaging.pageable(0, 1, null).getPageSize());
		assertEquals(100, BookPaging.pageable(0, 100, null).getPageSize());
	}

	@Test
	void offsetBeyondIntegerMaxIsRejectedButTheLargestValidOneIsAccepted() {
		assertEquals("page", assertThrows(BadRequestException.class,
				() -> BookPaging.pageable(Integer.MAX_VALUE, 100, null)).getField());
		// 21474836 * 100 = 2147483600 <= Integer.MAX_VALUE
		assertEquals(2_147_483_600L, BookPaging.pageable(21_474_836, 100, null).getOffset());
	}

	private static Sort.Order firstOrder(String sort) {
		return BookPaging.pageable(0, 20, sort).getSort().iterator().next();
	}

}
