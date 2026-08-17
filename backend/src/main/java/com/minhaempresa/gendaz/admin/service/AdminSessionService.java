package com.minhaempresa.gendaz.admin.service;

import com.minhaempresa.gendaz.admin.entity.AdminSessionEntity;
import com.minhaempresa.gendaz.admin.repository.AdminSessionRepository;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.shared.SessaoExpiradaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@Slf4j
public class AdminSessionService {
    private final AdminSessionRepository adminSessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminSessionService(AdminSessionRepository adminSessionRepository) {
        this.adminSessionRepository = adminSessionRepository;
    }

    @Transactional
    public String criarSessao(UsuarioEntity admin, String ip, String userAgent) {
        if (admin.getPerfil() != PerfilUsuario.SUPER_ADMIN || admin.getStatus() != StatusUsuario.ATIVO) {
            throw new SessaoExpiradaException("Administrador invalido.");
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = calcularHash(token);

        AdminSessionEntity session = AdminSessionEntity.builder()
                .admin(admin)
                .tokenHash(tokenHash)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15)) // A sessão administrativa possui TTL curto de 15 minutos por decisão de segurança, devido ao alto privilégio do Painel Admin.
                .ip(ip)
                .userAgent(userAgent)
                .build();

        adminSessionRepository.save(session);
        return token;
    }

    @Transactional(readOnly = true)
    public UsuarioEntity validarSessao(String token) {
        String tokenHash = calcularHash(token);
        Optional<AdminSessionEntity> sessionOpt = adminSessionRepository.findByTokenHash(tokenHash);

        if (sessionOpt.isEmpty()) {
            throw new SessaoExpiradaException("Sessão admin não encontrada.");
        }

        AdminSessionEntity session = sessionOpt.get();

        if (session.getRevokedAt() != null) {
            throw new SessaoExpiradaException("Sessão admin revogada.");
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new SessaoExpiradaException("Sessão admin expirada.");
        }

        UsuarioEntity admin = session.getAdmin();
        if (admin.getStatus() != StatusUsuario.ATIVO || admin.getPerfil() != PerfilUsuario.SUPER_ADMIN) {
            throw new SessaoExpiradaException("Administrador sem acesso.");
        }

        return admin;
    }

    @Transactional
    public void revogarSessao(String token) {
        String tokenHash = calcularHash(token);
        adminSessionRepository.findByTokenHash(tokenHash).ifPresent(session -> {
            session.setRevokedAt(LocalDateTime.now());
            adminSessionRepository.save(session);
        });
    }

    private String calcularHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao calcular hash do token", e);
        }
    }
}
