package com.nextep.bookstore.book;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookRepository extends JpaRepository<Book, Long> {

	boolean existsByIsbn(String isbn);

	boolean existsByIsbnAndIdNot(String isbn, Long id);

	Optional<Book> findByIsbn(String isbn);

	// Coincidencia exacta sin distinguir mayúsculas (upper() en ambos lados); sí distingue acentos.
	Page<Book> findByCategoryIgnoreCase(String category, Pageable pageable);

	Page<Book> findByStockQuantityLessThanEqual(int threshold, Pageable pageable);

	@Query("select distinct b.category from Book b order by b.category")
	List<String> findDistinctCategories();

}
