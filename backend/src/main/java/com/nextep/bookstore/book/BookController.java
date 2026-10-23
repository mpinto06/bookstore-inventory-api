package com.nextep.bookstore.book;

import java.net.URI;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.nextep.bookstore.book.dto.BookRequest;
import com.nextep.bookstore.book.dto.BookResponse;
import com.nextep.bookstore.book.dto.PageResponse;
import com.nextep.bookstore.book.dto.PriceCalculationResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/books")
public class BookController {

	private static final String DEFAULT_THRESHOLD = "10";

	private final BookService service;

	private final PriceService priceService;

	public BookController(BookService service, PriceService priceService) {
		this.service = service;
		this.priceService = priceService;
	}

	@PostMapping
	public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
		BookResponse created = service.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	public PageResponse<BookResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = BookPaging.DEFAULT_SIZE) int size,
			@RequestParam(required = false) String sort) {
		return service.list(BookPaging.pageable(page, size, sort));
	}

	// Las rutas literales tienen prioridad sobre /{id}, por lo que no se interpretan como un id
	@GetMapping("/search")
	public PageResponse<BookResponse> search(
			@RequestParam(required = false) String category,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = BookPaging.DEFAULT_SIZE) int size,
			@RequestParam(required = false) String sort) {
		return service.search(category, BookPaging.pageable(page, size, sort));
	}

	@GetMapping("/low-stock")
	public PageResponse<BookResponse> lowStock(
			@RequestParam(defaultValue = DEFAULT_THRESHOLD) int threshold,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = BookPaging.DEFAULT_SIZE) int size,
			@RequestParam(required = false) String sort) {
		Sort defaultSort = Sort.by("stockQuantity", "id");
		return service.lowStock(threshold, BookPaging.pageable(page, size, sort, defaultSort));
	}

	@GetMapping("/categories")
	public List<String> categories() {
		return service.categories();
	}

	@GetMapping("/{id}")
	public BookResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PostMapping("/{id}/calculate-price")
	public PriceCalculationResponse calculatePrice(@PathVariable Long id) {
		return priceService.calculate(id);
	}

	@PutMapping("/{id}")
	public BookResponse update(@PathVariable Long id,
			@Valid @RequestBody BookRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
