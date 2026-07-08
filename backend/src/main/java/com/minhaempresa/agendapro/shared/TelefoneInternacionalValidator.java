package com.minhaempresa.agendapro.shared;

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
        if (!digitos.startsWith("55")) {
            digitos = "55" + digitos;
        }
        if (digitos.length() == 12 && digitos.startsWith("55")) {
            digitos = digitos.substring(0, 4) + "9" + digitos.substring(4);
        }
        if (digitos.length() != 13) {
            return false;
        }
        int ddd = Integer.parseInt(digitos.substring(2, 4));
        return ddd >= 11 && ddd <= 99;
    }
}
