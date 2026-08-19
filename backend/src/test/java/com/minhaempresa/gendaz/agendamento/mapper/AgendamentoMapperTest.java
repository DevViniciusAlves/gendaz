package com.minhaempresa.gendaz.agendamento.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class AgendamentoMapperTest {

    private final AgendamentoMapper mapper = new AgendamentoMapper();

    private AgendamentoEntity agendamentoBase() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(9L).nome("Ana").build();
        ServicoEntity servico = ServicoEntity.builder().id(3L).nome("Corte").valor(new BigDecimal("100.00")).build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(4L).nome("Dra. Marina").build();
        return AgendamentoEntity.builder()
                .id(10L)
                .protocolo("123456")
                .cliente(cliente)
                .servico(servico)
                .profissional(profissional)
                .empresa(empresa)
                .data(LocalDate.now())
                .horaInicio(LocalTime.of(9, 0))
                .horaFim(LocalTime.of(10, 0))
                .status(StatusAgendamento.PENDENTE)
                .build();
    }

    @Test
    void novoAgendamentoComSnapshotExpoeVetorFinalComoValor() {
        AgendamentoEntity agendamento = agendamentoBase();
        agendamento.setValorOriginal(new BigDecimal("100.00"));
        agendamento.setValorDesconto(new BigDecimal("50.00"));
        agendamento.setValorFinal(new BigDecimal("50.00"));
        agendamento.setCupomCodigo("TESTE50");
        agendamento.setTipoPromocaoAplicada("VALOR_FIXO");
        agendamento.setValorPromocaoAplicada(new BigDecimal("50.00"));
        agendamento.setPromocaoOrigemId(888L);

        AgendamentoResponse response = mapper.toResponse(agendamento);

        assertEquals(new BigDecimal("50.00"), response.valor());
        assertEquals(new BigDecimal("100.00"), response.valorOriginal());
        assertEquals(new BigDecimal("50.00"), response.valorDesconto());
        assertEquals(new BigDecimal("50.00"), response.valorFinal());
        assertEquals("TESTE50", response.cupomCodigo());
        assertEquals("VALOR_FIXO", response.tipoPromocaoAplicada());
        assertEquals(new BigDecimal("50.00"), response.valorPromocaoAplicada());
    }

    @Test
    void registroAntigoSemSnapshotUsaFallbackLegadoSemInventarDesconto() {
        AgendamentoResponse response = mapper.toResponse(agendamentoBase());

        assertEquals(new BigDecimal("100.00"), response.valor());
        assertNull(response.valorOriginal());
        assertNull(response.valorDesconto());
        assertNull(response.valorFinal());
        assertNull(response.cupomCodigo());
        assertNull(response.tipoPromocaoAplicada());
    }
}