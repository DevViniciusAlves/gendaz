package com.minhaempresa.agendapro.auth.service;

import com.minhaempresa.agendapro.auth.websocket.SessionWebSocketHandler;
import com.minhaempresa.agendapro.empresa.enums.StatusEmpresa;
import com.minhaempresa.agendapro.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.agendapro.meugendazacesso.repository.MeuGendazAcessoRepository;
import com.minhaempresa.agendapro.shared.SessaoExpiradaException;
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
    private final MeuGendazAcessoRepository meuGendazAcessoRepository;
    private final SessionWebSocketHandler sessionWebSocketHandler;

    @Transactional
    public synchronized String renovarSessao(UsuarioEntity usuario) {
        String sessao = UUID.randomUUID().toString();
        UsuarioEntity usuarioBloqueado = usuarioRepository.findByIdForUpdate(usuario.getId())
                .orElseThrow(() -> new SessaoExpiradaException("Usuario autenticado invalido."));
        usuarioBloqueado.setSessaoAtiva(sessao);
        usuarioRepository.save(usuarioBloqueado);

        sessionWebSocketHandler.notifySessionInvalidated(usuario.getId(), sessao);

        return sessao;
    }

    @Transactional
    public synchronized String renovarSessao(UsuarioEntity usuario, String sessionTokenAtual) {
        if (sessionTokenAtual != null && !sessionTokenAtual.isBlank()
                && sessionTokenAtual.equals(usuario.getSessaoAtiva())) {
            return sessionTokenAtual;
        }
        return renovarSessao(usuario);
    }

    @Transactional
    public synchronized String renovarSessao(UsuarioEntity usuario, Long empresaId) {
        if (usuario == null || empresaId == null || usuario.getEmpresa() == null || !empresaId.equals(usuario.getEmpresa().getId())) {
            throw new SessaoExpiradaException("Usuario autenticado invalido.");
        }
        return renovarSessao(usuario);
    }

    @Transactional
    public synchronized String obterOuCriarSessao(UsuarioEntity usuario) {
        UsuarioEntity usuarioBloqueado = usuarioRepository.findByIdForUpdate(usuario.getId())
                .orElseThrow(() -> new SessaoExpiradaException("Usuario autenticado invalido."));
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

    @Transactional(readOnly = true)
    public boolean sessaoValida(Long usuarioId, String sessao, Long empresaId) {
        if (usuarioId == null || sessao == null || sessao.isBlank() || empresaId == null) {
            return false;
        }
        return usuarioRepository.findById(usuarioId)
                .filter(usuario -> usuario.getEmpresa() != null)
                .filter(usuario -> empresaId.equals(usuario.getEmpresa().getId()))
                .filter(usuario -> usuario.getStatus() == StatusUsuario.ATIVO)
                .filter(usuario -> sessao.equals(usuario.getSessaoAtiva()))
                .isPresent();
    }

    @Transactional
    public void encerrarSessao(Long usuarioId, String sessao) {
        if (usuarioId == null) {
            return;
        }
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new SessaoExpiradaException("Usuario autenticado invalido."));
        if (sessao == null || sessao.equals(usuario.getSessaoAtiva())) {
            usuario.setSessaoAtiva(null);
            usuarioRepository.save(usuario);
        }
    }

    @Transactional
    public synchronized String criarSessaoMeuGendaz(MeuGendazAcessoEntity acesso) {
        String sessao = UUID.randomUUID().toString();
        MeuGendazAcessoEntity acessoAtual = meuGendazAcessoRepository.findById(acesso.getId())
                .orElseThrow(() -> new SessaoExpiradaException("Acesso do Meu Gendaz invalido."));
        acessoAtual.setSessaoAtiva(sessao);
        meuGendazAcessoRepository.save(acessoAtual);
        return sessao;
    }

    @Transactional
    public synchronized String renovarSessaoMeuGendaz(MeuGendazAcessoEntity acesso, String sessionTokenAtual) {
        if (sessionTokenAtual != null && !sessionTokenAtual.isBlank()
                && sessionTokenAtual.equals(acesso.getSessaoAtiva())) {
            return sessionTokenAtual;
        }
        return criarSessaoMeuGendaz(acesso);
    }

    @Transactional(readOnly = true)
    public boolean sessaoValidaMeuGendaz(Long acessoId, String sessao, Long empresaId) {
        if (acessoId == null || sessao == null || sessao.isBlank() || empresaId == null) {
            return false;
        }
        return meuGendazAcessoRepository.findById(acessoId)
                .filter(acesso -> acesso.getEmpresa() != null)
                .filter(acesso -> empresaId.equals(acesso.getEmpresa().getId()))
                .filter(acesso -> acesso.getStatus() == StatusUsuario.ATIVO)
                .filter(acesso -> sessao.equals(acesso.getSessaoAtiva()))
                .isPresent();
    }

    @Transactional
    public void encerrarSessaoMeuGendaz(Long acessoId, String sessao) {
        if (acessoId == null) {
            return;
        }
        MeuGendazAcessoEntity acesso = meuGendazAcessoRepository.findById(acessoId)
                .orElseThrow(() -> new SessaoExpiradaException("Acesso do Meu Gendaz invalido."));
        if (sessao == null || sessao.equals(acesso.getSessaoAtiva())) {
            acesso.setSessaoAtiva(null);
            meuGendazAcessoRepository.save(acesso);
        }
    }
}
