package com.nextep.bookstore.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextep.bookstore.dto.BookRequest;
import com.nextep.bookstore.dto.BookResponse;
import com.nextep.bookstore.dto.PageResponse;
import com.nextep.bookstore.exception.BadRequestException;
import com.nextep.bookstore.exception.BookNotFoundException;
import com.nextep.bookstore.exception.DuplicateIsbnException;
import com.nextep.bookstore.mapper.BookMapper;
import com.nextep.bookstore.model.Book;
import com.nextep.bookstore.repository.BookRepository;
import com.nextep.bookstore.validation.IsbnValidator;
import com.nextep.bookstore.validation.NoNulValidator;

@Service
public class BookService {

	private final BookRepository repository;

	public BookService(BookRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public BookResponse create(BookRequest request) {
		String isbn = IsbnValidator.normalize(request.isbn());
		if (repository.existsByIsbn(isbn)) {
			throw new DuplicateIsbnException(isbn);
		}
		Book book = new Book(request.title().trim(), request.author().trim(), isbn, request.costUsd(), null, null,
				request.stockQuantity(), request.category().trim(), normalizeCountry(request.supplierCountry()));
		return BookMapper.toResponse(repository.saveAndFlush(book));
	}

	@Transactional(readOnly = true)
	public BookResponse get(Long id) {
		return BookMapper.toResponse(find(id));
	}

	@Transactional(readOnly = true)
	public PageResponse<BookResponse> list(Pageable pageable) {
		return PageResponse.of(repository.findAll(pageable).map(BookMapper::toResponse));
	}

	// Categoría obligatoria (máx. 100), coincidencia exacta sin distinguir mayúsculas. Sin resultados: página vacía.
	@Transactional(readOnly = true)
	public PageResponse<BookResponse> search(String category, Pageable pageable) {
		if (category == null || category.isBlank()) {
			throw new BadRequestException("category", "category es obligatorio");
		}
		String trimmed = category.trim();
		if (trimmed.length() > BookRequest.MAX_CATEGORY_LENGTH) {
			throw new BadRequestException("category",
					"category no puede superar los " + BookRequest.MAX_CATEGORY_LENGTH + " caracteres");
		}
		if (NoNulValidator.containsNul(trimmed)) {
			// los parámetros de consulta no pasan por Bean Validation: se comprueba aquí para evitar un 500
			throw new BadRequestException("category", "category contiene caracteres no permitidos");
		}
		return PageResponse.of(repository.findByCategoryIgnoreCase(trimmed, pageable).map(BookMapper::toResponse));
	}

	// Stock bajo: stock_quantity <= threshold, con threshold entre 0 y el máximo permitido de stock.
	@Transactional(readOnly = true)
	public PageResponse<BookResponse> lowStock(int threshold, Pageable pageable) {
		if (threshold < 0 || threshold > BookRequest.MAX_STOCK) {
			throw new BadRequestException("threshold", "threshold debe estar entre 0 y " + BookRequest.MAX_STOCK);
		}
		return PageResponse.of(repository.findByStockQuantityLessThanEqual(threshold, pageable)
				.map(BookMapper::toResponse));
	}

	// Categorías distintas, en orden alfabético (para el filtro del frontend).
	@Transactional(readOnly = true)
	public List<String> categories() {
		return repository.findDistinctCategories();
	}

	// Reemplazo completo. Si cambia cost_usd o supplier_country, el precio de venta calculado
	// (selling_price_local y selling_currency) queda obsoleto: se pone en null y debe recalcularse.
	@Transactional
	public BookResponse update(Long id, BookRequest request) {
		Book book = find(id);
		String isbn = IsbnValidator.normalize(request.isbn());
		if (repository.existsByIsbnAndIdNot(isbn, id)) {
			throw new DuplicateIsbnException(isbn);
		}
		String country = normalizeCountry(request.supplierCountry());
		boolean priceInputsChanged = book.getCostUsd().compareTo(request.costUsd()) != 0
				|| !book.getSupplierCountry().equals(country);

		book.setTitle(request.title().trim());
		book.setAuthor(request.author().trim());
		book.setIsbn(isbn);
		book.setCostUsd(request.costUsd());
		book.setStockQuantity(request.stockQuantity());
		book.setCategory(request.category().trim());
		book.setSupplierCountry(country);
		if (priceInputsChanged) {
			book.setSellingPriceLocal(null);
			book.setSellingCurrency(null);
		}
		// flush explícito: una violación del índice único sale aquí, dentro del servicio
		return BookMapper.toResponse(repository.saveAndFlush(book));
	}

	@Transactional
	public void delete(Long id) {
		repository.delete(find(id));
	}

	private Book find(Long id) {
		if (id <= 0) {
			throw new BadRequestException("id", "id debe ser un número positivo");
		}
		return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
	}

	private static String normalizeCountry(String country) {
		return country.trim().toUpperCase(Locale.ROOT);
	}

}
