package com.minhaempresa.agendapro.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KeepAliveScheduler {

    /**
     * Executa a cada 4 minutos para evitar hibernação do Render free tier
     */
    @Scheduled(fixedRate = 4 * 60 * 1000, initialDelay = 30 * 1000)
    public void keepAliveTask() {
        try {
            log.info("[keep-alive] scheduled task executed");
        } catch (Exception e) {
            log.warn("[keep-alive] task failed:", e);
        }
    }
}
