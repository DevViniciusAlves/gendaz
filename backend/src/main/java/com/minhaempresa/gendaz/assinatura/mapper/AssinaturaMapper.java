package com.minhaempresa.gendaz.assinatura.mapper;

import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class AssinaturaMapper {
    public AssinaturaResponse toResponse(AssinaturaEntity assinatura) {
        return new AssinaturaResponse(
                assinatura.getId(),
                assinatura.getEmpresa().getId(),
                assinatura.getEmpresa().getNomeFantasia(),
                assinatura.getPlano().getId(),
                assinatura.getPlano().getNome(),
                assinatura.getStatus(),
                assinatura.getDataInicio(),
                assinatura.getDataFim(),
                assinatura.getDataInicioTeste(),
                assinatura.getDataFimTeste(),
                diasRestantes(assinatura.getDataInicio(), assinatura.getDataFim())
        );
    }

    private long diasRestantes(LocalDate dataInicio, LocalDate dataFim) {
        if (dataFim == null) return 0;
        LocalDate referencia = dataInicio != null && dataInicio.isAfter(LocalDate.now())
                ? dataInicio
                : LocalDate.now();
        return Math.max(0, ChronoUnit.DAYS.between(referencia, dataFim));
    }
}

