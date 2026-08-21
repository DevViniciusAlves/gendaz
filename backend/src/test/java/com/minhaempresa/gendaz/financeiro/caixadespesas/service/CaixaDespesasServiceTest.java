package com.minhaempresa.gendaz.financeiro.caixadespesas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.financeiro.caixadespesas.dto.CaixaDespesasDtos.CaixaDespesasTotaisResponse;
import com.minhaempresa.gendaz.financeiro.caixadespesas.dto.CaixaDespesasDtos.HistoricoResponse;
import com.minhaempresa.gendaz.financeiro.caixadespesas.entity.CaixaDespesasLogEntity;
import com.minhaempresa.gendaz.financeiro.caixadespesas.enums.TipoCaixaDespesasLog;
import com.minhaempresa.gendaz.financeiro.caixadespesas.repository.CaixaDespesasLogRepository;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.Mock;

class CaixaDespesasServiceTest {

    @Mock
    private CaixaDespesasLogRepository logRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AssinaturaService assinaturaService;

    private CaixaDespesasService service;
    private EmpresaEntity empresa;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new CaixaDespesasService(logRepository, empresaRepository, usuarioRepository, assinaturaService);
        empresa = new EmpresaEntity();
        empresa.setId(1L);
        empresa.setCaixaTotal(BigDecimal.ZERO);
        empresa.setDespesasTotal(BigDecimal.ZERO);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(5L);
        usuario.setNome("Maria");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
    }

    @Test
    void deveAdicionarAoCaixaApenasNoPlanoPro() {
        when(assinaturaService.isPlanoPro(1L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.adicionarCaixaManual(1L, new BigDecimal("100"), "obs", 5L));

        when(assinaturaService.isPlanoPro(1L)).thenReturn(true);
        CaixaDespesasTotaisResponse resposta = service.adicionarCaixaManual(1L, new BigDecimal("100"), "obs", 5L);
        assertEquals(0, new BigDecimal("100").compareTo(resposta.caixaTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(resposta.despesasTotal()));
    }

    @Test
    void deveRejeitarValorInvalido() {
        when(assinaturaService.isPlanoPro(1L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.adicionarCaixaManual(1L, BigDecimal.ZERO, "obs", 5L));
        assertThrows(BusinessException.class, () -> service.adicionarCaixaManual(1L, new BigDecimal("-10"), "obs", 5L));
    }

    @Test
    void deveRemoverAdicaoManualDeCaixa() {
        when(assinaturaService.isPlanoPro(1L)).thenReturn(true);
        service.adicionarCaixaManual(1L, new BigDecimal("50"), "obs", 5L);

        CaixaDespesasLogEntity log = new CaixaDespesasLogEntity();
        log.setId(10L);
        log.setBusiness(empresa);
        log.setTipo(TipoCaixaDespesasLog.ADICAO_MANUAL_CAIXA);
        log.setValor(new BigDecimal("50"));
        when(logRepository.findByIdAndBusinessId(10L, 1L)).thenReturn(Optional.of(log));

        CaixaDespesasTotaisResponse resposta = service.removerCaixaManual(1L, 10L, 5L);
        assertEquals(0, BigDecimal.ZERO.compareTo(resposta.caixaTotal()));
    }

    @Test
    void naoDeveRemoverLogQueNaoSejaAdicaoManual() {
        when(assinaturaService.isPlanoPro(1L)).thenReturn(true);
        CaixaDespesasLogEntity log = new CaixaDespesasLogEntity();
        log.setId(11L);
        log.setBusiness(empresa);
        log.setTipo(TipoCaixaDespesasLog.PAGAMENTO_APROVADO);
        log.setValor(new BigDecimal("50"));
        when(logRepository.findByIdAndBusinessId(11L, 1L)).thenReturn(Optional.of(log));

        assertThrows(BusinessException.class, () -> service.removerCaixaManual(1L, 11L, 5L));
    }

    @Test
    void deveListarHistoricoPaginado() {
        when(assinaturaService.isPlanoPro(1L)).thenReturn(true);
        CaixaDespesasLogEntity log = new CaixaDespesasLogEntity();
        log.setId(1L);
        log.setTipo(TipoCaixaDespesasLog.ADICAO_MANUAL_CAIXA);
        log.setValor(new BigDecimal("10"));
        log.setDescricao("Maria - adicionou");
        log.setCriadoEm(LocalDateTime.now());
        when(logRepository.findByBusinessIdOrderByCriadoEmDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        HistoricoResponse resposta = service.listarHistorico(1L, 1, 10);
        assertEquals(1, resposta.itens().size());
        assertTrue(resposta.itens().get(0).positivo());
        assertEquals("CAIXA", resposta.itens().get(0).categoria());
    }

    @Test
    void deveRegistrarPagamentoAprovadoNoCaixa() {
        when(assinaturaService.isPlanoPro(1L)).thenReturn(true);

        PagamentoEntity pagamento = new PagamentoEntity();
        pagamento.setId(2L);
        pagamento.setEmpresa(empresa);
        pagamento.setValor(new BigDecimal("200"));

        AgendamentoEntity agendamento = new AgendamentoEntity();
        agendamento.setData(LocalDate.of(2026, 8, 21));
        ServicoEntity servico = new ServicoEntity();
        servico.setNome("Corte");
        agendamento.setServico(servico);
        pagamento.setAgendamento(agendamento);

        service.registrarPagamentoAprovado(pagamento);
        assertEquals(0, new BigDecimal("200").compareTo(empresa.getCaixaTotal()));
    }

    @Test
    void naoDeveRegistrarPagamentoAprovadoForaDoPlanoPro() {
        when(assinaturaService.isPlanoPro(1L)).thenReturn(false);
        PagamentoEntity pagamento = new PagamentoEntity();
        pagamento.setEmpresa(empresa);
        pagamento.setValor(new BigDecimal("200"));
        service.registrarPagamentoAprovado(pagamento);
        assertEquals(0, BigDecimal.ZERO.compareTo(empresa.getCaixaTotal()));
    }
}
