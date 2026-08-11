package com.minhaempresa.gendaz.shared;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = TelefoneInternacionalValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface TelefoneInternacional {
    String message() default "O telefone deve conter codigo da cidade + numero.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

