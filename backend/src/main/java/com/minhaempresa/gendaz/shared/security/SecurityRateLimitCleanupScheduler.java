package com.minhaempresa.gendaz.shared.security;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityRateLimitCleanupScheduler {
    private final SecurityRateLimitEntryRepository rateLimitEntryRepository;

    @Scheduled(fixedDelayString = "${security.rate-limit.cleanup-ms:3600000}")
    @Transactional
    public void limparExpirados() {
        rateLimitEntryRepository.deleteExpiredBefore(LocalDateTime.now());
    }
}
