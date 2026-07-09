package com.minhaempresa.agendapro.empresa.mapper;

import com.minhaempresa.agendapro.empresa.dto.EmpresaDtos.EmpresaResponse;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;

public class EmpresaMapper {
    public EmpresaResponse toResponse(EmpresaEntity empresa) {
        return new EmpresaResponse(
                empresa.getId(),
                empresa.getNomeFantasia(),
                empresa.getDocumento(),
                empresa.getTelefone(),
                empresa.getEmail(),
                empresa.getStatus(),
                // ⚠️ DESATIVADO - WhatsApp
                // empresa.getWhatsappConnected(),
                // empresa.getWhatsappPhone(),
                // empresa.getWhatsappNotificationsEnabled(),
                // empresa.getWhatsappSecretariaIaEnabled(),
                empresa.getTimezone(),
                empresa.getDataCriacao(),
                empresa.getDataAtualizacao()
        );
    }
}
