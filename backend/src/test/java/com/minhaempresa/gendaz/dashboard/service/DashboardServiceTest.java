package com.minhaempresa.gendaz.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.conversa.repository.ConversaRepository;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.DashboardResumoResponse;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DashboardServiceTest {
    @Mock UsuarioRepository usuarioRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ServicoRepository servicoRepository;
    @Mock ProfissionalRepository profissionalRepository;
    @Mock AssinaturaService assinaturaService;
    @Mock ConversaRepository conversaRepository;
    @Mock AgendamentoRepository agendamentoRepository;
    @Mock PagamentoRepository pagamentoRepository;

    private DashboardService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new DashboardService(
                usuarioRepository, clienteRepository, servicoRepository, profissionalRepository,
                assinaturaService, conversaRepository, agendamentoRepository, pagamentoRepository);
    }

    private UsuarioEntity usuarioComEmpresa() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).nomeFantasia("Empresa A").build();
        return UsuarioEntity.builder().id(7L).empresa(empresa).build();
    }

    private void preparaResumoBasico() {
        UsuarioEntity usuario = usuarioComEmpresa();
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(conversaRepository.countAbertasByEmpresaId(1L)).thenReturn(0L);
        when(clienteRepository.countByEmpresaIdAndStatusNot(1L, StatusCadastro.EXCLUIDO)).thenReturn(0L);
        when(servicoRepository.countAtivosByEmpresaId(1L)).thenReturn(0L);
        when(profissionalRepository.countAtivosByEmpresaId(1L)).thenReturn(0L);
        when(agendamentoRepository.findTop5ByEmpresaIdAndStatusInAndDataGreaterThanEqualAndClienteStatusNotOrderByDataAscHoraInicioAsc(
                anyLong(), any(), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.findTop10ByEmpresaIdAndClienteStatusNotOrderByDataDescHoraInicioDesc(anyLong(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendados(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(pagamentoRepository.resumoReceitaPorDia(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(pagamentoRepository.findTop5ByEmpresaIdAndStatusOrderByIdDescForFinanceiro(anyLong(), any()))
                .thenReturn(List.of());
        when(assinaturaService.buscarAtualPorEmpresa(1L)).thenReturn(Optional.empty());
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(1L), any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void agendamentosHojeExcluiCancelado() {
        preparaResumoBasico();
        when(agendamentoRepository.countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(1L, LocalDate.now(), StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO))
                .thenReturn(2L);

        DashboardResumoResponse response = service.resumo(7L);

        assertEquals(2L, response.agendamentosHoje());
        verify(agendamentoRepository).countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(
                eq(1L), eq(LocalDate.now()), eq(StatusAgendamento.CANCELADO), eq(StatusCadastro.EXCLUIDO));
        verify(agendamentoRepository, never()).countByEmpresaIdAndData(anyLong(), any());
    }

    @Test
    void pendenciaCobrancaNaoIncluiPagoNemCancelado() {
        preparaResumoBasico();
        when(agendamentoRepository.countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(1L, LocalDate.now(), StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO))
                .thenReturn(0L);

        service.resumo(7L);

        ArgumentCaptor<List<StatusPagamento>> statusCaptor = ArgumentCaptor.forClass(List.class);
        verify(pagamentoRepository, org.mockito.Mockito.times(2))
                .somarValorByEmpresaIdAndStatusIn(eq(1L), statusCaptor.capture());
        // A segunda chamada e a de pendencia de cobranca (STATUS_PENDENTE)
        List<StatusPagamento> statuses = statusCaptor.getAllValues().get(1);
        assertEquals(List.of(StatusPagamento.PENDENTE, StatusPagamento.PAYMENT_PENDING), statuses);
        // CANCELADO nao faz parte dos status de pendencia de cobranca
    }

    @Test
    void pendenciaPagamentoNaListaUsoNaoIncluiCancelado() {
        preparaResumoBasico();
        when(agendamentoRepository.countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(1L, LocalDate.now(), StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO))
                .thenReturn(0L);

        service.resumo(7L);

        verify(pagamentoRepository).findTop5ByEmpresaIdAndStatusOrderByIdDescForFinanceiro(
                eq(1L), eq(StatusPagamento.PENDENTE));
    }
}
