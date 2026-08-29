package com.minhaempresa.gendaz.auth.service;

import com.minhaempresa.gendaz.auth.entity.PasswordResetTokenEntity;
import com.minhaempresa.gendaz.auth.repository.PasswordResetTokenRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PasswordResetTokenRepository tokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordService passwordService;

    @Transactional
    public String solicitarRecuperacao(UsuarioEntity usuario) {
        String token = gerarTokenSeguro();
        tokenRepository.save(PasswordResetTokenEntity.builder()
                .usuario(usuario)
                .tokenHash(hash(token))
                .dataExpiracao(LocalDateTime.now().plusHours(1))
                .usado(false)
                .build());
        return token;
    }

    @Transactional
    public void redefinirSenha(String token, String novaSenha, String confirmarNovaSenha) {
        if (!novaSenha.equals(confirmarNovaSenha)) {
            throw new BusinessException("As senhas nao coincidem.");
        }
        passwordService.validarSenha(novaSenha);
        PasswordResetTokenEntity resetToken = tokenRepository.findByTokenHashAndUsadoFalse(hash(token))
                .orElseThrow(() -> new BusinessException("Token invalido ou expirado."));
        if (resetToken.getDataExpiracao().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Token invalido ou expirado.");
        }
        UsuarioEntity usuario = resetToken.getUsuario();
        usuario.setSenha(passwordService.hash(novaSenha));
        usuarioRepository.save(usuario);
        resetToken.setUsado(true);
        resetToken.setDataUso(LocalDateTime.now());
        tokenRepository.save(resetToken);
    }

    private String gerarTokenSeguro() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("Nao foi possivel gerar hash do token.", ex);
        }
    }
}

