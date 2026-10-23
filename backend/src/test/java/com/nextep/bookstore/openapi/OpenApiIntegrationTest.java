package com.nextep.bookstore.openapi;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.nextep.bookstore.AbstractIntegrationTest;

class OpenApiIntegrationTest extends AbstractIntegrationTest {

	@Test
	void apiDocsListTheNineEndpointsWithTheirLiteralRoutes() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Bookstore Inventory API"))
				.andExpect(jsonPath("$.paths", hasKey("/books")))
				.andExpect(jsonPath("$.paths", hasKey("/books/{id}")))
				.andExpect(jsonPath("$.paths", hasKey("/books/search")))
				.andExpect(jsonPath("$.paths", hasKey("/books/low-stock")))
				.andExpect(jsonPath("$.paths", hasKey("/books/categories")))
				.andExpect(jsonPath("$.paths['/books/{id}/calculate-price']", hasKey("post")))
				.andExpect(jsonPath("$.paths['/books'].*.operationId", containsInAnyOrder("create", "list")))
				.andExpect(jsonPath("$.paths['/books/{id}'].*.operationId", containsInAnyOrder("get", "update", "delete")));
	}

	// springdoc usa su propio ObjectMapper (Jackson 2): sin ModelResolver propio los esquemas saldrían en camelCase
	@Test
	void schemasUseSnakeCaseLikeTheRealJson() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.components.schemas.BookRequest.properties", hasKey("cost_usd")))
				.andExpect(jsonPath("$.components.schemas.BookRequest.properties", hasKey("supplier_country")))
				.andExpect(jsonPath("$.components.schemas.BookRequest.properties", not(hasKey("costUsd"))))
				.andExpect(jsonPath("$.components.schemas.BookResponse.properties.created_at.readOnly").value(true))
				.andExpect(jsonPath("$.components.schemas.PageResponseBookResponse.properties", hasKey("total_elements")))
				.andExpect(jsonPath("$.components.schemas.ApiError.properties", hasKey("field_errors")))
				.andExpect(jsonPath("$.components.schemas.PriceCalculationResponse.properties", hasKey("rate_source")));
	}

	@Test
	void paginationParametersArePageSizeAndSortWithLimits() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/books'].get.parameters[*].name", containsInAnyOrder("page", "size", "sort")))
				.andExpect(jsonPath("$.paths['/books'].get.parameters[?(@.name=='size')].schema.maximum").value(100));
	}

	// El @RestControllerAdvice (incluido el catch-all de 500) no debe interferir con la documentación
	@Test
	void swaggerUiAndItsResourcesAreNotHijackedByTheErrorHandler() throws Exception {
		mvc.perform(get("/swagger-ui.html")).andExpect(status().isFound())
				.andExpect(redirectedUrl("/swagger-ui/index.html"));
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
		mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
	}

}
