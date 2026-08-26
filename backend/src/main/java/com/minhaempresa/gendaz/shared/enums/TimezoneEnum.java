package com.minhaempresa.gendaz.shared.enums;

import java.util.Arrays;
import java.util.Optional;

public enum TimezoneEnum {
    AMERICA_CUIABA("America/Cuiaba", "Cuiabá"),
    AMERICA_SAO_PAULO("America/Sao_Paulo", "São Paulo"),
    AMERICA_MANAUS("America/Manaus", "Manaus"),
    AMERICA_RIO_BRANCO("America/Rio_Branco", "Rio Branco"),
    AMERICA_PORTO_VELHO("America/Porto_Velho", "Porto Velho"),
    AMERICA_BELEM("America/Belem", "Belém"),
    AMERICA_FORTALEZA("America/Fortaleza", "Fortaleza"),
    AMERICA_RECIFE("America/Recife", "Recife"),
    AMERICA_BAHIA("America/Bahia", "Bahia"),
    UTC("UTC", "UTC");

    private final String value;
    private final String label;

    TimezoneEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<TimezoneEnum> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(item -> item.value.equals(value))
                .findFirst();
    }
}

