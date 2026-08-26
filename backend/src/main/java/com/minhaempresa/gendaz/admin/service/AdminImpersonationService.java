package com.minhaempresa.gendaz.admin.service;

import com.minhaempresa.gendaz.admin.entity.AdminImpersonationSessionEntity;
import com.minhaempresa.gendaz.admin.repository.AdminImpersonationSessionRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.security.SecurityMonitoringService;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminImpersonationService {
    public static final String STATUS_ATIVA = "ATIVA";
    public static final String STATUS_ENCERRADA = "ENCERRADA";
    public static final String STATUS_EXPIRADA = "EXPIRADA";

    private final AdminImpersonationSessionRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final AdminService adminService;
    private final SecurityMonitoringService securityMonitoringService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.admin.impersonation.ttl-minutes:${ADMIN_IMPERSONATION_TTL_MINUTES:30}}")
    private long ttlMinutes;

    @Transactional
    public StartImpersonationResult iniciar(String adminToken, Long empresaId, Long usuarioId, HttpServletRequest request) {
        UsuarioEntity admin = adminService.exigirAdmin(adminToken);
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
        if (empresa.getStatus() != StatusEmpresa.ATIVA) {
            throw new BusinessException("Empresa indisponivel para impersonacao.");
        }
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado."));
        if (usuario.getEmpresa() == null || !empresaId.equals(usuario.getEmpresa().getId())) {
            throw new BusinessException("Usuario nao pertence a empresa informada.");
        }
        if (usuario.getStatus() != StatusUsuario.ATIVO) {
            throw new BusinessException("Usuario indisponivel para impersonacao.");
        }
        if (usuario.getPerfil() == PerfilUsuario.SUPER_ADMIN) {
            throw new BusinessException("Nao e permitido impersonar SUPER_ADMIN.");
        }

        LocalDateTime agora = LocalDateTime.now();
        repository.findByAdminUsuarioIdAndStatus(admin.getId(), STATUS_ATIVA).forEach(sessao -> encerrar(sessao, STATUS_ENCERRADA, agora));

        String rawToken = gerarToken();
        String tokenHash = hash(rawToken);
        LocalDateTime expiraEm = agora.plusMinutes(ttlMinutes > 0 ? ttlMinutes : 30);
        AdminImpersonationSessionEntity sessao = repository.save(AdminImpersonationSessionEntity.builder()
                .adminUsuarioId(admin.getId())
                .usuarioImpersonadoId(usuario.getId())
                .empresaId(empresa.getId())
                .sessionTokenHash(tokenHash)
                .status(STATUS_ATIVA)
                .ipInicio(ip(request))
                .userAgentInicio(limitar(userAgent(request), 500))
                .criadoEm(agora)
                .expiraEm(expiraEm)
                .build());

        log.info("[ADMIN_IMPERSONATION] START adminId={} usuarioId={} empresaId={} ip={} userAgent={}", admin.getId(), usuario.getId(), empresa.getId(), ip(request), limitar(userAgent(request), 200));
        registrarMonitoramento("ADMIN_IMPERSONATION_START", request, String.valueOf(admin.getId()), "usuarioId=" + usuario.getId() + "; empresaId=" + empresa.getId());
        return new StartImpersonationResult(rawToken, sessao.getId(), empresa.getId(), usuario.getId(), expiraEm);
    }

    @Transactional
    public Optional<AdminImpersonationSessionEntity> validar(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<AdminImpersonationSessionEntity> sessao = repository.findBySessionTokenHashAndStatus(hash(rawToken), STATUS_ATIVA);
        if (sessao.isEmpty()) {
            return Optional.empty();
        }
        AdminImpersonationSessionEntity ativa = sessao.get();
        if (ativa.getExpiraEm().isBefore(LocalDateTime.now())) {
            encerrar(ativa, STATUS_EXPIRADA, LocalDateTime.now());
            log.info("[ADMIN_IMPERSONATION] EXPIRED adminId={} usuarioId={} empresaId={}", ativa.getAdminUsuarioId(), ativa.getUsuarioImpersonadoId(), ativa.getEmpresaId());
            return Optional.empty();
        }
        return Optional.of(ativa);
    }

    @Transactional
    public Optional<CurrentImpersonationResult> atual(String rawToken) {
        return validar(rawToken).map(sessao -> new CurrentImpersonationResult(true, sessao.getAdminUsuarioId(), sessao.getUsuarioImpersonadoId(), sessao.getEmpresaId(), sessao.getExpiraEm()));
    }

    @Transactional
    public void encerrarPorToken(String rawToken, HttpServletRequest request) {
        validar(rawToken).ifPresent(sessao -> {
            encerrar(sessao, STATUS_ENCERRADA, LocalDateTime.now());
            log.info("[ADMIN_IMPERSONATION] END adminId={} usuarioId={} empresaId={} motivo=MANUAL", sessao.getAdminUsuarioId(), sessao.getUsuarioImpersonadoId(), sessao.getEmpresaId());
            registrarMonitoramento("ADMIN_IMPERSONATION_END", request, String.valueOf(sessao.getAdminUsuarioId()), "usuarioId=" + sessao.getUsuarioImpersonadoId() + "; empresaId=" + sessao.getEmpresaId());
        });
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel gerar hash da sessao.", e);
        }
    }

    private void encerrar(AdminImpersonationSessionEntity sessao, String status, LocalDateTime quando) {
        sessao.setStatus(status);
        sessao.setEncerradoEm(quando);
        sessao.setMotivoEncerramento(status.equals(STATUS_EXPIRADA) ? "EXPIRADA" : "MANUAL");
    }

    private String gerarToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void registrarMonitoramento(String tipo, HttpServletRequest request, String identificador, String detalhe) {
        if (securityMonitoringService != null) {
            securityMonitoringService.registrarEvento(tipo, "SECURITY", request, identificador, detalhe);
        }
    }

    private String ip(HttpServletRequest request) {
        return securityMonitoringService == null ? "unknown" : securityMonitoringService.getClientIp(request);
    }

    private String userAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }

    private String limitar(String valor, int tamanho) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.replaceAll("[\\r\\n\\t]", " ").trim();
        return limpo.length() > tamanho ? limpo.substring(0, tamanho) : limpo;
    }

    public record StartImpersonationResult(String rawToken, Long sessionId, Long empresaId, Long usuarioId, LocalDateTime expiraEm) {}
    public record CurrentImpersonationResult(boolean active, Long adminId, Long usuarioId, Long empresaId, LocalDateTime expiraEm) {}
}
