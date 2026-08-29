package com.minhaempresa.gendaz.shared.security;

import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.SessaoExpiradaException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioAutenticadoProvider {
    private final UsuarioRepository usuarioRepository;

    public Long exigirUsuarioId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw new SessaoExpiradaException("Usuario autenticado obrigatorio.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        if (principal instanceof Number number) {
            return number.longValue();
        }
        throw new SessaoExpiradaException("Usuario autenticado invalido.");
    }

    @Transactional(readOnly = true)
    public UsuarioEntity exigirUsuario() {
        Long usuarioId = exigirUsuarioId();
        return usuarioRepository.findByIdComEmpresa(usuarioId)
                .orElseThrow(() -> new SessaoExpiradaException("Usuario autenticado invalido."));
    }

    @Transactional(readOnly = true)
    public PerfilUsuario exigirPerfil() {
        return exigirUsuario().getPerfil();
    }

    public Long exigirEmpresaId() {
        Long empresaId = CompanyContext.getCompanyId();
        if (empresaId == null) {
            throw new BusinessException("Empresa autenticada obrigatoria.");
        }
        return empresaId;
    }

    public void exigirEmpresa(Long empresaId) {
        Long empresaContexto = exigirEmpresaId();
        if (empresaId == null || !empresaContexto.equals(empresaId)) {
            throw new BusinessException("Empresa da sessão não corresponde ao recurso solicitado.");
        }
    }
}
