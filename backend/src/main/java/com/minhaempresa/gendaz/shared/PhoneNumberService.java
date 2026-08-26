package com.minhaempresa.gendaz.shared;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.stereotype.Service;

@Service
public class PhoneNumberService {

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    public String normalizarObrigatorio(String valor) {
        String normalizado = normalizar(valor, false);
        if (normalizado == null) {
            throw new BusinessException("Telefone é obrigatório.");
        }
        return normalizado;
    }

    public String normalizarOpcional(String valor) {
        return normalizar(valor, true);
    }

    private String normalizar(String valor, boolean opcional) {
        if (valor == null || valor.isBlank()) {
            if (opcional) return null;
            throw new BusinessException("Telefone é obrigatório.");
        }

        String sanitizado = valor.trim();
        Phonenumber.PhoneNumber phoneNumber = null;

        if (sanitizado.startsWith("+")) {
            phoneNumber = parsearConfiado(sanitizado, null);
        } else {
            // Compatibilidade com dados/telas legadas do Brasil: valores antigos sem "+"
            // jamais podem ser prefixados manualmente com 55; apenas o parsing do
            // libphonenumber decide (região padrão BR).
            phoneNumber = parsearConfiado(sanitizado, "BR");
        }

        if (phoneNumber == null || !phoneNumberUtil.isValidNumber(phoneNumber)) {
            throw new BusinessException("Número de telefone inválido. Confira o país e o número informado.");
        }

        return phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164).replace("+", "");
    }

    private Phonenumber.PhoneNumber parsearConfiado(String valor, String regiao) {
        try {
            return phoneNumberUtil.parse(valor, regiao);
        } catch (NumberParseException e) {
            return null;
        }
    }

    public boolean valido(String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        String sanitizado = valor.trim();
        Phonenumber.PhoneNumber phoneNumber = null;
        if (sanitizado.startsWith("+")) {
            phoneNumber = parsearConfiado(sanitizado, null);
        } else {
            phoneNumber = parsearConfiado(sanitizado, "BR");
        }
        return phoneNumber != null && phoneNumberUtil.isValidNumber(phoneNumber);
    }

    public String paraE164(String valorCanonico) {
        if (valorCanonico == null || valorCanonico.isBlank()) {
            return null;
        }
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse("+" + valorCanonico, null);
            return phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            return valorCanonico;
        }
    }

    public String formatarExibicao(String valorCanonico) {
        if (valorCanonico == null || valorCanonico.isBlank()) {
            return null;
        }
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse("+" + valorCanonico, null);
            return phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        } catch (NumberParseException e) {
            return valorCanonico;
        }
    }
}
