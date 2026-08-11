package com.minhaempresa.gendaz.shared;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TelefoneInternacionalValidator implements ConstraintValidator<TelefoneInternacional, String> {

    @Override
    public void initialize(TelefoneInternacional constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String digitos = value.replaceAll("\\D", "");
        return digitos.length() >= 11 && digitos.length() <= 14;
    }
}

