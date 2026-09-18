package com.minhaempresa.gendaz.whatsapp.service;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppEntregaReceiptRepository;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppEntregaServiceTest {

    @Mock
    private WhatsAppNotificacaoRepository notificacaoRepository;

    @Mock
    private WhatsAppEntregaReceiptRepository receiptRepository;

    @Mock
    private WhatsAppQuotaService quotaService;

    @Mock
    private EmpresaRepository empresaRepository;

    private WhatsAppEntregaService service;

    @BeforeEach
    void setUp() {
        service = spy(new WhatsAppEntregaService(
                notificacaoRepository,
                receiptRepository,
                quotaService,
                empresaRepository
        ));
        service.setSelf(service);
    }

    @Test
    void persistirReceiptFalha_retornaERRO_TRANSITORIO() {
        when(empresaRepository.existsById(1L)).thenReturn(true);
        doThrow(new RuntimeException("db failure")).when(service).persistirReceipt(1L, "WAMID-1");

        var resultado = service.registrarEntrega(1L, "WAMID-1");

        assertThat(resultado).isEqualTo(WhatsAppEntregaService.ResultadoEntrega.ERRO_TRANSITORIO);
        verify(service, never()).confirmarEntrega(1L, "WAMID-1");
    }

    @Test
    void confirmarEntregaFalha_retornaERRO_TRANSITORIO() {
        when(empresaRepository.existsById(1L)).thenReturn(true);
        doNothing().when(service).persistirReceipt(1L, "WAMID-1");
        doThrow(new RuntimeException("confirm failure")).when(service).confirmarEntrega(1L, "WAMID-1");

        var resultado = service.registrarEntrega(1L, "WAMID-1");

        assertThat(resultado).isEqualTo(WhatsAppEntregaService.ResultadoEntrega.ERRO_TRANSITORIO);
    }

    @Test
    void optimisticLock_chamaVerificarAposConflito() {
        when(empresaRepository.existsById(1L)).thenReturn(true);
        doNothing().when(service).persistirReceipt(1L, "WAMID-1");
        doThrow(new ObjectOptimisticLockingFailureException(ObjectOptimisticLockingFailureException.class, "optimistic lock")).when(service).confirmarEntrega(1L, "WAMID-1");
        when(service.verificarAposConflito(1L, "WAMID-1")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.JA_CONFIRMADO);

        var resultado = service.registrarEntrega(1L, "WAMID-1");

        assertThat(resultado).isEqualTo(WhatsAppEntregaService.ResultadoEntrega.JA_CONFIRMADO);
        verify(service, times(1)).verificarAposConflito(1L, "WAMID-1");
    }
}
