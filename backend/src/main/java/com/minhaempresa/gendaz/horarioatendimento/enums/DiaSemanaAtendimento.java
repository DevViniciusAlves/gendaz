package com.minhaempresa.gendaz.horarioatendimento.enums;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public enum DiaSemanaAtendimento {
    SEGUNDA(1, "Segunda"),
    TERCA(2, "Terça"),
    QUARTA(3, "Quarta"),
    QUINTA(4, "Quinta"),
    SEXTA(5, "Sexta"),
    SABADO(6, "Sábado"),
    DOMINGO(7, "Domingo");

    private final int ordem;
    private final String rotulo;

    DiaSemanaAtendimento(int ordem, String rotulo) {
        this.ordem = ordem;
        this.rotulo = rotulo;
    }

    public int getOrdem() {
        return ordem;
    }

    public String getRotulo() {
        return rotulo;
    }

    public DayOfWeek toDayOfWeek() {
        return DayOfWeek.of(ordem);
    }

    public static DiaSemanaAtendimento from(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null) {
            return SEGUNDA;
        }
        return Arrays.stream(values())
                .filter(dia -> dia.ordem == dayOfWeek.getValue())
                .findFirst()
                .orElse(SEGUNDA);
    }

    public static List<DiaSemanaAtendimento> ordemPadrao() {
        return Arrays.stream(values())
                .sorted(Comparator.comparingInt(DiaSemanaAtendimento::getOrdem))
                .toList();
    }
}

