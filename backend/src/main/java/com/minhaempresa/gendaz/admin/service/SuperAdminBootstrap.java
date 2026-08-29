package com.minhaempresa.gendaz.admin.service;

import com.minhaempresa.gendaz.auth.service.PasswordService;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBootstrap implements CommandLineRunner {
    private final UsuarioRepository usuarioRepository;
    private final PasswordService passwordService;
    private final AdminAuditService auditService;

    @Value("${SUPER_ADMIN_BOOTSTRAP_ENABLED:false}")
    private boolean bootstrapEnabled;

    @Value("${SUPER_ADMIN_EMAIL:}")
    private String superAdminEmail;

    @Value("${SUPER_ADMIN_PASSWORD:}")
    private String superAdminPassword;

    @Value("${SUPER_ADMIN_FORCE_PASSWORD_RESET:false}")
    private boolean forcePasswordReset;

    @Override
    @Transactional
    public void run(String... args) {
        if (!bootstrapEnabled && !forcePasswordReset) {
            return;
        }
        String email = superAdminEmail == null ? "" : superAdminEmail.trim().toLowerCase();
        if (email.isBlank() || superAdminPassword == null || superAdminPassword.isBlank()) {
            throw new IllegalStateException("SUPER_ADMIN_EMAIL e SUPER_ADMIN_PASSWORD sao obrigatorios quando o bootstrap admin esta ativo.");
        }
        UsuarioEntity existente = usuarioRepository.findAllByEmailIgnoreCase(email).stream()
                .filter(usuario -> usuario.getPerfil() == PerfilUsuario.SUPER_ADMIN)
                .findFirst()
                .orElse(null);
        if (existente != null) {
            if (existente.getPerfil() != PerfilUsuario.SUPER_ADMIN) {
                throw new IllegalStateException("SUPER_ADMIN_EMAIL ja pertence a um usuario comum.");
            }
            atualizarSenhaSeForcado(existente);
            return;
        }

        UsuarioEntity adminExistente = usuarioRepository.findFirstByPerfil(PerfilUsuario.SUPER_ADMIN).orElse(null);
        if (adminExistente != null) {
            if (!forcePasswordReset) {
                log.warn("Super Admin ja existe com outro e-mail. Ative SUPER_ADMIN_FORCE_PASSWORD_RESET=true para atualizar e-mail/senha pelo Render.");
                return;
            }
            if (usuarioRepository.existsByEmail(email)) {
                throw new IllegalStateException("SUPER_ADMIN_EMAIL ja esta em uso por outro usuario.");
            }
            adminExistente.setEmail(email);
            atualizarSenhaSeForcado(adminExistente);
            log.info("E-mail do Super Admin atualizado por variavel segura do Render.");
            return;
        }

        if (!bootstrapEnabled) {
            log.warn("SUPER_ADMIN_FORCE_PASSWORD_RESET=true, mas nenhum Super Admin existe. Ative SUPER_ADMIN_BOOTSTRAP_ENABLED=true para criar o primeiro admin.");
            return;
        }
        usuarioRepository.save(UsuarioEntity.builder()
                .nome("Super Admin")
                .email(email)
                .senha(passwordService.hash(superAdminPassword))
                .perfil(PerfilUsuario.SUPER_ADMIN)
                .status(StatusUsuario.ATIVO)
                .aceitouTermos(true)
                .dataAceiteTermos(LocalDateTime.now())
                .versaoTermos("admin-bootstrap")
                .empresa(null)
                .build());
        log.info("Super Admin inicial criado por bootstrap seguro.");
    }

    private void atualizarSenhaSeForcado(UsuarioEntity admin) {
        if (!forcePasswordReset) {
            log.info("Super Admin ja existe. Senha nao foi alterada porque SUPER_ADMIN_FORCE_PASSWORD_RESET=false.");
            return;
        }
        admin.setSenha(passwordService.hash(superAdminPassword));
        admin.setStatus(StatusUsuario.ATIVO);
        usuarioRepository.save(admin);
        auditService.registrar(
                "SUPER_ADMIN_PASSWORD_RESET",
                "SECURITY",
                admin,
                admin,
                null,
                "Senha do Super Admin atualizada por variavel segura do Render",
                "SUPER_ADMIN_FORCE_PASSWORD_RESET=true",
                null,
                null
        );
        log.info("Senha do Super Admin atualizada por variavel segura do Render.");
    }
}

