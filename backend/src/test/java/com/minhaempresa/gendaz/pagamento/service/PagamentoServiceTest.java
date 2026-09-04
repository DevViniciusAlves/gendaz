package com.minhaempresa.gendaz.pagamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.service.AgendamentoService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.financeiro.caixadespesas.service.CaixaDespesasService;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarStatusPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoResponse;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PagamentoServiceTest {
    @Mock
    private PagamentoRepository pagamentoRepository;
    @Mock
    private AgendamentoService agendamentoService;
    @Mock
    private ClienteService clienteService;
    @Mock
    private EmpresaService empresaService;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private CaixaDespesasService caixaDespesasService;
    @Mock
    private UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    @Mock
    private LogAtividadeService logAtividadeService;
    @Mock
    private FormaPagamentoEmpresaService formaPagamentoEmpresaService;

    private PagamentoService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new PagamentoService(
                pagamentoRepository, agendamentoService, clienteService, empresaService,
                empresaRepository, mock(com.minhaempresa.gendaz.plano.service.PlanoService.class),
                mock(com.minhaempresa.gendaz.assinatura.service.AssinaturaService.class),
                mock(com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository.class),
                mock(com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoCobrancaRepository.class),
                mock(com.minhaempresa.gendaz.pagamento.gateway.PaymentGateway.class),
                mock(com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayProperties.class),
                mock(AdminAuditService.class), formaPagamentoEmpresaService,
                caixaDespesasService, usuarioAutenticadoProvider, logAtividadeService
        );
        CompanyContext.setCompanyId(9L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void cancelarPagamentoPagoDevePreservarDados() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(9L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(2L).nome("Ana").build();
        AgendamentoEntity agendamento = AgendamentoEntity.builder().id(10L).build();

        PagamentoEntity pagamento = PagamentoEntity.builder()
                .id(1L)
                .empresa(empresa)
                .cliente(cliente)
                .agendamento(agendamento)
                .valor(new BigDecimal("300.00"))
                .metodoPagamento(MetodoPagamento.CREDITO)
                .parcelas(3)
                .status(StatusPagamento.PAGO)
                .dataPagamento(LocalDateTime.of(2026, 8, 10, 10, 0))
                .build();

        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L)))
                .thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PagamentoResponse response = service.atualizarStatus(1L,
                new AtualizarStatusPagamentoRequest(StatusPagamento.CANCELADO));

        assertEquals(StatusPagamento.CANCELADO, response.status());
        assertNotNull(response.dataPagamento(), "dataPagamento deve ser preservado");
        assertNotNull(response.metodoPagamento(), "metodoPagamento deve ser preservado");
        assertNotNull(response.parcelas(), "parcelas deve ser preservado");
    }

    @Test
    void cancelarPagamentoPagoPreservaDataPagamento() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(9L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(2L).nome("Ana").build();

        LocalDateTime dataPagamentoOriginal = LocalDateTime.of(2026, 8, 10, 14, 30);
        PagamentoEntity pagamento = PagamentoEntity.builder()
                .id(1L)
                .empresa(empresa)
                .cliente(cliente)
                .valor(new BigDecimal("100.00"))
                .metodoPagamento(MetodoPagamento.PIX)
                .status(StatusPagamento.PAGO)
                .dataPagamento(dataPagamentoOriginal)
                .build();

        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L)))
                .thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PagamentoResponse response = service.atualizarStatus(1L,
                new AtualizarStatusPagamentoRequest(StatusPagamento.CANCELADO));

        assertEquals(dataPagamentoOriginal, response.dataPagamento(),
                "dataPagamento deve permanecer igual ao original");
    }

    @Test
    void cancelarPagamentoPagoPreservaMetodoEParcelas() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(9L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(2L).nome("Ana").build();

        PagamentoEntity pagamento = PagamentoEntity.builder()
                .id(1L)
                .empresa(empresa)
                .cliente(cliente)
                .valor(new BigDecimal("300.00"))
                .metodoPagamento(MetodoPagamento.CREDITO)
                .parcelas(3)
                .status(StatusPagamento.PAGO)
                .dataPagamento(LocalDateTime.of(2026, 8, 10, 10, 0))
                .build();

        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L)))
                .thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PagamentoResponse response = service.atualizarStatus(1L,
                new AtualizarStatusPagamentoRequest(StatusPagamento.CANCELADO));

        assertEquals(MetodoPagamento.CREDITO, response.metodoPagamento(),
                "metodoPagamento deve permanecer CREDITO");
        assertEquals(3, response.parcelas(),
                "parcelas deve permanecer 3");
    }

    @Test
    void voltarParaPendenteDeveLimparDados() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(9L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(2L).nome("Ana").build();

        PagamentoEntity pagamento = PagamentoEntity.builder()
                .id(1L)
                .empresa(empresa)
                .cliente(cliente)
                .valor(new BigDecimal("100.00"))
                .metodoPagamento(MetodoPagamento.PIX)
                .status(StatusPagamento.PAGO)
                .dataPagamento(LocalDateTime.of(2026, 8, 10, 10, 0))
                .build();

        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L)))
                .thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioAutenticadoProvider.exigirUsuarioId()).thenReturn(1L);

        PagamentoResponse response = service.atualizarStatus(1L,
                new AtualizarStatusPagamentoRequest(StatusPagamento.PENDENTE));

        assertEquals(StatusPagamento.PENDENTE, response.status());
    }

    @Test
    void canceladoContinuaVisivelNoFinanceiro() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(9L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(2L).nome("Ana").build();

        PagamentoEntity pagamento = PagamentoEntity.builder()
                .id(1L)
                .empresa(empresa)
                .cliente(cliente)
                .valor(new BigDecimal("300.00"))
                .metodoPagamento(MetodoPagamento.CREDITO)
                .parcelas(3)
                .status(StatusPagamento.PAGO)
                .dataPagamento(LocalDateTime.of(2026, 8, 10, 10, 0))
                .build();

        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L)))
                .thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PagamentoResponse response = service.atualizarStatus(1L,
                new AtualizarStatusPagamentoRequest(StatusPagamento.CANCELADO));

        assertNotNull(response.dataPagamento(),
                "Cancelado deve ter dataPagamento para aparecer no financeiro");
        assertNotNull(response.metodoPagamento(),
                "Cancelado deve ter metodoPagamento");
    }

    @Test
    void cancelarPagamentoPendenteDoAgendamentoCancelaQuandoPendente() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(2L).nome("Ana").build();
        PagamentoEntity pagamento = PagamentoEntity.builder()
                .id(1L).empresa(empresa).cliente(cliente).agendamento(AgendamentoEntity.builder().id(10L).build())
                .valor(new BigDecimal("100.00")).status(StatusPagamento.PENDENTE).build();

        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L)).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelarPagamentoPendenteDoAgendamento(10L, 1L);

        assertEquals(StatusPagamento.CANCELADO, pagamento.getStatus());
        verify(pagamentoRepository).save(pagamento);
    }

    @Test
    void cancelarPagamentoPendenteDoAgendamentoPreservaPagamentoPago() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(2L).nome("Ana").build();
        LocalDateTime dataPagamento = LocalDateTime.of(2026, 8, 10, 10, 0);
        PagamentoEntity pagamento = PagamentoEntity.builder()
                .id(1L).empresa(empresa).cliente(cliente).agendamento(AgendamentoEntity.builder().id(10L).build())
                .valor(new BigDecimal("100.00")).metodoPagamento(MetodoPagamento.PIX)
                .status(StatusPagamento.PAGO).dataPagamento(dataPagamento).build();

        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L)).thenReturn(Optional.of(pagamento));

        service.cancelarPagamentoPendenteDoAgendamento(10L, 1L);

        assertEquals(StatusPagamento.PAGO, pagamento.getStatus());
        assertEquals(dataPagamento, pagamento.getDataPagamento(), "dataPagamento nao pode ser alterado");
        assertEquals(MetodoPagamento.PIX, pagamento.getMetodoPagamento(), "metodoPagamento nao pode ser alterado");
        verify(pagamentoRepository, never()).save(pagamento);
    }

    @Test
    void cancelarPagamentoPendenteDoAgendamentoJaCanceladoEhIdempotente() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(2L).nome("Ana").build();
        PagamentoEntity pagamento = PagamentoEntity.builder()
                .id(1L).empresa(empresa).cliente(cliente).agendamento(AgendamentoEntity.builder().id(10L).build())
                .valor(new BigDecimal("100.00")).status(StatusPagamento.CANCELADO).build();

        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L)).thenReturn(Optional.of(pagamento));

        service.cancelarPagamentoPendenteDoAgendamento(10L, 1L);

        assertEquals(StatusPagamento.CANCELADO, pagamento.getStatus());
        verify(pagamentoRepository, never()).save(pagamento);
    }

    @Test
    void cancelarPagamentoPendenteDoAgendamentoSemPagamentoNaoFazNada() {
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L)).thenReturn(Optional.empty());

        service.cancelarPagamentoPendenteDoAgendamento(10L, 1L);

        verify(pagamentoRepository, never()).save(any());
    }

    private PagamentoEntity pagamentoPendente(Long id, BigDecimal valor) {
        EmpresaEntity empresa = EmpresaEntity.builder().id(9L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(2L).nome("Joao").build();
        return PagamentoEntity.builder()
                .id(id).empresa(empresa).cliente(cliente)
                .valor(valor).status(StatusPagamento.PENDENTE).build();
    }

    @Test
    void marcarPagoPendenteParaPagoRegistraEntradaNoCaixaUmaVez() {
        PagamentoEntity pagamento = pagamentoPendente(1L, new BigDecimal("200.00"));
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(formaPagamentoEmpresaService.normalizarMetodoManual(MetodoPagamento.PIX)).thenReturn(MetodoPagamento.PIX);
        when(formaPagamentoEmpresaService.normalizarParcelas(MetodoPagamento.PIX, null)).thenReturn(null);

        service.marcarPago(1L, new com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null));

        verify(caixaDespesasService).registrarPagamentoAprovado(pagamento);
        verify(caixaDespesasService, never()).registrarPagamentoCancelado(any(), any(), any());
    }

    @Test
    void excluirPagamentoCanceladoNaoDeveAlterarStatus() {
        PagamentoEntity pagamento = pagamentoPendente(1L, new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.CANCELADO);
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Idempotente: CANCELADO continua CANCELADO, sem erro e sem save.
        service.excluirPagamento(1L);

        verify(pagamentoRepository, never()).save(any());
        assertEquals(StatusPagamento.CANCELADO, pagamento.getStatus());
    }

    @Test
    void marcarPagoRepetidoPagoParaPagoNaoDuplicaCaixa() {
        PagamentoEntity pagamento = pagamentoPendente(1L, new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setMetodoPagamento(MetodoPagamento.PIX);
        pagamento.setDataPagamento(LocalDateTime.now());
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(formaPagamentoEmpresaService.normalizarMetodoManual(MetodoPagamento.PIX)).thenReturn(MetodoPagamento.PIX);
        when(formaPagamentoEmpresaService.normalizarParcelas(MetodoPagamento.PIX, null)).thenReturn(null);

        // Ja PAGO: a segunda execucao do "marcar pago" NAO pode somar caixa novamente.
        service.marcarPago(1L, new com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null));

        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
    }

    @Test
    void cancelarPagamentoPagoRemoveDoCaixa() {
        PagamentoEntity pagamento = pagamentoPendente(1L, new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setMetodoPagamento(MetodoPagamento.PIX);
        pagamento.setDataPagamento(LocalDateTime.now());
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        
        service.atualizarStatus(1L, new AtualizarStatusPagamentoRequest(StatusPagamento.CANCELADO));
        
        verify(caixaDespesasService).registrarPagamentoCancelado(eq(pagamento), any(), eq(StatusPagamento.PAGO));
    }

    @Test
    void cancelarPagamentoCanceladoNaoDeveRegistrarNoCaixa() {
        PagamentoEntity pagamento = pagamentoPendente(1L, new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.CANCELADO);
        pagamento.setMetodoPagamento(MetodoPagamento.PIX);
        pagamento.setDataPagamento(LocalDateTime.now());
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // CANCELADO atual nao pode sair de CANCELADO, nem mesmo para CANCELADO.
        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> service.atualizarStatus(1L, new AtualizarStatusPagamentoRequest(StatusPagamento.CANCELADO))
        );

        verify(pagamentoRepository, never()).save(any());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
        verify(caixaDespesasService, never()).registrarPagamentoCancelado(any(), any(), any());
        verify(caixaDespesasService, never()).registrarPagamentoRemovido(any(), any());
    }

    @Test
    void reativarPagamentoCanceladoParaPagoNaoDeveRegistrarEntradaNoCaixa() {
        PagamentoEntity pagamento = pagamentoPendente(1L, new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.CANCELADO);
        pagamento.setMetodoPagamento(MetodoPagamento.PIX);
        pagamento.setDataPagamento(LocalDateTime.now());
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> service.atualizarStatus(1L, new AtualizarStatusPagamentoRequest(StatusPagamento.PAGO))
        );

        verify(pagamentoRepository, never()).save(any());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
    }

    @Test
    void pendenteParaPendenteNaoMexeNoCaixa() {
        PagamentoEntity pagamento = pagamentoPendente(1L, new BigDecimal("200.00"));
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        
        service.atualizarStatus(1L, new AtualizarStatusPagamentoRequest(StatusPagamento.PENDENTE));
        
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
        verify(caixaDespesasService, never()).registrarPagamentoCancelado(any(), any(), any());
        verify(caixaDespesasService, never()).registrarPagamentoRemovido(any(), any());
    }

    @Test
    void canceladoParaPagoDeveLancarErro() {
        PagamentoEntity pagamento = pagamentoPendente(1L, new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.CANCELADO);
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.of(pagamento));

        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> service.atualizarStatus(1L, new AtualizarStatusPagamentoRequest(StatusPagamento.PAGO))
        );
        verify(pagamentoRepository, never()).save(any());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
        verify(caixaDespesasService, never()).registrarPagamentoCancelado(any(), any(), any());
        verify(caixaDespesasService, never()).registrarPagamentoRemovido(any(), any());
    }

    @Test
    void canceladoParaPendenteDeveLancarErro() {
        PagamentoEntity pagamento = pagamentoPendente(1L, new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.CANCELADO);
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.of(pagamento));

        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> service.atualizarStatus(1L, new AtualizarStatusPagamentoRequest(StatusPagamento.PENDENTE))
        );
        verify(pagamentoRepository, never()).save(any());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
        verify(caixaDespesasService, never()).registrarPagamentoCancelado(any(), any(), any());
        verify(caixaDespesasService, never()).registrarPagamentoRemovido(any(), any());
    }

    @Test
    void marcarPagoComPagamentoCanceladoDeveLancarErro() {
        PagamentoEntity pagamento = pagamentoPendente(1L, new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.CANCELADO);
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.of(pagamento));
        when(formaPagamentoEmpresaService.normalizarMetodoManual(MetodoPagamento.PIX)).thenReturn(MetodoPagamento.PIX);
        when(formaPagamentoEmpresaService.normalizarParcelas(MetodoPagamento.PIX, null)).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> service.marcarPago(1L, new com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null))
        );
        verify(pagamentoRepository, never()).save(any());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
    }

    @Test
    void pagamentoDeOutraEmpresaNaoPodeSerConfirmadoNemAlterarCaixa() {
        // CompanyContext = 9L (empresa A). Pagamento 1 pertence a outra empresa:
        // o repositorio isolado por empresa nao o retorna, logo nada pode ser alterado.
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(1L), eq(9L))).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.minhaempresa.gendaz.shared.ResourceNotFoundException.class,
                () -> service.marcarPago(1L, new com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null)));

        verify(pagamentoRepository, never()).save(any());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
        verify(caixaDespesasService, never()).registrarPagamentoCancelado(any(), any(), any());
        verify(caixaDespesasService, never()).registrarPagamentoRemovido(any(), any());
    }
}
