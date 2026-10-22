package com.nextep.bookstore.book;

import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextep.bookstore.book.dto.BookRequest;
import com.nextep.bookstore.book.dto.BookResponse;
import com.nextep.bookstore.book.dto.PageResponse;
import com.nextep.bookstore.error.BadRequestException;
import com.nextep.bookstore.validation.IsbnValidator;

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

	/**
	 * Reemplazo completo. Si cambia cost_usd o supplier_country, el precio de venta calculado
	 * (selling_price_local y selling_currency) queda obsoleto: se pone en null y debe recalcularse.
	 */
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
