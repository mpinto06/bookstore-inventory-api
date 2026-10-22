package com.nextep.bookstore.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

// Rechaza el carácter NUL (código 0): PostgreSQL no lo admite en texto y daría un 500.
// Los nulos se consideran válidos aquí; de eso se encarga @NotBlank.
@Documented
@Constraint(validatedBy = NoNulValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface NoNul {

	String message() default "contiene caracteres no permitidos";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
