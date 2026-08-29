package com.minhaempresa.gendaz.usuario.service;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.auth.service.PasswordService;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.shared.security.SecurityMonitoringService;
import com.minhaempresa.gendaz.usuario.dto.UsuarioDtos.AtualizarUsuarioRequest;
import com.minhaempresa.gendaz.usuario.dto.UsuarioDtos.CriarUsuarioRequest;
import com.minhaempresa.gendaz.usuario.dto.UsuarioDtos.UsuarioResponse;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.mapper.UsuarioMapper;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioService {
    private static final List<PerfilUsuario> PERFIS_PAINEL_DIRETOS = List.of(PerfilUsuario.SUPER_ADMIN, PerfilUsuario.DONO);
    private final UsuarioRepository usuarioRepository;
    private final EmpresaService empresaService;
    private final PasswordService passwordService;
    private final ResendEmailService resendEmailService;
    private final SanitizacaoService sanitizacaoService;
    private final SecurityMonitoringService securityMonitoringService;
    private final AdminAuditService adminAuditService;
    private final UsuarioMapper mapper = new UsuarioMapper();
    private final LogAtividadeService logAtividadeService;

    @Transactional
    public UsuarioResponse criar(CriarUsuarioRequest request) {
        validarNome(request.nome());
        if (request.perfil() == PerfilUsuario.SUPER_ADMIN) {
            throw new BusinessException("SUPER_ADMIN so pode ser criado pelo bootstrap seguro.");
        }
        if (request.empresaId() == null) {
            throw new BusinessException("empresaId e obrigatorio para este perfil.");
        }
        EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
        if (usuarioRepository.existsByEmpresaIdAndEmail(empresa.getId(), request.email())) {
            throw new ConflictException("Ja existe usuario com este e-mail nesta empresa.");
        }
        UsuarioEntity usuario = UsuarioEntity.builder()
                .nome(sanitizacaoService.textoObrigatorio(request.nome()))
                .email(sanitizacaoService.email(request.email()))
                .senha(passwordService.hash(request.senha()))
                .perfil(request.perfil())
                .status(StatusUsuario.ATIVO)
                .aceitouTermos(false)
                .empresa(empresa)
                .build();
        UsuarioEntity salvo = usuarioRepository.save(usuario);
        boolean emailBoasVindas = resendEmailService.enviarBoasVindas(
                salvo.getEmail(),
                salvo.getNome(),
                empresa.getNomeFantasia()
        );
        if (!emailBoasVindas) {
            log.warn("Usuario criado, mas o email de boas-vindas não foi enviado para {}", securityMonitoringService.mascararEmail(salvo.getEmail()));
        }
        // Registrar auditoria
        adminAuditService.registrar("Criar", "Usuário", salvo.getId(), "Adicionou " + salvo.getNome() + " como usuário");
        logAtividadeService.registrar("USUARIO", salvo.getId(), "Adicionou " + salvo.getNome() + " como usuário");
        return mapper.toResponse(salvo);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return usuarioRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return mapper.toResponse(buscarEntidade(id));
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, AtualizarUsuarioRequest request) {
        validarNome(request.nome());
        if (request.perfil() == PerfilUsuario.SUPER_ADMIN) {
            throw new BusinessException("SUPER_ADMIN so pode ser gerenciado pelo fluxo administrativo seguro.");
        }
        UsuarioEntity usuario = buscarEntidade(id);
        validarNaoSuperAdmin(usuario);
        
        String nomeAnterior = usuario.getNome();
        PerfilUsuario perfilAnterior = usuario.getPerfil();
        
        usuario.setNome(sanitizacaoService.textoObrigatorio(request.nome()));
        usuario.setEmail(sanitizacaoService.email(request.email()));
        usuario.setPerfil(request.perfil());
        UsuarioEntity salvo = usuarioRepository.save(usuario);
        // Registrar auditoria
        adminAuditService.registrar("Editar", "Usuário", salvo.getId(), "Editou usuário " + nomeAnterior);
        logAtividadeService.registrar("USUARIO", salvo.getId(), "Alterou perfil de " + salvo.getNome());
        return mapper.toResponse(salvo);
    }

    @Transactional
    public UsuarioResponse ativar(Long id) {
        UsuarioEntity usuario = buscarEntidade(id);
        validarNaoSuperAdmin(usuario);
        usuario.setStatus(StatusUsuario.ATIVO);
        UsuarioEntity salvo = usuarioRepository.save(usuario);
        // Registrar auditoria
        adminAuditService.registrar("Ativar", "Usuário", salvo.getId(), "Ativou usuário " + salvo.getNome());
        logAtividadeService.registrar("USUARIO", salvo.getId(), "Ativou usuário " + salvo.getNome());
        return mapper.toResponse(salvo);
    }

    @Transactional
    public UsuarioResponse desativar(Long id) {
        UsuarioEntity usuario = buscarEntidade(id);
        validarNaoSuperAdmin(usuario);
        usuario.setStatus(StatusUsuario.INATIVO);
        UsuarioEntity salvo = usuarioRepository.save(usuario);
        // Registrar auditoria
        adminAuditService.registrar("Desativar", "Usuário", salvo.getId(), "Desativou usuário " + salvo.getNome());
        logAtividadeService.registrar("USUARIO", salvo.getId(), "Desativou usuário " + salvo.getNome());
        return mapper.toResponse(salvo);
    }

    @Transactional(readOnly = true)
    public UsuarioEntity buscarEntidade(Long id) {
        UsuarioEntity usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario não encontrado."));
        validarEmpresaAtual(usuario.getEmpresa() == null ? null : usuario.getEmpresa().getId());
        return usuario;
    }

    @Transactional(readOnly = true)
    public UsuarioEntity buscarPorEmail(String email) {
        List<UsuarioEntity> usuarios = usuarioRepository.findUsuariosPainelByEmailIgnoreCase(email, PERFIS_PAINEL_DIRETOS);
        if (usuarios.isEmpty()) {
            throw new ResourceNotFoundException("Usuario não encontrado.");
        }
        if (usuarios.size() > 1) {
            throw new ConflictException("Dados de usuario duplicados. Contate o suporte para regularizacao.");
        }
        return usuarios.get(0);
    }

    private void validarNome(String nome) {
        if (!nome.matches("^[A-Za-zÀ-ÿ ]+$")) {
            throw new BusinessException("O nome deve conter apenas letras e espacos.");
        }
    }

    private void validarNaoSuperAdmin(UsuarioEntity usuario) {
        if (usuario.getPerfil() == PerfilUsuario.SUPER_ADMIN) {
            throw new BusinessException("SUPER_ADMIN so pode ser gerenciado pelo fluxo administrativo seguro.");
        }
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.requireCompanyId();
        if (empresaId == null || !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Usuario não encontrado.");
        }
    }
}

