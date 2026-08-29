package com.minhaempresa.gendaz.admin.service;

import com.minhaempresa.gendaz.admin.dto.AdminDtos.AdminAuditLogResponse;
import com.minhaempresa.gendaz.admin.entity.AdminAuditEntity;
import com.minhaempresa.gendaz.admin.repository.AdminAuditRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditRepository adminAuditRepository;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    /**
     * Registra um log de auditoria.
     *
     * @param acao      Ação realizada (ex: "Criar", "Editar", "Excluir").
     * @param entidade  Entidade afetada (ex: "Cliente", "Agendamento").
     * @param entidadeId ID da entidade afetada (opcional).
     * @param descricao Descrição detalhada da ação (ex: "Criou cliente Cleiton").
     */
    public void registrar(String acao, String entidade, Long entidadeId, String descricao) {
        try {
            AdminAuditEntity audit = new AdminAuditEntity();
            audit.setEmpresaId(CompanyContext.requireCompanyId()); // Use CompanyContext.requireCompanyId() para garantir empresa
            
            Long usuarioId = null;
            String usuarioNome = "Sistema";
            try {
                usuarioId = usuarioAutenticadoProvider.exigirUsuarioId();
                usuarioNome = usuarioAutenticadoProvider.exigirUsuario().getNome();
            } catch (Exception e) {
                log.debug("Nenhum usuario autenticado encontrado para o log de auditoria, registrando como 'Sistema'.");
            }
            
            audit.setUsuarioId(usuarioId != null ? usuarioId : 0L); // 0L representa sistema/anônimo se não autenticado
            audit.setUsuarioNome(usuarioNome);
            audit.setAcao(acao);
            audit.setEntidade(entidade);
            audit.setEntidadeId(entidadeId);
            audit.setDescricao(descricao);
            audit.setDataHora(LocalDateTime.now());
            
            // TODO: Obter IP e User-Agent do request (opcional)
            audit.setIp(null);
            audit.setUserAgent(null);
            
            adminAuditRepository.save(audit);
        } catch (Exception e) {
            log.error("Falha ao registrar auditoria: {}", e.getMessage(), e);
        }
    }


    /**
     * Registra um log de auditoria sem entidadeId.
     *
     * @param acao      Ação realizada.
     * @param entidade  Entidade afetada.
     * @param descricao Descrição detalhada da ação.
     */
    public void registrar(String acao, String entidade, String descricao) {
        registrar(acao, entidade, null, descricao);
    }

    /**
     * Retorna todos os logs de auditoria da empresa, ordenados por data/hora (mais recentes primeiro).
     *
     * @param empresaId ID da empresa.
     * @return Lista de logs de auditoria.
     */
    public List<AdminAuditEntity> findByEmpresaIdOrderByDataHoraDesc(Long empresaId) {
        return adminAuditRepository.findByEmpresaIdOrderByDataHoraDesc(empresaId);
    }

    /**
     * Registra um log de auditoria com dados completos de seguranca (compatibilidade).
     *
     * @param acao      Ação realizada.
     * @param severidade Severidade/categoria do log.
     * @param admin     Administrador responsavel (opcional).
     * @param usuario   Usuário afetado (opcional).
     * @param empresa   Empresa afetada (opcional).
     * @param descricao Descrição da ação.
     * @param motivo    Motivo adicional (opcional).
     * @param ip        Endereço IP (opcional).
     * @param userAgent User-Agent (opcional).
     */
    public void registrar(String acao, String severidade, UsuarioEntity admin, UsuarioEntity usuario,
                          EmpresaEntity empresa, String descricao, String motivo, String ip, String userAgent) {
        try {
            AdminAuditEntity audit = new AdminAuditEntity();
            Long empresaId = empresa != null ? empresa.getId() : CompanyContext.requireCompanyId();
            audit.setEmpresaId(empresaId);

            Long usuarioId = usuario != null ? usuario.getId() : (admin != null ? admin.getId() : null);
            String usuarioNome = usuario != null ? usuario.getNome() : (admin != null ? admin.getNome() : null);
            audit.setUsuarioId(usuarioId != null ? usuarioId : 0L);
            audit.setUsuarioNome(usuarioNome != null ? usuarioNome : "Sistema");

            audit.setAcao(acao);
            audit.setEntidade(severidade != null ? severidade : "AUDITORIA");
            audit.setEntidadeId(null);
            audit.setDescricao(motivo != null && !motivo.isBlank() ? descricao + " - " + motivo : descricao);
            audit.setDataHora(LocalDateTime.now());
            audit.setIp(ip);
            audit.setUserAgent(userAgent);

            adminAuditRepository.save(audit);
        } catch (Exception e) {
            log.error("Falha ao registrar auditoria: {}", e.getMessage(), e);
        }
    }

    /**
     * Retorna todos os logs de auditoria (Super Admin), mais recentes primeiro.
     *
     * @return Lista de logs de auditoria.
     */
    public List<AdminAuditLogResponse> listar() {
        return adminAuditRepository.findAllByOrderByDataHoraDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private AdminAuditLogResponse toResponse(AdminAuditEntity e) {
        return new AdminAuditLogResponse(
                e.getId(),
                e.getAcao(),
                e.getEntidade(),
                null,
                e.getUsuarioNome(),
                String.valueOf(e.getEmpresaId()),
                e.getDescricao(),
                null,
                e.getIp(),
                e.getUserAgent(),
                e.getDataHora()
        );
    }

    /**
     * Registra um evento de seguranca (ex.: login falhado, logout, falha de cadastro)
     * em sua propria transacao (REQUIRES_NEW), para que o registro de auditoria nao
     * seja perdido caso a operacao principal sofra rollback. O tenant (empresa) e o
     * usuario sao informados explicitamente pelo chamador, que ja os resolveu no
     * servidor; nunca se confia em dados vindos do frontend.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarEventoSeguranca(String tipo, String descricao, Long empresaId,
                                          String usuarioNome, String ip, String userAgent) {
        try {
            AdminAuditEntity audit = new AdminAuditEntity();
            audit.setEmpresaId(empresaId != null ? empresaId : 0L);
            audit.setUsuarioId(0L);
            audit.setUsuarioNome(usuarioNome != null && !usuarioNome.isBlank() ? usuarioNome : "Desconhecido");
            audit.setAcao(tipo);
            audit.setEntidade("SECURITY");
            audit.setEntidadeId(null);
            audit.setDescricao(descricao != null ? descricao : "");
            audit.setDataHora(LocalDateTime.now());
            audit.setIp(ip);
            audit.setUserAgent(userAgent);
            adminAuditRepository.save(audit);
        } catch (Exception e) {
            log.error("Falha ao registrar evento de seguranca: {}", e.getMessage(), e);
        }
    }
}