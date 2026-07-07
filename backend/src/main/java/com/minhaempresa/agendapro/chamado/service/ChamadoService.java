package com.minhaempresa.agendapro.chamado.service;

import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.AtualizarChamadoRequest;
import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.CriarChamadoRequest;
import com.minhaempresa.agendapro.chamado.entity.ChamadoEntity;
import com.minhaempresa.agendapro.chamado.enums.StatusChamado;
import com.minhaempresa.agendapro.chamado.mapper.ChamadoMapper;
import com.minhaempresa.agendapro.chamado.repository.ChamadoRepository;
import com.minhaempresa.agendapro.admin.service.AdminAuditService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChamadoService {
    private final ChamadoRepository chamadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AdminAuditService auditService;
    private final ChamadoMapper mapper = new ChamadoMapper();

    @Transactional
    public ChamadoResponse criar(CriarChamadoRequest request, Long usuarioId) {
        UsuarioEntity usuario = buscarUsuario(usuarioId);
        EmpresaEntity empresa = usuario.getEmpresa();
        if (empresa == null) {
            throw new BusinessException("Usuario sem empresa nao pode abrir chamado.");
        }
        ChamadoEntity chamado = chamadoRepository.save(ChamadoEntity.builder()
                .assunto(request.assunto().trim())
                .mensagem(request.mensagem().trim())
                .prioridade(request.prioridade())
                .empresa(empresa)
                .usuario(usuario)
                .status(StatusChamado.ABERTO)
                .build());
        auditService.registrar("CHAMADO_CRIADO", "INFO", null, usuario, empresa, "Chamado aberto pelo painel", request.assunto().trim(), null, null);
        return mapper.toResponse(chamado);
    }

    @Transactional(readOnly = true)
    public List<ChamadoResponse> listarPorEmpresa(Long empresaId, Long usuarioId) {
        UsuarioEntity usuario = buscarUsuario(usuarioId);
        if (usuario.getPerfil() != PerfilUsuario.SUPER_ADMIN) {
            if (usuario.getEmpresa() == null || !usuario.getEmpresa().getId().equals(empresaId)) {
                throw new BusinessException("Acesso nao autorizado aos chamados desta empresa.");
            }
        }
        return chamadoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ChamadoResponse> listarTodos() {
        return chamadoRepository.findAllByOrderByDataCriacaoDesc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public ChamadoResponse atualizar(Long id, AtualizarChamadoRequest request, UsuarioEntity admin) {
        if (admin.getPerfil() != PerfilUsuario.SUPER_ADMIN) {
            throw new BusinessException("Acesso nao autorizado.");
        }
        ChamadoEntity chamado = chamadoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chamado nao encontrado."));
        StatusChamado statusAnterior = chamado.getStatus();
        chamado.setStatus(request.status());
        if (request.resposta() != null && !request.resposta().isBlank()) {
            chamado.setResposta(request.resposta().trim());
        }
        ChamadoEntity salvo = chamadoRepository.save(chamado);
        auditService.registrar(
                "CHAMADO_ATUALIZADO",
                "INFO",
                admin,
                null,
                salvo.getEmpresa(),
                "Chamado atualizado pelo Super Admin",
                "status=" + statusAnterior + "->" + salvo.getStatus(),
                null,
                null
        );
        return mapper.toResponse(salvo);
    }

    private UsuarioEntity buscarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado."));
    }
}
