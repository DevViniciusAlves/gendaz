package com.minhaempresa.gendaz.admin.scheduler;

import com.minhaempresa.gendaz.admin.repository.AuditLogRepository;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class AuditLogCleanupScheduler {
    private final AuditLogRepository auditLogRepository;
    private final long retentionDays;

    public AuditLogCleanupScheduler(
            AuditLogRepository auditLogRepository,
            @Value("${app.audit-logs.retention-days:365}") long retentionDays
    ) {
        this.auditLogRepository = auditLogRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${app.audit-logs.cleanup-delay-ms:86400000}")
    @Transactional
    public void limparAntigos() {
        try {
            if (retentionDays <= 0) {
                log.warn("[audit-logs-cleanup] retencao desativada (retention-days={})", retentionDays);
                return;
            }
            int deletados = auditLogRepository.deleteBefore(LocalDateTime.now().minusDays(retentionDays));
            if (deletados > 0) {
                log.info("[audit-logs-cleanup] {} registros de auditoria removidos (retencao={} dias)", deletados, retentionDays);
            }
        } catch (Exception ex) {
            log.error("[audit-logs-cleanup] erro ao limpar audit_logs. erroTipo={}", ex.getClass().getSimpleName());
        }
    }
}
