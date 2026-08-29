package com.minhaempresa.gendaz.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.agendamento.service.AgendamentoService;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.conversa.entity.ConversaEntity;
import com.minhaempresa.gendaz.conversa.service.ConversaService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.mensagem.entity.MensagemEntity;
import com.minhaempresa.gendaz.mensagem.enums.DirecaoMensagem;
import com.minhaempresa.gendaz.mensagem.enums.TipoMensagem;
import com.minhaempresa.gendaz.mensagem.repository.MensagemRepository;
import com.minhaempresa.gendaz.mensagem.service.MensagemService;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.enums.StatusPlano;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantIsolationServiceTest {
    @Mock MensagemRepository mensagemRepository;
    @Mock ConversaService conversaService;
    @InjectMocks MensagemService mensagemService;

    @Mock AgendamentoRepository agendamentoRepository;
    @Mock ClienteService clienteService;
    @InjectMocks AgendamentoService agendamentoService;

    @Mock AssinaturaRepository assinaturaRepository;
    @InjectMocks AssinaturaService assinaturaService;

    @AfterEach
    void clearCompanyContext() {
        CompanyContext.clear();
    }

    @Test
    void mensagensDevemValidarConversaNoTenantEUsarQueryEscopada() {
        EmpresaEntity empresaA = empresa(1L, StatusEmpresa.ATIVA);
        ConversaEntity conversaA = ConversaEntity.builder().id(10L).empresa(empresaA).build();
        MensagemEntity mensagemA = MensagemEntity.builder()
                .id(100L)
                .conversa(conversaA)
                .conteudo("mensagem A")
                .direcao(DirecaoMensagem.CLIENTE_PARA_EMPRESA)
                .tipo(TipoMensagem.TEXTO)
                .dataEnvio(LocalDateTime.now())
                .build();
        CompanyContext.setCompanyId(1L);
        when(conversaService.buscarEntidade(10L)).thenReturn(conversaA);
        when(mensagemRepository.findByConversaIdAndConversaEmpresaIdOrderByDataEnvioAsc(10L, 1L)).thenReturn(List.of(mensagemA));

        var response = mensagemService.listarPorConversa(10L);

        assertEquals(1, response.size());
        assertEquals("mensagem A", response.get(0).conteudo());
        verify(mensagemRepository).findByConversaIdAndConversaEmpresaIdOrderByDataEnvioAsc(10L, 1L);
    }

    @Test
    void mensagensCrossTenantDevemFalharAntesDeRetornarDados() {
        CompanyContext.setCompanyId(1L);
        when(conversaService.buscarEntidade(20L)).thenThrow(new ResourceNotFoundException("Conversa não encontrada."));

        assertThrows(ResourceNotFoundException.class, () -> mensagemService.listarPorConversa(20L));

        verify(mensagemRepository, never()).findByConversaIdAndConversaEmpresaIdOrderByDataEnvioAsc(eq(20L), eq(1L));
    }

    @Test
    void agendamentosPorClienteDevemValidarClienteEUsarEmpresaAutenticada() {
        EmpresaEntity empresaA = empresa(1L, StatusEmpresa.ATIVA);
        ClienteEntity clienteA = ClienteEntity.builder().id(10L).nome("Cliente A").empresa(empresaA).build();
        AgendamentoEntity agendamentoA = AgendamentoEntity.builder()
                .id(100L)
                .cliente(clienteA)
                .servico(com.minhaempresa.gendaz.servico.entity.ServicoEntity.builder()
                        .id(30L)
                        .nome("Servico A")
                        .valor(BigDecimal.TEN)
                        .empresa(empresaA)
                        .build())
                .profissional(com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity.builder()
                        .id(40L)
                        .nome("Profissional A")
                        .empresa(empresaA)
                        .build())
                .empresa(empresaA)
                .data(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(9, 0))
                .horaFim(LocalTime.of(10, 0))
                .status(StatusAgendamento.PENDENTE)
                .build();
        CompanyContext.setCompanyId(1L);
        when(clienteService.buscarEntidade(10L)).thenReturn(clienteA);
        when(agendamentoRepository.findByEmpresaIdAndClienteId(1L, 10L)).thenReturn(List.of(agendamentoA));

        var response = agendamentoService.listarPorCliente(10L);

        assertEquals(1, response.size());
        assertEquals(1L, response.get(0).empresaId());
        verify(agendamentoRepository).findByEmpresaIdAndClienteId(1L, 10L);
        verify(agendamentoRepository, never()).findByClienteId(10L);
    }

    @Test
    void agendamentosPorClienteCrossTenantDevemFalharSemConsultarAgendamentos() {
        CompanyContext.setCompanyId(1L);
        when(clienteService.buscarEntidade(20L)).thenThrow(new ResourceNotFoundException("Cliente não encontrado."));

        assertThrows(ResourceNotFoundException.class, () -> agendamentoService.listarPorCliente(20L));

        verify(agendamentoRepository, never()).findByEmpresaIdAndClienteId(eq(1L), eq(20L));
        verify(agendamentoRepository, never()).findByClienteId(20L);
    }

    @Test
    void assinaturasDevemValidarEmpresaAntesDeListar() {
        EmpresaEntity empresaA = empresa(1L, StatusEmpresa.ATIVA);
        AssinaturaEntity assinaturaA = assinatura(100L, empresaA, StatusAssinatura.ATIVA, LocalDate.now(), LocalDate.now().plusDays(10));
        CompanyContext.setCompanyId(1L);
        when(assinaturaRepository.findByEmpresaId(1L)).thenReturn(List.of(assinaturaA));

        var response = assinaturaService.listarPorEmpresa(1L);

        assertEquals(1, response.size());
        assertEquals(1L, response.get(0).empresaId());
        verify(assinaturaRepository).findByEmpresaId(1L);
    }

    @Test
    void assinaturasCrossTenantDevemFalharAntesDeListar() {
        CompanyContext.setCompanyId(1L);

        assertThrows(ResourceNotFoundException.class, () -> assinaturaService.listarPorEmpresa(2L));

        verify(assinaturaRepository, never()).findByEmpresaId(2L);
    }

    @Test
    void assinaturaAtualCrossTenantDeveFalharAntesDeProcessarExpiracao() {
        EmpresaEntity empresaB = empresa(2L, StatusEmpresa.ATIVA);
        AssinaturaEntity vencidaB = assinatura(200L, empresaB, StatusAssinatura.ATIVA, LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        CompanyContext.setCompanyId(1L);

        assertThrows(ResourceNotFoundException.class, () -> assinaturaService.buscarAtualResponsePorEmpresa(2L));

        assertEquals(StatusAssinatura.ATIVA, vencidaB.getStatus());
        assertEquals(StatusEmpresa.ATIVA, empresaB.getStatus());
        verify(assinaturaRepository, never()).findByEmpresaId(2L);
        verify(assinaturaRepository, never()).save(vencidaB);
    }

    @Test
    void chamadasTenantFacingDevemFalharSemCompanyContext() {
        assertThrows(RuntimeException.class, () -> mensagemService.listarPorConversa(10L));
        assertThrows(RuntimeException.class, () -> agendamentoService.listarPorCliente(10L));
        assertThrows(RuntimeException.class, () -> assinaturaService.listarPorEmpresa(1L));
        assertThrows(RuntimeException.class, () -> assinaturaService.buscarAtualResponsePorEmpresa(1L));
    }

    @Test
    void assinaturaAtualInternaContinuaSemCompanyContext() {
        EmpresaEntity empresaA = empresa(1L, StatusEmpresa.ATIVA);
        AssinaturaEntity assinaturaA = assinatura(100L, empresaA, StatusAssinatura.ATIVA, LocalDate.now(), LocalDate.now().plusDays(10));
        when(assinaturaRepository.findByEmpresaId(1L)).thenReturn(List.of(assinaturaA));

        Optional<AssinaturaEntity> atual = assinaturaService.buscarAtualPorEmpresa(1L);

        assertEquals(100L, atual.orElseThrow().getId());
    }

    private EmpresaEntity empresa(Long id, StatusEmpresa status) {
        return EmpresaEntity.builder()
                .id(id)
                .nomeFantasia("Empresa " + id)
                .email("empresa" + id + "@gendaz.test")
                .status(status)
                .timezone("America/Cuiaba")
                .build();
    }

    private AssinaturaEntity assinatura(Long id, EmpresaEntity empresa, StatusAssinatura status, LocalDate inicio, LocalDate fim) {
        PlanoEntity plano = PlanoEntity.builder()
                .id(id)
                .nome("Plano " + id)
                .descrição("Plano")
                .valorMensal(BigDecimal.TEN)
                .status(StatusPlano.ATIVO)
                .build();
        return AssinaturaEntity.builder()
                .id(id)
                .empresa(empresa)
                .plano(plano)
                .status(status)
                .dataInicio(inicio)
                .dataFim(fim)
                .build();
    }
}
