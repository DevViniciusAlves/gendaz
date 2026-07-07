package com.minhaempresa.agendapro.auth.service;

import com.minhaempresa.agendapro.empresa.enums.StatusEmpresa;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.enums.StatusUsuario;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioSessionService {
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public synchronized String renovarSessao(UsuarioEntity usuario) {
        String sessao = UUID.randomUUID().toString();
        UsuarioEntity usuarioBloqueado = usuarioRepository.findByIdForUpdate(usuario.getId())
                .orElseThrow(() -> new BusinessException("Usuário autenticado inválido."));
        usuarioBloqueado.setSessaoAtiva(sessao);
        usuarioRepository.save(usuarioBloqueado);
        return sessao;
    }

    @Transactional
    public synchronized String obterOuCriarSessao(UsuarioEntity usuario) {
        UsuarioEntity usuarioBloqueado = usuarioRepository.findByIdForUpdate(usuario.getId())
                .orElseThrow(() -> new BusinessException("Usuário autenticado inválido."));
        if (usuarioBloqueado.getSessaoAtiva() != null && !usuarioBloqueado.getSessaoAtiva().isBlank()) {
            return usuarioBloqueado.getSessaoAtiva();
        }
        String sessao = UUID.randomUUID().toString();
        usuarioBloqueado.setSessaoAtiva(sessao);
        usuarioRepository.save(usuarioBloqueado);
        return sessao;
    }

    @Transactional(readOnly = true)
    public String sessaoAtual(Long usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        return usuarioRepository.findById(usuarioId)
                .map(UsuarioEntity::getSessaoAtiva)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean sessaoValida(Long usuarioId, String sessao) {
        if (usuarioId == null || sessao == null || sessao.isBlank()) {
            return false;
        }
        return usuarioRepository.findById(usuarioId)
                .filter(usuario -> usuario.getStatus() == StatusUsuario.ATIVO)
                .filter(usuario -> sessao.equals(usuario.getSessaoAtiva()))
                .filter(usuario -> usuario.getPerfil() == PerfilUsuario.SUPER_ADMIN
                        || usuario.getEmpresa() == null
                        || usuario.getEmpresa().getStatus() == StatusEmpresa.ATIVA)
                .isPresent();
    }

    @Transactional
    public void encerrarSessao(Long usuarioId, String sessao) {
        if (usuarioId == null) {
            return;
        }
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuário autenticado inválido."));
        if (sessao == null || sessao.equals(usuario.getSessaoAtiva())) {
            usuario.setSessaoAtiva(null);
            usuarioRepository.save(usuario);
        }
    }
}
