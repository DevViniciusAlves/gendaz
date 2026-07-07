package com.minhaempresa.agendapro.shared;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TelefoneInternacionalValidator implements ConstraintValidator<TelefoneInternacional, String> {

    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("^\\+?\\d{10,15}$");

    @Override
    public void initialize(TelefoneInternacional constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String digits = value.replaceAll("[^\\d+]", "");
        if (!digits.startsWith("+")) {
            digits = "+" + digits;
        }
        return PHONE_PATTERN.matcher(digits).matches();
    }
}
