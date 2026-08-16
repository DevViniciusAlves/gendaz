package com.minhaempresa.gendaz.shared;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

public class TelefoneInternacionalValidator implements ConstraintValidator<TelefoneInternacional, String> {

    @Autowired
    private PhoneNumberService phoneNumberService;

    @Override
    public void initialize(TelefoneInternacional constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return phoneNumberService.valido(value);
    }
}

