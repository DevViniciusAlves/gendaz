package com.minhaempresa.gendaz.auth.dto;

public record MeuGendazOnboardingPrincipal(
        Long challengeId,
        Long empresaId,
        String email
) {}
