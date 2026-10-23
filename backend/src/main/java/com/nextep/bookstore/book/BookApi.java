package com.nextep.bookstore.book;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.nextep.bookstore.book.dto.BookRequest;
import com.nextep.bookstore.book.dto.BookResponse;
import com.nextep.bookstore.book.dto.PageResponse;
import com.nextep.bookstore.book.dto.PriceCalculationResponse;
import com.nextep.bookstore.error.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

// Solo documentación OpenAPI de /books: el mapeo y la lógica están en BookController. Los errores 400, 500 y 503
// genéricos se añaden a todos los endpoints desde OpenApiConfig.
@Tag(name = "Libros", description = "Inventario de libros")
public interface BookApi {

	String SORT_DESCRIPTION = "Orden en formato `campo,asc|desc` (la dirección es opcional, `asc` por defecto). "
			+ "Campos permitidos: id, title, author, category, cost_usd, stock_quantity, created_at.";

	@Operation(summary = "Crear un libro", description = "Crea un libro. `id`, `selling_price_local`, "
			+ "`selling_currency`, `created_at` y `updated_at` los gestiona el servidor y se ignoran si llegan. "
			+ "El ISBN se guarda sin guiones.")
	@ApiResponse(responseCode = "201", description = "Libro creado; la cabecera `Location` apunta a /books/{id}")
	@ApiResponse(responseCode = "409", description = "Ya existe un libro con ese ISBN",
			content = @Content(schema = @Schema(implementation = ApiError.class)))
	ResponseEntity<BookResponse> create(BookRequest request);

