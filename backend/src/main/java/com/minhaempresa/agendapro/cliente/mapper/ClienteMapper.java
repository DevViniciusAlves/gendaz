package com.minhaempresa.agendapro.cliente.mapper;

import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.ClienteResponse;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;

public class ClienteMapper {
    public ClienteResponse toResponse(ClienteEntity cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNome(),
                cliente.getTelefone(),
                cliente.getEmail(),
                cliente.getObservacoes(),
                cliente.getStatus(),
                cliente.getEmpresa().getId(),
                cliente.getDataCriacao(),
                cliente.getDataAtualizacao()
        );
    }
}
