package com.nextep.bookstore.config;

import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.nextep.bookstore.dto.ApiError;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

// Con springdoc.api-docs.enabled=false springdoc no registra sus beans (p. ej. ObjectMapperProvider), así que esta
// configuración tampoco debe cargarse.
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", matchIfMissing = true)
public class OpenApiConfig {

	private static final String API_ERROR_REF = "#/components/schemas/ApiError";

	// Errores genéricos que cualquier endpoint puede devolver; cada endpoint documenta solo los suyos.
	private static final Map<String, String> COMMON_ERRORS = Map.of(
			"400", "Petición inválida: body, parámetros de consulta o id con datos incorrectos.",
			"500", "Error inesperado del servidor (mensaje genérico; el detalle solo va al log).",
			"503", "Servicio no disponible: base de datos caída.");

	@Bean
	OpenAPI bookstoreOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Bookstore Inventory API")
				.description("API REST para gestionar el inventario de una librería: altas, consulta, búsqueda "
						+ "por categoría, control de stock bajo y cálculo del precio de venta en la moneda local "
						+ "del proveedor a partir de tasas de cambio. El JSON usa snake_case.")
				.version("0.0.1"));
	}

	// springdoc genera los esquemas con su propio ObjectMapper (Jackson 2 vía swagger-core), que no hereda
	// spring.jackson.property-naming-strategy (Jackson 3): sin esto los esquemas saldrían en camelCase.
	@Bean
	ModelResolver snakeCaseModelResolver(ObjectMapperProvider objectMapperProvider) {
		return new ModelResolver(objectMapperProvider.jsonMapper().copy()
				.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE));
	}

	@Bean
	OpenApiCustomizer commonErrorResponses() {
		return openApi -> openApi.getPaths().values()
				.forEach(item -> item.readOperations().forEach(this::addCommonErrors));
	}

	// Solo se agrega si el endpoint no definió ya ese status (ej. el 503 propio de calculate-price).
	private void addCommonErrors(Operation operation) {
		COMMON_ERRORS.forEach((status, description) -> {
			if (operation.getResponses().get(status) == null) {
				operation.getResponses().addApiResponse(status, new ApiResponse().description(description)
						.content(new Content().addMediaType("application/json",
								new MediaType().schema(new Schema<>().$ref(API_ERROR_REF)))));
			}
		});
	}

}
