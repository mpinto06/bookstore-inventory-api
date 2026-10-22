package com.nextep.bookstore.book;

import java.net.URI;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/books")
public class BookController {

	private final BookService service;

	public BookController(BookService service) {
		this.service = service;
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

	@GetMapping("/{id}")
	public BookResponse get(@PathVariable Long id) {
		return service.get(id);
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
