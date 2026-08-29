package com.minhaempresa.gendaz.meugendazpromocao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.meugendazpromocao.dto.MeuGendazPromocaoDtos.CupomAplicadoResult;
import com.minhaempresa.gendaz.meugendazpromocao.entity.MeuGendazPromocaoEntity;
import com.minhaempresa.gendaz.meugendazpromocao.repository.MeuGendazPromocaoNotificacaoRepository;
import com.minhaempresa.gendaz.meugendazpromocao.repository.MeuGendazPromocaoRepository;
import com.minhaempresa.gendaz.meugendazpromocao.repository.MeuGendazPromocaoUsoRepository;
import com.minhaempresa.gendaz.promocao.repository.PromocaoRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeuGendazPromocaoServiceTest {

    @Mock
    private MeuGendazPromocaoRepository promocaoRepository;
    @Mock
    private MeuGendazPromocaoUsoRepository usoRepository;
    @Mock
    private MeuGendazPromocaoNotificacaoRepository notificacaoRepository;
    @Mock
    private MeuGendazPromocaoSyncService syncService;
    @Mock
    private PromocaoRepository adminPromocaoRepository;

    private MeuGendazPromocaoService service;

    private EmpresaEntity empresa;
    private EmpresaEntity outraEmpresa;
    private ClienteEntity cliente;
    private ServicoEntity servico;

    @BeforeEach
    void setup() {
        service = new MeuGendazPromocaoService(
                promocaoRepository,
                usoRepository,
                notificacaoRepository,
                syncService,
                adminPromocaoRepository
        );
        empresa = EmpresaEntity.builder().id(1L).build();
        outraEmpresa = EmpresaEntity.builder().id(2L).build();
        cliente = ClienteEntity.builder().id(7L).empresa(empresa).build();
        servico = ServicoEntity.builder().id(3L).nome("Corte").valor(new BigDecimal("100.00")).build();
    }

    private MeuGendazPromocaoEntity promocao(String codigo, String tipo, String valor) {
        return promocao(codigo, tipo, valor, new BigDecimal("999"), null, null);
    }

    private MeuGendazPromocaoEntity promocao(String codigo, String tipo, String valor, BigDecimal limite, Integer quantidadeUsada, StatusCadastro status) {
        Set<ServicoEntity> servicos = new HashSet<>();
        servicos.add(servico);
        return MeuGendazPromocaoEntity.builder()
                .id(5L)
                .empresa(empresa)
                .promocaoOrigemId(999L)
                .codigo(codigo)
                .descricao("Cupom de teste")
                .tipo(tipo)
                .valor(new BigDecimal(valor))
                .dataInicio(LocalDateTime.now().minusDays(1))
                .dataFim(LocalDateTime.now().plusDays(1))
                .quantidadeLimite(limite == null ? null : limite.intValue())
                .quantidadeUsada(quantidadeUsada == null ? 0 : quantidadeUsada)
                .status(status == null ? StatusCadastro.ATIVO : status)
                .aplicarTodosServicos(false)
                .servicos(servicos)
                .build();
    }

    private void mockarCupomValido(MeuGendazPromocaoEntity promocao) {
        when(promocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(eq(1L), eq(promocao.getCodigo())))
                .thenReturn(Optional.of(promocao));
        when(promocaoRepository.findByIdComLock(promocao.getId())).thenReturn(Optional.of(promocao));
        when(usoRepository.existsByPromocaoIdAndClienteId(any(), any())).thenReturn(false);
        when(promocaoRepository.save(any(MeuGendazPromocaoEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(usoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void cupomFixo50Servico100Desconta50() {
        MeuGendazPromocaoEntity promocao = promocao("TESTE50", "VALOR_FIXO", "50.00");
        mockarCupomValido(promocao);

        CupomAplicadoResult resultado = service.aplicarCupomAoAgendamento(cliente, empresa, servico, "TESTE50", 11L);

        assertEquals(new BigDecimal("50.00"), resultado.desconto());
        assertEquals("TESTE50", resultado.codigo());
        assertEquals("VALOR_FIXO", resultado.tipo());
        assertEquals(new BigDecimal("50.00"), resultado.valorPromocao());
        assertEquals(999L, resultado.promocaoOrigemId());
        verify(usoRepository).save(any());
        assertEquals(1, promocao.getQuantidadeUsada());
    }

    @Test
    void cupomPercentual50Servico100Desconta50() {
        MeuGendazPromocaoEntity promocao = promocao("TESTE50P", "PERCENTUAL", "50.00");
        mockarCupomValido(promocao);

        CupomAplicadoResult resultado = service.aplicarCupomAoAgendamento(cliente, empresa, servico, "TESTE50P", 11L);

        assertEquals(0, new BigDecimal("50.00").compareTo(resultado.desconto()));
    }

    @Test
    void cupomFixoMaiorQueServicoTemDescontoLimitadoAoServico() {
        MeuGendazPromocaoEntity promocao = promocao("TESTE150", "VALOR_FIXO", "150.00");
        mockarCupomValido(promocao);

        CupomAplicadoResult resultado = service.aplicarCupomAoAgendamento(cliente, empresa, servico, "TESTE150", 11L);

        assertEquals(new BigDecimal("100.00"), resultado.desconto());
    }

    @Test
    void cupomEmBrancoNaoRegistraUso() {
        assertNull(service.aplicarCupomAoAgendamento(cliente, empresa, servico, "  ", 11L));
        assertNull(service.aplicarCupomAoAgendamento(cliente, empresa, servico, null, 11L));
        verify(usoRepository, never()).save(any());
    }

    @Test
    void cupomExpiradoRejeitado() {
        MeuGendazPromocaoEntity promocao = promocao("EXPIRADO", "VALOR_FIXO", "50.00");
        promocao.setDataFim(LocalDateTime.now().minusHours(1));
        mockarCupomValido(promocao);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.aplicarCupomAoAgendamento(cliente, empresa, servico, "EXPIRADO", 11L));
        assertTrue(ex.getMessage().toLowerCase().contains("expirado") || ex.getMessage().toLowerCase().contains("invalido"));
    }

    @Test
    void cupomInativoRejeitado() {
        MeuGendazPromocaoEntity promocao = promocao("INATIVO", "VALOR_FIXO", "50.00", null, null, StatusCadastro.INATIVO);
        mockarCupomValido(promocao);

        assertThrows(IllegalArgumentException.class,
                () -> service.aplicarCupomAoAgendamento(cliente, empresa, servico, "INATIVO", 11L));
        verify(usoRepository, never()).save(any());
    }

    @Test
    void cupomLimitadoAOutroServicoRejeitado() {
        ServicoEntity outroServico = ServicoEntity.builder().id(9L).valor(new BigDecimal("80.00")).build();
        MeuGendazPromocaoEntity promocao = promocao("OUTRO", "VALOR_FIXO", "20.00");
        promocao.setServicos(new HashSet<>(Set.of(outroServico)));
        mockarCupomValido(promocao);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.aplicarCupomAoAgendamento(cliente, empresa, servico, "OUTRO", 11L));
        assertTrue(ex.getMessage().toLowerCase().contains("servico"));
    }

    @Test
    void cupomAplicarTodosServicosAceitoParaQualquerServico() {
        ServicoEntity qualquer = ServicoEntity.builder().id(42L).valor(new BigDecimal("200.00")).build();
        MeuGendazPromocaoEntity promocao = promocao("TODOS", "VALOR_FIXO", "30.00");
        promocao.setAplicarTodosServicos(true);
        promocao.setServicos(new HashSet<>());
        when(promocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(eq(1L), eq("TODOS"))).thenReturn(Optional.of(promocao));
        when(promocaoRepository.findByIdComLock(5L)).thenReturn(Optional.of(promocao));
        when(usoRepository.existsByPromocaoIdAndClienteId(any(), any())).thenReturn(false);
        when(promocaoRepository.save(any(MeuGendazPromocaoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(usoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CupomAplicadoResult resultado = service.aplicarCupomAoAgendamento(cliente, empresa, qualquer, "TODOS", 11L);

        assertEquals(new BigDecimal("30.00"), resultado.desconto());
    }

    @Test
    void cupomDeOutraEmpresaRejeitado() {
        when(promocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(eq(1L), eq("FORA"))).thenReturn(Optional.empty());
        when(adminPromocaoRepository.findByEmpresaIdOrderByDataCriacaoDesc(1L)).thenReturn(java.util.List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.aplicarCupomAoAgendamento(cliente, empresa, servico, "FORA", 11L));
        assertTrue(ex.getMessage().toLowerCase().contains("invalido"));
        verify(usoRepository, never()).save(any());
    }

    @Test
    void clienteQueJaUsouRejeitado() {
        MeuGendazPromocaoEntity promocao = promocao("USADO", "VALOR_FIXO", "50.00");
        when(promocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(eq(1L), eq("USADO"))).thenReturn(Optional.of(promocao));
        when(promocaoRepository.findByIdComLock(5L)).thenReturn(Optional.of(promocao));
        when(usoRepository.existsByPromocaoIdAndClienteId(eq(5L), eq(7L))).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.aplicarCupomAoAgendamento(cliente, empresa, servico, "USADO", 11L));
        assertTrue(ex.getMessage().toLowerCase().contains("ja usou"));
        verify(usoRepository, never()).save(any());
    }

    @Test
    void quantidadeUsadaNaoUltrapassaLimite() {
        MeuGendazPromocaoEntity promocao = promocao("FIM", "VALOR_FIXO", "50.00", new BigDecimal("1"), 1, null);
        mockarCupomValido(promocao);

        assertThrows(IllegalArgumentException.class,
                () -> service.aplicarCupomAoAgendamento(cliente, empresa, servico, "FIM", 11L));
        verify(usoRepository, never()).save(any());
        assertEquals(1, promocao.getQuantidadeUsada());
    }

    @Test
    void segundoUsoAposEsgotarLimiteRejeitado() {
        MeuGendazPromocaoEntity promocao = promocao("LAST", "VALOR_FIXO", "50.00", new BigDecimal("1"), 0, null);
        mockarCupomValido(promocao);

        CupomAplicadoResult primeiro = service.aplicarCupomAoAgendamento(cliente, empresa, servico, "LAST", 11L);
        assertEquals(new BigDecimal("50.00"), primeiro.desconto());
        assertEquals(1, promocao.getQuantidadeUsada());

        assertThrows(IllegalArgumentException.class,
                () -> service.aplicarCupomAoAgendamento(cliente, empresa, servico, "LAST", 7L));
        verify(usoRepository).save(any());
    }

    @Test
    void cupomComCodigoDiferenteDeCaseAceito() {
        MeuGendazPromocaoEntity promocao = promocao("TESTE", "VALOR_FIXO", "50.00");
        when(promocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(eq(1L), eq("teste"))).thenReturn(Optional.of(promocao));
        when(promocaoRepository.findByIdComLock(promocao.getId())).thenReturn(Optional.of(promocao));
        when(usoRepository.existsByPromocaoIdAndClienteId(any(), any())).thenReturn(false);
        when(promocaoRepository.save(any(MeuGendazPromocaoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(usoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CupomAplicadoResult resultado = service.aplicarCupomAoAgendamento(cliente, empresa, servico, " teste ", 11L);

        assertEquals(new BigDecimal("50.00"), resultado.desconto());
        verify(promocaoRepository).findByEmpresaIdAndCodigoIgnoreCase(eq(1L), eq("teste"));
    }
}