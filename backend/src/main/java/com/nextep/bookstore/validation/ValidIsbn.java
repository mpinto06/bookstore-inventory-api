package com.nextep.bookstore.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

// ISBN-10 o ISBN-13, con o sin guiones/espacios, con dígito verificador correcto.
// Nulos y en blanco se consideran válidos aquí: de eso se encarga @NotBlank (así no hay dos mensajes).
@Documented
@Constraint(validatedBy = IsbnValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIsbn {

	String message() default "ISBN inválido: debe ser un ISBN-10 o ISBN-13 con dígito verificador correcto";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
