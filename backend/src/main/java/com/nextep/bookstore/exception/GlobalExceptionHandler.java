package com.nextep.bookstore.exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.nextep.bookstore.dto.ApiError;
import com.nextep.bookstore.dto.ApiError.FieldError;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import tools.jackson.databind.exc.MismatchedInputException;

// Traduce toda excepción al formato ApiError. Extiende ResponseEntityExceptionHandler para que las
// excepciones estándar de Spring MVC (404 de ruta, 405, 415...) usen el mismo formato. Los 500 nunca
// filtran detalles internos: el detalle va solo al log.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final String ISBN_UNIQUE_CONSTRAINT = "uq_books_isbn";

	// ---- Excepciones estándar de Spring MVC ----

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		return build(statusCode, defaultMessage(statusCode), null, headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> new FieldError(toSnakeCase(e.getField()), e.getDefaultMessage())).toList();
		return build(status, "La petición contiene datos inválidos", errors, headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = new ArrayList<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			if (result instanceof ParameterErrors parameterErrors) {
				parameterErrors.getFieldErrors()
						.forEach(e -> errors.add(new FieldError(toSnakeCase(e.getField()), e.getDefaultMessage())));
			}
			else {
				String name = result.getMethodParameter().getParameterName();
				for (MessageSourceResolvable error : result.getResolvableErrors()) {
					errors.add(new FieldError(name, error.getDefaultMessage()));
				}
			}
		}
		return build(status, "Parámetros inválidos", errors, headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = null;
		if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
			String name = mismatch.getPath().get(mismatch.getPath().size() - 1).getPropertyName();
			if (name != null) {
				errors = List.of(new FieldError(toSnakeCase(name),
						"El valor tiene un tipo o formato inválido, o está fuera de rango"));
			}
		}
		return build(status, "Cuerpo de la petición vacío, malformado o con valores inválidos", errors, headers,
				request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
		List<FieldError> errors = name == null ? null
				: List.of(new FieldError(toSnakeCase(name), "El valor no tiene el tipo esperado o está fuera de rango"));
		return build(status, "Parámetro con tipo inválido", errors, headers, request);
	}

	// ---- Errores de la aplicación ----

	@ExceptionHandler(BookNotFoundException.class)
	ResponseEntity<Object> handleBookNotFound(BookNotFoundException ex, WebRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), null, new HttpHeaders(), request);
	}

	@ExceptionHandler(DuplicateIsbnException.class)
	ResponseEntity<Object> handleDuplicateIsbn(DuplicateIsbnException ex, WebRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), List.of(new FieldError("isbn", "El ISBN ya está registrado")),
				new HttpHeaders(), request);
	}

	@ExceptionHandler(NoLocalCurrencyException.class)
	ResponseEntity<Object> handleNoLocalCurrency(NoLocalCurrencyException ex, WebRequest request) {
		return build(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), null, new HttpHeaders(), request);
	}

	@ExceptionHandler(BookModifiedException.class)
	ResponseEntity<Object> handleBookModified(BookModifiedException ex, WebRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), null, new HttpHeaders(), request);
	}

	@ExceptionHandler(BadRequestException.class)
	ResponseEntity<Object> handleBadRequest(BadRequestException ex, WebRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Parámetros inválidos",
				List.of(new FieldError(ex.getField(), ex.getMessage())), new HttpHeaders(), request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
		List<FieldError> errors = new ArrayList<>();
		for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
			String field = null;
			for (Path.Node node : violation.getPropertyPath()) {
				field = node.getName();
			}
			errors.add(new FieldError(field == null ? null : toSnakeCase(field), violation.getMessage()));
		}
		return build(HttpStatus.BAD_REQUEST, "Parámetros inválidos", errors, new HttpHeaders(), request);
	}

	// Carrera entre dos altas/ediciones con el mismo ISBN: lo atrapa el índice único.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
		if (mentionsConstraint(ex, ISBN_UNIQUE_CONSTRAINT)) {
			return build(HttpStatus.CONFLICT, "Ya existe un libro con ese ISBN",
					List.of(new FieldError("isbn", "El ISBN ya está registrado")), new HttpHeaders(), request);
		}
		return handleUnexpected(ex, request);
	}

	// Base de datos no disponible.
	@ExceptionHandler({ CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
			TransientDataAccessResourceException.class })
	ResponseEntity<Object> handleServiceUnavailable(Exception ex, WebRequest request) {
		log.error("Servicio no disponible (base de datos)", ex);
		return build(HttpStatus.SERVICE_UNAVAILABLE, "Servicio temporalmente no disponible, intente más tarde", null,
				new HttpHeaders(), request);
	}

	// Fallo de la API externa de tasas de cambio (mismo 503, mensaje propio; el detalle va solo al log).
	@ExceptionHandler(ExchangeRateUnavailableException.class)
	ResponseEntity<Object> handleExchangeRateUnavailable(ExchangeRateUnavailableException ex, WebRequest request) {
		log.warn("Servicio de tasas de cambio no disponible: {}", ex.getMessage());
		// el mensaje de la moneda sin tasa es seguro para el cliente; los de fallos de la API llevan detalles internos
		String message = ex instanceof ExchangeRateNotFoundException ? ex.getMessage()
				: "Servicio de tasas de cambio no disponible, intente más tarde";
		return build(HttpStatus.SERVICE_UNAVAILABLE, message, null, new HttpHeaders(), request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		log.error("Error inesperado", ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", null, new HttpHeaders(),
				request);
	}

	// ---- Utilidades ----

	private ResponseEntity<Object> build(HttpStatusCode status, String message, List<FieldError> fieldErrors,
			HttpHeaders headers, WebRequest request) {
		String path = request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI()
				: null;
		HttpStatus resolved = HttpStatus.resolve(status.value());
		String reason = resolved != null ? resolved.getReasonPhrase() : status.toString();
		ApiError body = new ApiError(Instant.now(), status.value(), reason, message, path,
				fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors);
		return ResponseEntity.status(status).headers(headers).body(body);
	}

	private static String defaultMessage(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "Petición inválida";
			case 404 -> "Recurso no encontrado";
			case 405 -> "Método HTTP no permitido para este recurso";
			case 406 -> "Formato de respuesta no soportado";
			case 415 -> "Tipo de contenido no soportado; use application/json";
			default -> status.is5xxServerError() ? "Error interno del servidor" : "Petición no válida";
		};
	}

	private static boolean mentionsConstraint(Throwable throwable, String constraint) {
		for (Throwable t = throwable; t != null; t = t.getCause()) {
			if (t.getMessage() != null && t.getMessage().contains(constraint)) {
				return true;
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return false;
	}

	// camelCase -> snake_case (idempotente sobre nombres que ya son snake_case).
	static String toSnakeCase(String name) {
		return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
	}

}
