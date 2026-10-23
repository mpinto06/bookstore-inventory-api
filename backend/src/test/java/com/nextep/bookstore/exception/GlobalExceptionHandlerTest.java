package com.nextep.bookstore.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.nextep.bookstore.dto.ApiError;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/books"));

	@Test
	void uniqueIsbnViolationFromRaceIsA409() {
		var cause = new SQLException(
				"ERROR: duplicate key value violates unique constraint \"uq_books_isbn\"");
		ResponseEntity<Object> response = handler.handleDataIntegrity(
				new DataIntegrityViolationException("could not execute statement", cause), request);

		assertEquals(409, response.getStatusCode().value());
		ApiError body = (ApiError) response.getBody();
		assertEquals("isbn", body.fieldErrors().get(0).field());
		assertEquals("/books", body.path());
	}

	@Test
	void otherIntegrityViolationIsAGeneric500WithoutLeakingDetails() {
		var cause = new SQLException("ERROR: new row violates check constraint \"chk_secret_internal_name\"");
		ResponseEntity<Object> response = handler.handleDataIntegrity(
				new DataIntegrityViolationException("could not execute statement", cause), request);

		assertEquals(500, response.getStatusCode().value());
		ApiError body = (ApiError) response.getBody();
		assertEquals("Error interno del servidor", body.message());
		assertFalse(body.message().contains("chk_secret_internal_name"));
		assertNull(body.fieldErrors());
	}

	@Test
	void unexpectedExceptionIsAGeneric500() {
		ResponseEntity<Object> response = handler.handleUnexpected(new IllegalStateException("detalle interno"),
				request);

		assertEquals(500, response.getStatusCode().value());
		assertEquals("Error interno del servidor", ((ApiError) response.getBody()).message());
	}

}
