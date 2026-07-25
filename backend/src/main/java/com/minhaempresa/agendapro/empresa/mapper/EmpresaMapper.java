package com.minhaempresa.agendapro.empresa.mapper;

import com.minhaempresa.agendapro.empresa.dto.EmpresaDtos.EmpresaResponse;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.enums.RamoEmpresa;

public class EmpresaMapper {
    public EmpresaResponse toResponse(EmpresaEntity empresa) {
        RamoEmpresa ramo = empresa.getRamo();
        return new EmpresaResponse(
                empresa.getId(),
                empresa.getNomeFantasia(),
                empresa.getDocumento(),
                empresa.getTelefone(),
                empresa.getEmail(),
                empresa.getStatus(),
                empresa.getTimezone(),
                ramo,
                ramo != null ? ramo.getDisplayName() : null,
                ramo != null ? ramo.getDiasRegular() : null,
                ramo != null ? ramo.getDiasAltoRisco() : null,
                empresa.getRamoAtualizadoEm(),
                empresa.getDataCriacao(),
                empresa.getDataAtualizacao()
        );
    }
}
