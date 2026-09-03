package com.minhaempresa.gendaz.pagamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.financeiro.caixadespesas.repository.CaixaDespesasLogRepository;
import com.minhaempresa.gendaz.financeiro.caixadespesas.service.CaixaDespesasService;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Parte 13 (estrutural) — movimentacoes financeiras carregam a empresa com
 * lock pessimista, serializando atualizacoes concorrentes de caixaTotal.
 * A prova comportamental com threads reais esta em
 * PagamentoConcorrenciaIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class CaixaLockTest {
    @Mock CaixaDespesasLogRepository logRepository;
    @Mock EmpresaRepository empresaRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock AssinaturaService assinaturaService;
    @Mock LogAtividadeService logAtividadeService;

    @Test
    void registrarPagamentoAprovadoCarregaEmpresaComLock() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L)
                .caixaTotal(BigDecimal.ZERO).despesasTotal(BigDecimal.ZERO).build();
        when(empresaRepository.findByIdWithLock(1L)).thenReturn(java.util.Optional.of(empresa));
        when(empresaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assinaturaService.isPlanoComRecursosAvancados(1L)).thenReturn(true);
        ClienteEntity cliente = ClienteEntity.builder().id(2L).nome("Ana").build();
        PagamentoEntity pagamento = PagamentoEntity.builder()
                .id(9L).empresa(empresa).cliente(cliente)
                .valor(new BigDecimal("100.00")).status(StatusPagamento.PAGO).build();

        new CaixaDespesasService(logRepository, empresaRepository, usuarioRepository,
                assinaturaService, logAtividadeService).registrarPagamentoAprovado(pagamento);

        verify(empresaRepository).findByIdWithLock(1L);
        verify(empresaRepository, org.mockito.Mockito.never()).findById(1L);
        assertEquals(0, new BigDecimal("100.00").compareTo(empresa.getCaixaTotal()));
    }
}
