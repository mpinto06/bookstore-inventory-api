package com.nextep.bookstore.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Código de país ISO 3166-1 alpha-2 real (p. ej. "ES", "VE"), sin distinguir mayúsculas.
 * Los valores nulos se consideran válidos (combinar con @NotNull/@NotBlank).
 */
@Documented
@Constraint(validatedBy = CountryCodeValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCountryCode {

	String message() default "Código de país inválido: debe ser un código ISO 3166-1 alpha-2";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
