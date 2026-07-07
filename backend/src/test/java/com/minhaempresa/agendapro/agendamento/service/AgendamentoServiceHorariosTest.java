package com.minhaempresa.agendapro.agendamento.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository.AgendamentoHorarioProjection;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.service.ClienteService;
import com.minhaempresa.agendapro.email.ResendEmailService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.service.EmpresaService;
import com.minhaempresa.agendapro.horarioatendimento.entity.HorarioAtendimentoEntity;
import com.minhaempresa.agendapro.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;
import com.minhaempresa.agendapro.profissional.service.ProfissionalService;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import com.minhaempresa.agendapro.servico.service.ServicoService;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import com.minhaempresa.agendapro.whatsapp.repository.WhatsappLembretePagamentoRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceHorariosTest {

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
    @Mock WhatsappLembretePagamentoRepository lembretePagamentoRepository;
    @InjectMocks AgendamentoService agendamentoService;

    private EmpresaEntity criarEmpresa(Long id) {
        return EmpresaEntity.builder().id(id).timezone("America/Cuiaba").build();
    }

    private ServicoEntity criarServico(Long id, Long empresaId, int duracaoMinutos) {
        EmpresaEntity empresa = criarEmpresa(empresaId);
        return ServicoEntity.builder().id(id).nome("Consulta").duracaoMinutos(duracaoMinutos).valor(java.math.BigDecimal.valueOf(100)).empresa(empresa).build();
    }

    private HorarioAtendimentoEntity criarHorario(Long empresaId, LocalTime inicio, LocalTime fim) {
        EmpresaEntity empresa = criarEmpresa(empresaId);
        return HorarioAtendimentoEntity.builder()
                .id(1L).empresa(empresa).ativo(true)
                .horaInicio(inicio).horaFim(fim)
                .build();
    }

    private ProfissionalEntity criarProfissional(Long id, Long empresaId, boolean sistema) {
        EmpresaEntity empresa = criarEmpresa(empresaId);
        return ProfissionalEntity.builder().id(id).nome(sistema ? "Sem preferência" : "Dra. Marina").status(StatusCadastro.ATIVO).sistema(sistema).empresa(empresa).build();
    }

    private ProfissionalResponse criarProfissionalResponse(Long id, boolean sistema) {
        return new ProfissionalResponse(id, sistema ? "Sem preferência" : "Dra. Marina", null, null, StatusCadastro.ATIVO, 1L, sistema);
    }

    @Test
    void planoBasicoSemProfissionaisDeveListarHorariosNormalmente() {
        Long empresaId = 1L;
        Long servicoId = 1L;
        LocalDate data = LocalDate.now().plusDays(1);
        ServicoEntity servico = criarServico(servicoId, empresaId, 60);
        HorarioAtendimentoEntity horario = criarHorario(empresaId, LocalTime.of(8, 0), LocalTime.of(12, 0));
        ProfissionalEntity sistema = criarProfissional(10L, empresaId, true);

        when(servicoService.buscarEntidade(servicoId)).thenReturn(servico);
        when(profissionalService.listarPorEmpresa(empresaId)).thenReturn(List.of(
                criarProfissionalResponse(10L, true)
        ));
        when(profissionalService.buscarEntidade(10L)).thenReturn(sistema);
        when(horarioAtendimentoService.obterHorarioEfetivo(empresaId, data)).thenReturn(horario);
        when(agendamentoRepository.findByEmpresaIdAndDataHorarios(empresaId, data)).thenReturn(List.of());

        List<String> horarios = agendamentoService.horariosDisponiveis(empresaId, null, servicoId, data);

        assertFalse(horarios.isEmpty(), "Plano basico deve retornar horarios disponiveis");
        assertTrue(horarios.contains("08:00"));
        assertTrue(horarios.contains("09:00"));
        assertTrue(horarios.contains("10:00"));
        assertTrue(horarios.contains("11:00"));
        verify(agendamentoRepository).findByEmpresaIdAndDataHorarios(empresaId, data);
        verify(agendamentoRepository, never()).findByProfissionalIdAndData(any(), any());
    }

    @Test
    void planoProComProfissionaisDeveContinuarFuncionandoNormalmente() {
        Long empresaId = 1L;
        Long servicoId = 1L;
        Long profissionalId = 20L;
        LocalDate data = LocalDate.now().plusDays(1);
        ServicoEntity servico = criarServico(servicoId, empresaId, 60);
        HorarioAtendimentoEntity horario = criarHorario(empresaId, LocalTime.of(8, 0), LocalTime.of(12, 0));
        ProfissionalEntity profissional = criarProfissional(profissionalId, empresaId, false);

        when(servicoService.buscarEntidade(servicoId)).thenReturn(servico);
        when(profissionalService.buscarEntidade(profissionalId)).thenReturn(profissional);
        when(horarioAtendimentoService.obterHorarioEfetivo(empresaId, data)).thenReturn(horario);
        when(agendamentoRepository.findByProfissionalIdAndData(profissionalId, data)).thenReturn(List.of());

        List<String> horarios = agendamentoService.horariosDisponiveis(empresaId, profissionalId, servicoId, data);

        assertFalse(horarios.isEmpty());
        assertTrue(horarios.contains("08:00"));
        verify(agendamentoRepository).findByProfissionalIdAndData(profissionalId, data);
        verify(agendamentoRepository, never()).findByEmpresaIdAndDataHorarios(any(), any());
    }

    @Test
    void planoBasicoSemProfissionaisDeveConsiderarAgendamentosDeQualquerProfissional() {
        Long empresaId = 1L;
        Long servicoId = 1L;
        LocalDate data = LocalDate.now().plusDays(1);
        ServicoEntity servico = criarServico(servicoId, empresaId, 60);
        HorarioAtendimentoEntity horario = criarHorario(empresaId, LocalTime.of(8, 0), LocalTime.of(12, 0));
        ProfissionalEntity sistema = criarProfissional(10L, empresaId, true);

        AgendamentoHorarioProjection agendado = mock(AgendamentoHorarioProjection.class);
        when(agendado.getHoraInicio()).thenReturn(LocalTime.of(9, 0));
        when(agendado.getHoraFim()).thenReturn(LocalTime.of(10, 0));
        when(agendado.getStatus()).thenReturn(StatusAgendamento.CONFIRMADO);

        when(servicoService.buscarEntidade(servicoId)).thenReturn(servico);
        when(profissionalService.listarPorEmpresa(empresaId)).thenReturn(List.of(
                criarProfissionalResponse(10L, true)
        ));
        when(profissionalService.buscarEntidade(10L)).thenReturn(sistema);
        when(horarioAtendimentoService.obterHorarioEfetivo(empresaId, data)).thenReturn(horario);
        when(agendamentoRepository.findByEmpresaIdAndDataHorarios(empresaId, data)).thenReturn(List.of(agendado));

        List<String> horarios = agendamentoService.horariosDisponiveis(empresaId, null, servicoId, data);

        assertFalse(horarios.isEmpty());
        assertFalse(horarios.contains("09:00"), "Horario ocupado nao deve aparecer");
        assertTrue(horarios.contains("08:00"));
        assertTrue(horarios.contains("10:00"));
    }

    @Test
    void planoBasicoCriarAgendamentoDeveSalvarCorretamente() {
        Long empresaId = 1L;
        Long clienteId = 1L;
        Long servicoId = 1L;
        EmpresaEntity empresa = criarEmpresa(empresaId);
        ClienteEntity cliente = ClienteEntity.builder().id(clienteId).nome("Ana").empresa(empresa).build();
        ServicoEntity servico = criarServico(servicoId, empresaId, 60);
        ProfissionalEntity semPreferencia = criarProfissional(10L, empresaId, true);

        when(clienteService.buscarEntidade(clienteId)).thenReturn(cliente);
        when(servicoService.buscarEntidade(servicoId)).thenReturn(servico);
        when(profissionalService.buscarOuCriarAtendimentoPrincipal(empresa)).thenReturn(semPreferencia);
        when(empresaService.buscarEntidade(empresaId)).thenReturn(empresa);
        when(agendaBlockedDayService.diaBloqueado(any(), any(), any())).thenReturn(false);
        when(agendamentoRepository.existeConflitoDeHorario(any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(agendamentoRepository.save(any())).thenAnswer(inv -> {
            AgendamentoEntity a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = agendamentoService.criar(new CriarAgendamentoRequest(
                clienteId, servicoId, null, empresaId,
                LocalDate.now().plusDays(1), LocalTime.of(9, 0), "Teste basico"
        ));

        assertNotNull(response);
        assertEquals(LocalTime.of(10, 0), response.horaFim());
        assertEquals(StatusAgendamento.PENDENTE, response.status());
        verify(profissionalService).buscarOuCriarAtendimentoPrincipal(empresa);
    }

    @Test
    void planoProCriarAgendamentoDeveContinuarFuncionando() {
        Long empresaId = 1L;
        Long clienteId = 1L;
        Long servicoId = 1L;
        Long profissionalId = 20L;
        EmpresaEntity empresa = criarEmpresa(empresaId);
        ClienteEntity cliente = ClienteEntity.builder().id(clienteId).nome("Carlos").empresa(empresa).build();
        ServicoEntity servico = criarServico(servicoId, empresaId, 60);
        ProfissionalEntity profissional = criarProfissional(profissionalId, empresaId, false);

        when(clienteService.buscarEntidade(clienteId)).thenReturn(cliente);
        when(servicoService.buscarEntidade(servicoId)).thenReturn(servico);
        when(profissionalService.buscarEntidade(profissionalId)).thenReturn(profissional);
        when(empresaService.buscarEntidade(empresaId)).thenReturn(empresa);
        when(agendaBlockedDayService.diaBloqueado(any(), any(), any())).thenReturn(false);
        when(agendamentoRepository.existeConflitoDeHorario(any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(agendamentoRepository.save(any())).thenAnswer(inv -> {
            AgendamentoEntity a = inv.getArgument(0);
            a.setId(200L);
            return a;
        });
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = agendamentoService.criar(new CriarAgendamentoRequest(
                clienteId, servicoId, profissionalId, empresaId,
                LocalDate.now().plusDays(1), LocalTime.of(10, 0), null
        ));

        assertNotNull(response);
        assertEquals(LocalTime.of(11, 0), response.horaFim());
        assertEquals(StatusAgendamento.PENDENTE, response.status());
        verify(profissionalService).buscarEntidade(profissionalId);
        verify(profissionalService, never()).buscarOuCriarAtendimentoPrincipal(any());
    }

    @Test
    void planoBasicoHorariosComAgendamentosCanceladosDevemSerLiberados() {
        Long empresaId = 1L;
        Long servicoId = 1L;
        LocalDate data = LocalDate.now().plusDays(1);
        ServicoEntity servico = criarServico(servicoId, empresaId, 60);
        HorarioAtendimentoEntity horario = criarHorario(empresaId, LocalTime.of(8, 0), LocalTime.of(12, 0));
        ProfissionalEntity sistema = criarProfissional(10L, empresaId, true);

        AgendamentoHorarioProjection cancelado = mock(AgendamentoHorarioProjection.class);
        when(cancelado.getHoraInicio()).thenReturn(LocalTime.of(9, 0));
        when(cancelado.getHoraFim()).thenReturn(LocalTime.of(10, 0));
        when(cancelado.getStatus()).thenReturn(StatusAgendamento.CANCELADO);

        when(servicoService.buscarEntidade(servicoId)).thenReturn(servico);
        when(profissionalService.listarPorEmpresa(empresaId)).thenReturn(List.of(
                criarProfissionalResponse(10L, true)
        ));
        when(profissionalService.buscarEntidade(10L)).thenReturn(sistema);
        when(horarioAtendimentoService.obterHorarioEfetivo(empresaId, data)).thenReturn(horario);
        when(agendamentoRepository.findByEmpresaIdAndDataHorarios(empresaId, data)).thenReturn(List.of(cancelado));

        List<String> horarios = agendamentoService.horariosDisponiveis(empresaId, null, servicoId, data);

        assertTrue(horarios.contains("09:00"), "Agendamento cancelado deve liberar o horario");
    }
}
