package com.minhaempresa.gendaz.auth.service;

import com.minhaempresa.gendaz.auth.repository.MeuGendazOtpChallengeRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MeuGendazOtpChallengeCleanupScheduler {
    private final MeuGendazOtpChallengeRepository challengeRepository;

    @Scheduled(fixedDelayString = "${security.meu-gendaz.otp.cleanup-ms:3600000}")
    @Transactional
    public void limparExpirados() {
        challengeRepository.deleteExpiredBefore(LocalDateTime.now().minusDays(1));
    }
}
