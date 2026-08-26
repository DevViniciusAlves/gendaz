package com.minhaempresa.gendaz.conversa.mapper;

import com.minhaempresa.gendaz.conversa.dto.ConversaDtos.ConversaResponse;
import com.minhaempresa.gendaz.conversa.entity.ConversaEntity;

public class ConversaMapper {
    public ConversaResponse toResponse(ConversaEntity conversa) {
        return new ConversaResponse(
                conversa.getId(),
                conversa.getCliente().getId(),
                conversa.getCliente().getNome(),
                conversa.getCliente().getTelefone(),
                conversa.getEmpresa().getId(),
                conversa.getStatus(),
                conversa.getUltimaMensagem(),
                conversa.getDataUltimaMensagem()
        );
    }
}

