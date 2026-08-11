package com.minhaempresa.gendaz.security;

import com.minhaempresa.gendaz.security.repository.IpTrackingRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IpTrackingCleanupScheduler {

    private final IpTrackingRepository ipTrackingRepository;

    @Scheduled(fixedDelay = 21600000)
    @Transactional
    public void limparIpsAntigos() {
        try {
            LocalDateTime dataLimite = LocalDateTime.now().minusDays(30);
            int deletados = ipTrackingRepository.deleteByUltimoAcessoBefore(dataLimite);
            if (deletados > 0) {
                log.info("[ip-cleanup] {} registros de IP antigos foram removidos", deletados);
            }
        } catch (Exception ex) {
            log.error("[ip-cleanup] erro ao limpar IPs antigos: {}", ex.getMessage());
        }
    }
}

