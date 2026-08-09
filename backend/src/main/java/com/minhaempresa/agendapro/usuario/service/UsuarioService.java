package com.minhaempresa.agendapro.usuario.service;

import com.minhaempresa.agendapro.auth.service.PasswordService;
import com.minhaempresa.agendapro.email.ResendEmailService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.service.EmpresaService;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ConflictException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.usuario.dto.UsuarioDtos.AtualizarUsuarioRequest;
import com.minhaempresa.agendapro.usuario.dto.UsuarioDtos.CriarUsuarioRequest;
import com.minhaempresa.agendapro.usuario.dto.UsuarioDtos.UsuarioResponse;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.enums.StatusUsuario;
import com.minhaempresa.agendapro.usuario.mapper.UsuarioMapper;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
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
    private final UsuarioMapper mapper = new UsuarioMapper();

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
            log.warn("Usuario criado, mas o email de boas-vindas nao foi enviado para {}", salvo.getEmail());
        }
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
        usuario.setNome(sanitizacaoService.textoObrigatorio(request.nome()));
        usuario.setEmail(sanitizacaoService.email(request.email()));
        usuario.setPerfil(request.perfil());
        return mapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse ativar(Long id) {
        UsuarioEntity usuario = buscarEntidade(id);
        validarNaoSuperAdmin(usuario);
        usuario.setStatus(StatusUsuario.ATIVO);
        return mapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse desativar(Long id) {
        UsuarioEntity usuario = buscarEntidade(id);
        validarNaoSuperAdmin(usuario);
        usuario.setStatus(StatusUsuario.INATIVO);
        return mapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional(readOnly = true)
    public UsuarioEntity buscarEntidade(Long id) {
        UsuarioEntity usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado."));
        validarEmpresaAtual(usuario.getEmpresa() == null ? null : usuario.getEmpresa().getId());
        return usuario;
    }

    @Transactional(readOnly = true)
    public UsuarioEntity buscarPorEmail(String email) {
        List<UsuarioEntity> usuarios = usuarioRepository.findUsuariosPainelByEmailIgnoreCase(email, PERFIS_PAINEL_DIRETOS);
        if (usuarios.isEmpty()) {
            throw new ResourceNotFoundException("Usuario nao encontrado.");
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
        Long companyId = CompanyContext.getCompanyId();
        if (companyId != null && empresaId != null && !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Usuario nao encontrado.");
        }
    }
}
