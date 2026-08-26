package com.minhaempresa.gendaz.auth.service;

import com.minhaempresa.gendaz.auth.dto.MeuGendazOnboardingPrincipal;
import com.minhaempresa.gendaz.auth.entity.MeuGendazOtpChallengeEntity;
import com.minhaempresa.gendaz.auth.repository.MeuGendazOtpChallengeRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeuGendazOnboardingService {
    private final MeuGendazOtpChallengeRepository challengeRepository;
    private final MeuGendazTokenHashService tokenHashService;

    @Transactional(readOnly = true)
    public Optional<MeuGendazOnboardingPrincipal> validar(String token, Long empresaId) {
        if (token == null || token.isBlank() || empresaId == null) {
            return Optional.empty();
        }
        String hash = tokenHashService.hashToken(token);
        return challengeRepository.findByOnboardingSessionHash(hash)
                .filter(challenge -> challenge.getEmpresa() != null)
                .filter(challenge -> empresaId.equals(challenge.getEmpresa().getId()))
                .filter(challenge -> challenge.getOnboardingSessionExpiraEm() != null)
                .filter(challenge -> LocalDateTime.now().isBefore(challenge.getOnboardingSessionExpiraEm()))
                .map(challenge -> new MeuGendazOnboardingPrincipal(challenge.getId(), empresaId, challenge.getEmail()));
    }

    @Transactional
    public MeuGendazOtpChallengeEntity exigirValidoParaAtualizacao(String token, Long empresaId) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("Cadastro temporario expirado. Solicite um novo codigo.");
        }
        String hash = tokenHashService.hashToken(token);
        MeuGendazOtpChallengeEntity challenge = challengeRepository.findByOnboardingSessionHashForUpdate(hash)
                .orElseThrow(() -> new BusinessException("Cadastro temporario expirado. Solicite um novo codigo."));
        if (challenge.getEmpresa() == null || !empresaId.equals(challenge.getEmpresa().getId())) {
            throw new BusinessException("Cadastro temporario invalido para esta loja.");
        }
        if (challenge.getOnboardingSessionExpiraEm() == null || !LocalDateTime.now().isBefore(challenge.getOnboardingSessionExpiraEm())) {
            throw new BusinessException("Cadastro temporario expirado. Solicite um novo codigo.");
        }
        return challenge;
    }

    @Transactional
    public void invalidar(MeuGendazOtpChallengeEntity challenge) {
        if (challenge == null) {
            return;
        }
        challenge.setOnboardingSessionHash(null);
        challenge.setOnboardingSessionExpiraEm(null);
        challenge.setOtpHash(null);
        challengeRepository.save(challenge);
    }
}
