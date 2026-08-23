package com.minhaempresa.gendaz.admin.service;

import com.minhaempresa.gendaz.admin.entity.AdminAuditEntity;
import com.minhaempresa.gendaz.admin.repository.AdminAuditRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
}