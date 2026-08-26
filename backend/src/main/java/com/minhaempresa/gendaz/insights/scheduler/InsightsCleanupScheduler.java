package com.minhaempresa.gendaz.insights.scheduler;

import com.minhaempresa.gendaz.insights.repository.InsightRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InsightsCleanupScheduler {
    private final InsightRepository insightRepository;

    @Scheduled(fixedDelayString = "${app.insights.cleanup-delay-ms:3600000}")
    @Transactional
    public void limparExpirados() {
        try {
            int deletados = insightRepository.deleteExpiredBefore(LocalDateTime.now());
            if (deletados > 0) {
                log.info("[insights-cleanup] {} insights expirados removidos", deletados);
            }
        } catch (Exception ex) {
            log.error("[insights-cleanup] erro ao limpar insights expirados. erroTipo={}", ex.getClass().getSimpleName());
        }
    }
}
