package com.minhaempresa.agendapro.assinatura.mapper;

import com.minhaempresa.agendapro.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.agendapro.assinatura.entity.AssinaturaEntity;
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
                diasRestantes(assinatura.getDataFimTeste())
        );
    }

    private long diasRestantes(LocalDate dataFimTeste) {
        if (dataFimTeste == null) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), dataFimTeste));
    }
}
