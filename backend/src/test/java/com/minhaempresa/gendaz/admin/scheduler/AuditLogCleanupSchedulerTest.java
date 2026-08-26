package com.minhaempresa.gendaz.admin.scheduler;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.admin.repository.AuditLogRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogCleanupSchedulerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void limparAntigosUtilizaRetencaoConfiguradaEmDias() {
        AuditLogCleanupScheduler scheduler = new AuditLogCleanupScheduler(auditLogRepository, 90L);
        when(auditLogRepository.deleteBefore(org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(2);

        scheduler.limparAntigos();

        ArgumentCaptor<LocalDateTime> limite = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepository).deleteBefore(limite.capture());
        assertTrue(limite.getValue().isBefore(LocalDateTime.now()));
        assertTrue(limite.getValue().isAfter(LocalDateTime.now().minusDays(91)));
    }

    @Test
    void retencaoZeroOuNegativaNaoRemoveNada() {
        AuditLogCleanupScheduler desativado = new AuditLogCleanupScheduler(auditLogRepository, 0L);
        desativado.limparAntigos();
        verify(auditLogRepository, never()).deleteBefore(org.mockito.ArgumentMatchers.any());
    }
}