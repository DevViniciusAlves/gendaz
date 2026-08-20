package com.minhaempresa.gendaz.pagamento.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PagamentoMapperTest {
    private final PagamentoMapper mapper = new PagamentoMapper();

    @Test
    void toResponseDevePropagarStatusCliente() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(9L).nome("Vinicius").status(StatusCadastro.EXCLUIDO).empresa(empresa).build();
        PagamentoEntity pagamento = PagamentoEntity.builder()
                .id(11L)
                .cliente(cliente)
                .empresa(empresa)
                .agendamento(AgendamentoEntity.builder().id(7L).protocolo("329720").build())
                .valor(new BigDecimal("70.00"))
                .metodoPagamento(MetodoPagamento.OUTRO)
                .status(StatusPagamento.PENDENTE)
                .build();

        var response = mapper.toResponse(pagamento);

        assertEquals(StatusCadastro.EXCLUIDO, response.statusCliente());
        assertEquals("Vinicius", response.clienteNome());
    }
}
