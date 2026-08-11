package com.minhaempresa.gendaz.notafiscal.mapper;

import com.minhaempresa.gendaz.notafiscal.dto.NotaFiscalDtos.NotaFiscalResponse;
import com.minhaempresa.gendaz.notafiscal.entity.NotaFiscalEntity;

public class NotaFiscalMapper {
    public NotaFiscalResponse toResponse(NotaFiscalEntity notaFiscal) {
        return new NotaFiscalResponse(
                notaFiscal.getId(),
                notaFiscal.getCliente().getId(),
                notaFiscal.getCliente().getNome(),
                notaFiscal.getEmpresa().getId(),
                notaFiscal.getValor(),
                notaFiscal.getStatus(),
                notaFiscal.getNumeroFake(),
                notaFiscal.getDataEmissao()
        );
    }
}

