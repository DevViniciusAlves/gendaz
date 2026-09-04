package com.minhaempresa.gendaz.servico.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.empresa.service.RamoDeteccaoService;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Partes 7 e 8 — servico com historico e INATIVADO (nunca destruido com a
 * cadeia); servico sem vinculo pode ser removido; busca operacional exige
 * ATIVO enquanto a historica continua resolvendo INATIVO.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoExclusaoHistoricoTest {
    @Mock ServicoRepository servicoRepository;
    @Mock EmpresaService empresaService;
    @Mock RamoDeteccaoService ramoDeteccaoService;
    @Mock AgendamentoRepository agendamentoRepository;
    @Mock PagamentoRepository pagamentoRepository;
    @Mock SanitizacaoService sanitizacaoService;
    @Mock LogAtividadeService logAtividadeService;
    ServicoService service;

    @BeforeEach
    void setup() {
        service = new ServicoService(servicoRepository, empresaService, ramoDeteccaoService,
                agendamentoRepository, pagamentoRepository, sanitizacaoService, logAtividadeService);
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private ServicoEntity servico(StatusCadastro status) {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).build();
        return ServicoEntity.builder()
                .id(7L).nome("Corte").duracaoMinutos(30)
                .valor(new BigDecimal("100.00")).status(status).empresa(empresa).build();
    }

    @Test
    void excluirServicoComHistoricoInativaSemApagarCadeia() {
        when(servicoRepository.findById(7L)).thenReturn(Optional.of(servico(StatusCadastro.ATIVO)));
        when(agendamentoRepository.existsByServicoId(7L)).thenReturn(true);
        when(servicoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.excluirOuInativar(7L, 1L);

        assertEquals(StatusCadastro.INATIVO, response.status());
        verify(servicoRepository, never()).delete(any());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(any(), any());
    }

    @Test
    void excluirServicoSemVinculoRemoveFisicamente() {
        when(servicoRepository.findById(7L)).thenReturn(Optional.of(servico(StatusCadastro.ATIVO)));
        when(agendamentoRepository.existsByServicoId(7L)).thenReturn(false);

        service.excluirOuInativar(7L, 1L);

        verify(servicoRepository).delete(any());
    }

    @Test
    void buscaOperacionalExigeAtivoEHistoricaAceitaInativo() {
        when(servicoRepository.findById(7L)).thenReturn(Optional.of(servico(StatusCadastro.INATIVO)));

        assertThrows(BusinessException.class, () -> service.buscarEntidadeOperacional(7L));
        // Historico continua resolvendo o servico inativo usado antigamente.
        assertEquals(7L, service.buscarEntidade(7L).getId());
    }

    @Test
    void buscaOperacionalAceitaAtivo() {
        when(servicoRepository.findById(7L)).thenReturn(Optional.of(servico(StatusCadastro.ATIVO)));

        assertEquals(7L, service.buscarEntidadeOperacional(7L).getId());
    }
}
