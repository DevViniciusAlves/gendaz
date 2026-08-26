package com.minhaempresa.gendaz.empresa.enums;

public enum RamoEmpresa {
    BARBERSHOP("Barbershop", 20, 60, "dias"),
    SALAO_CABELO("Salão/Cabelo", 45, 90, "dias"),
    PERSONAL_TRAINER("Personal Trainer", 7, 14, "dias"),
    CLINICA_FISIOTERAPIA("Clínica - Fisioterapia", 7, 14, "dias"),
    CLINICA_ODONTOLOGIA("Clínica - Odontologia", 180, 360, "dias"),
    OUTRO("Outro", 30, 60, "dias");

    private final String displayName;
    private final int diasRegular;
    private final int diasAltoRisco;
    private final String unidade;

    RamoEmpresa(String displayName, int diasRegular, int diasAltoRisco, String unidade) {
        this.displayName = displayName;
        this.diasRegular = diasRegular;
        this.diasAltoRisco = diasAltoRisco;
        this.unidade = unidade;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDiasRegular() {
        return diasRegular;
    }

    public int getDiasAltoRisco() {
        return diasAltoRisco;
    }

    public String getUnidade() {
        return unidade;
    }
}

