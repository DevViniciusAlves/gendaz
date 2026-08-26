package com.minhaempresa.gendaz.mensagem.mapper;

import com.minhaempresa.gendaz.mensagem.dto.MensagemDtos.MensagemResponse;
import com.minhaempresa.gendaz.mensagem.entity.MensagemEntity;

public class MensagemMapper {
    public MensagemResponse toResponse(MensagemEntity mensagem) {
        return new MensagemResponse(
                mensagem.getId(),
                mensagem.getConversa().getId(),
                mensagem.getConteudo(),
                mensagem.getDirecao(),
                mensagem.getTipo(),
                mensagem.getDataEnvio()
        );
    }
}

