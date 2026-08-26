package com.minhaempresa.gendaz.shared;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = TelefoneInternacionalValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface TelefoneInternacional {
    String message() default "Número de telefone inválido. Confira o país e o número informado.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

