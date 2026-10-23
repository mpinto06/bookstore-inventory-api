package com.nextep.bookstore.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "books")
public class Book {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(nullable = false, length = 255)
	private String author;

	@Column(nullable = false, length = 13)
	private String isbn;

	@Column(name = "cost_usd", nullable = false, precision = 10, scale = 2)
	private BigDecimal costUsd;

	@Column(name = "selling_price_local", precision = 20, scale = 2)
	private BigDecimal sellingPriceLocal;

	@Column(name = "selling_currency", length = 3)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String sellingCurrency;

	@Column(name = "stock_quantity", nullable = false)
	private int stockQuantity;

	@Column(nullable = false, length = 100)
	private String category;

	@Column(name = "supplier_country", nullable = false, length = 2)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String supplierCountry;

	// created_at (DEFAULT) y updated_at (trigger) los asigna la BD; Hibernate solo los lee de vuelta
	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Generated(event = { EventType.INSERT, EventType.UPDATE })
	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	protected Book() {
	}

	public Book(String title, String author, String isbn, BigDecimal costUsd, BigDecimal sellingPriceLocal,
			String sellingCurrency, int stockQuantity, String category, String supplierCountry) {
		this.title = title;
		this.author = author;
		this.isbn = isbn;
		this.costUsd = costUsd;
		this.sellingPriceLocal = sellingPriceLocal;
		this.sellingCurrency = sellingCurrency;
		this.stockQuantity = stockQuantity;
		this.category = category;
		this.supplierCountry = supplierCountry;
	}

	public void applyPrice(BigDecimal sellingPriceLocal, String sellingCurrency) {
		this.sellingPriceLocal = sellingPriceLocal;
		this.sellingCurrency = sellingCurrency;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getIsbn() {
		return isbn;
	}

	public void setIsbn(String isbn) {
		this.isbn = isbn;
	}

	public BigDecimal getCostUsd() {
		return costUsd;
	}

	public void setCostUsd(BigDecimal costUsd) {
		this.costUsd = costUsd;
	}

	public BigDecimal getSellingPriceLocal() {
		return sellingPriceLocal;
	}

	public void setSellingPriceLocal(BigDecimal sellingPriceLocal) {
		this.sellingPriceLocal = sellingPriceLocal;
	}

	public String getSellingCurrency() {
		return sellingCurrency;
	}

	public void setSellingCurrency(String sellingCurrency) {
		this.sellingCurrency = sellingCurrency;
	}

	public int getStockQuantity() {
		return stockQuantity;
	}

	public void setStockQuantity(int stockQuantity) {
		this.stockQuantity = stockQuantity;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getSupplierCountry() {
		return supplierCountry;
	}

	public void setSupplierCountry(String supplierCountry) {
		this.supplierCountry = supplierCountry;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
