package com.minhaempresa.agendapro.admin.service;

import com.minhaempresa.agendapro.admin.dto.AdminDtos.AdminAuditLogResponse;
import com.minhaempresa.agendapro.admin.entity.AuditLogEntity;
import com.minhaempresa.agendapro.admin.repository.AuditLogRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void registrar(String tipo, String severidade, UsuarioEntity admin, UsuarioEntity usuario, EmpresaEntity empresa, String descricao, String motivo, String ip, String userAgent) {
        auditLogRepository.save(AuditLogEntity.builder()
                .tipo(tipo)
                .severidade(severidade)
                .admin(admin)
                .usuario(usuario)
                .empresa(empresa)
                .descricao(descricao)
                .motivo(motivo)
                .ip(ip)
                .userAgent(userAgent)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> listar() {
        return auditLogRepository.findTop200ByOrderByDataCriacaoDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private AdminAuditLogResponse toResponse(AuditLogEntity log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getTipo(),
                log.getSeveridade(),
                log.getAdmin() == null ? null : log.getAdmin().getEmail(),
                log.getUsuario() == null ? null : log.getUsuario().getEmail(),
                log.getEmpresa() == null ? null : log.getEmpresa().getNomeFantasia(),
                log.getDescricao(),
                log.getMotivo(),
                log.getIp(),
                log.getUserAgent(),
                log.getDataCriacao()
        );
    }
}
