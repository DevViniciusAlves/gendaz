package com.minhaempresa.agendapro.notafiscal.mapper;

import com.minhaempresa.agendapro.notafiscal.dto.NotaFiscalDtos.NotaFiscalResponse;
import com.minhaempresa.agendapro.notafiscal.entity.NotaFiscalEntity;

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
