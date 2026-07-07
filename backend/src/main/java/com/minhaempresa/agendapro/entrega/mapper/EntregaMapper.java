package com.minhaempresa.agendapro.entrega.mapper;

import com.minhaempresa.agendapro.entrega.dto.EntregaDtos.EntregaResponse;
import com.minhaempresa.agendapro.entrega.entity.EntregaEntity;

public class EntregaMapper {
    public EntregaResponse toResponse(EntregaEntity entrega) {
        return new EntregaResponse(
                entrega.getId(),
                entrega.getCliente().getId(),
                entrega.getCliente().getNome(),
                entrega.getEmpresa().getId(),
                entrega.getEndereco(),
                entrega.getStatus(),
                entrega.getObservacoes(),
                entrega.getDataPrevisao()
        );
    }
}