	@Operation(summary = "Listar libros",
			description = "Listado paginado de todos los libros (orden por defecto: id ascendente).")
	@ApiResponse(responseCode = "200", description = "Página de libros")
	PageResponse<BookResponse> list(
			@Parameter(name = "page", description = "Número de página, empezando en 0 (page * size no puede superar 2147483647)",
					schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) int page,
			@Parameter(name = "size", description = "Elementos por página",
					schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")) int size,
			@Parameter(name = "sort", description = SORT_DESCRIPTION, example = "cost_usd,desc") String sort);

	@Operation(summary = "Buscar libros por categoría", description = "Coincidencia exacta de la categoría, sin distinguir "
			+ "mayúsculas (distingue acentos). Una categoría sin resultados devuelve 200 con `content` vacío.")
	@ApiResponse(responseCode = "200", description = "Página de libros de la categoría")
	PageResponse<BookResponse> search(
			@Parameter(name = "category", description = "Categoría a buscar (se recortan los espacios)", required = true,
					example = "Literatura Clásica", schema = @Schema(maxLength = 100)) String category,
			@Parameter(name = "page", description = "Número de página, empezando en 0",
					schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) int page,
			@Parameter(name = "size", description = "Elementos por página",
					schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")) int size,
			@Parameter(name = "sort", description = SORT_DESCRIPTION, example = "cost_usd,desc") String sort);

	@Operation(summary = "Libros con stock bajo",
			description = "Libros con `stock_quantity <= threshold` (umbral inclusivo). "
					+ "Orden por defecto: `stock_quantity` ascendente y luego `id`.")
	@ApiResponse(responseCode = "200", description = "Página de libros con stock bajo")
	PageResponse<BookResponse> lowStock(
			@Parameter(name = "threshold", description = "Umbral de stock (inclusivo)",
					schema = @Schema(type = "integer", minimum = "0", maximum = "1000000", defaultValue = "10")) int threshold,
			@Parameter(name = "page", description = "Número de página, empezando en 0",
					schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) int page,
			@Parameter(name = "size", description = "Elementos por página",
					schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")) int size,
			@Parameter(name = "sort", description = SORT_DESCRIPTION, example = "title,asc") String sort);

	@Operation(summary = "Listar categorías", description = "Categorías distintas existentes, en orden alfabético y sin "
			+ "paginar (`[]` si no hay libros). Sirve para poblar filtros.")
	@ApiResponse(responseCode = "200", description = "Arreglo de categorías")
	List<String> categories();

	@Operation(summary = "Obtener un libro por id")
	@ApiResponse(responseCode = "200", description = "Libro encontrado")
	@ApiResponse(responseCode = "404", description = "No existe un libro con ese id",
			content = @Content(schema = @Schema(implementation = ApiError.class)))
	BookResponse get(
			@Parameter(name = "id", description = "Id del libro (entero positivo)", example = "1",
					schema = @Schema(type = "integer", format = "int64", minimum = "1")) Long id);

	@Operation(summary = "Calcular y guardar el precio de venta", description = """
			Convierte `cost_usd` a la moneda del país del proveedor, aplica el margen configurado y **guarda** \
			`selling_price_local` y `selling_currency` en el libro (cambia `updated_at`). No lleva body.

			La tasa de cambio se obtiene en cadena: caché en memoria (15 min) -> API externa -> última tasa guardada \
			en la BD (respaldo) -> 503 si la moneda no tiene tasa en ninguna.

			`rate_source` indica el origen de la tasa: `LIVE` (datos de la API de los últimos 15 minutos) o \
			`FALLBACK` (tasa guardada porque la API no respondió; `rate_date` indica su antigüedad).""")
	@ApiResponse(responseCode = "200",
			description = "Desglose del cálculo; el precio y la moneda quedan guardados en el libro")
	@ApiResponse(responseCode = "404", description = "No existe un libro con ese id",
			content = @Content(schema = @Schema(implementation = ApiError.class)))
	@ApiResponse(responseCode = "409", description = "El libro cambió (costo o país) mientras se calculaba; reintentar",
			content = @Content(schema = @Schema(implementation = ApiError.class)))
	@ApiResponse(responseCode = "422", description = "El país del proveedor no tiene moneda asociada (p. ej. AQ)",
			content = @Content(schema = @Schema(implementation = ApiError.class)))
	@ApiResponse(responseCode = "503",
			description = "Sin tasa de cambio para la moneda (ni en la API ni en el respaldo) o BD caída",
			content = @Content(schema = @Schema(implementation = ApiError.class)))
	PriceCalculationResponse calculatePrice(
			@Parameter(name = "id", description = "Id del libro (entero positivo)", example = "1",
					schema = @Schema(type = "integer", format = "int64", minimum = "1")) Long id);

	@Operation(summary = "Reemplazar un libro", description = "Reemplazo completo (no hay PATCH): hay que enviar todos los "
			+ "campos. Si cambia `cost_usd` o `supplier_country`, `selling_price_local` y `selling_currency` se ponen en "
			+ "`null` porque el precio calculado quedó obsoleto y debe recalcularse con calculate-price.")
	@ApiResponse(responseCode = "200", description = "Libro actualizado")
	@ApiResponse(responseCode = "404", description = "No existe un libro con ese id",
			content = @Content(schema = @Schema(implementation = ApiError.class)))
	@ApiResponse(responseCode = "409", description = "Otro libro ya usa ese ISBN",
			content = @Content(schema = @Schema(implementation = ApiError.class)))
	BookResponse update(
			@Parameter(name = "id", description = "Id del libro (entero positivo)", example = "1",
					schema = @Schema(type = "integer", format = "int64", minimum = "1")) Long id,
			BookRequest request);

	@Operation(summary = "Eliminar un libro")
	@ApiResponse(responseCode = "204", description = "Libro eliminado")
	@ApiResponse(responseCode = "404", description = "No existe un libro con ese id",
			content = @Content(schema = @Schema(implementation = ApiError.class)))
	ResponseEntity<Void> delete(
			@Parameter(name = "id", description = "Id del libro (entero positivo)", example = "1",
					schema = @Schema(type = "integer", format = "int64", minimum = "1")) Long id);

}
