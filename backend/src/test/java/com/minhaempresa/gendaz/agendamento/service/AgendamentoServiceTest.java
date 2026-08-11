package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.agendamento.service.AgendaBlockedDayService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.service.ServicoService;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {
    @Mock AgendamentoRepository agendamentoRepository;
    @Mock ClienteService clienteService;
    @Mock ServicoService servicoService;
    @Mock ProfissionalService profissionalService;
    @Mock EmpresaService empresaService;
    @Mock HorarioAtendimentoService horarioAtendimentoService;
    @Mock AgendaBlockedDayService agendaBlockedDayService;
    @Mock PagamentoRepository pagamentoRepository;
    @Mock SanitizacaoService sanitizacaoService;
    @Mock ResendEmailService resendEmailService;
    @Captor ArgumentCaptor<AgendamentoEntity> agendamentoCaptor;
    @InjectMocks AgendamentoService agendamentoService;

    @Test
    void deveCalcularHoraFimPelaDuracaoDoServico() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").empresa(empresa).build();
        ServicoEntity servico = ServicoEntity.builder().id(1L).nome("Consulta").duracaoMinutos(60).empresa(empresa).build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(1L).nome("Dra. Marina").empresa(empresa).build();
        when(clienteService.buscarEntidade(1L)).thenReturn(cliente);
        when(servicoService.buscarEntidade(1L)).thenReturn(servico);
        when(profissionalService.buscarEntidade(1L)).thenReturn(profissional);
        when(empresaService.buscarEntidade(1L)).thenReturn(empresa);
        when(agendamentoRepository.existeConflitoDeHorario(any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(agendaBlockedDayService.diaBloqueado(any(), any(), any())).thenReturn(false);
        when(agendamentoRepository.save(any())).thenAnswer(invocation -> {
            AgendamentoEntity agendamento = invocation.getArgument(0);
            agendamento.setId(10L);
            return agendamento;
        });
        when(pagamentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = agendamentoService.criar(new CriarAgendamentoRequest(1L, 1L, 1L, 1L, LocalDate.now(), LocalTime.of(9, 0), null, null));

        assertEquals(LocalTime.of(10, 0), response.horaFim());
        verify(resendEmailService).enviarEmailNovoAgendamento(empresa, agendamentoCaptor.capture());
        assertEquals("10", agendamentoCaptor.getValue().getId().toString());
    }
}

