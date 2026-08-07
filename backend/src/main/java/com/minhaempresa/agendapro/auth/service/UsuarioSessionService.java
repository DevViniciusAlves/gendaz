package com.minhaempresa.agendapro.auth.service;

import com.minhaempresa.agendapro.auth.websocket.SessionWebSocketHandler;
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
    private final SessionWebSocketHandler sessionWebSocketHandler;

    @Transactional
    public synchronized String renovarSessao(UsuarioEntity usuario) {
        String sessao = UUID.randomUUID().toString();
        UsuarioEntity usuarioBloqueado = usuarioRepository.findByIdForUpdate(usuario.getId())
                .orElseThrow(() -> new BusinessException("Usuario autenticado invalido."));
        usuarioBloqueado.setSessaoAtiva(sessao);
        usuarioRepository.save(usuarioBloqueado);
        
        sessionWebSocketHandler.notifySessionInvalidated(usuario.getId(), sessao);
        
        return sessao;
    }

    /**
     * Renova a sessão de forma idempotente: se o token informado ainda é o ativo,
     * mantém o mesmo token (evita race de rotacao em refreshes concorrentes, ex: F5).
     * Só gera um novo token quando a sessão informada não é mais a ativa.
     */
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
            throw new BusinessException("Usuario autenticado invalido.");
        }
        return renovarSessao(usuario);
    }

    @Transactional
    public synchronized String obterOuCriarSessao(UsuarioEntity usuario) {
        UsuarioEntity usuarioBloqueado = usuarioRepository.findByIdForUpdate(usuario.getId())
                .orElseThrow(() -> new BusinessException("Usuario autenticado invalido."));
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
                .orElseThrow(() -> new BusinessException("Usuario autenticado invalido."));
        if (sessao == null || sessao.equals(usuario.getSessaoAtiva())) {
            usuario.setSessaoAtiva(null);
            usuarioRepository.save(usuario);
        }
    }

    /**
     * Sessões do Meu Gendaz usam um slot próprio, separado do painel.
     * Assim o login do cliente no Meu Gendaz não derruba a sessão do dono
     * no painel (e vice-versa). Não notifica o WebSocket de sessão do painel.
     */
    @Transactional
    public synchronized String criarSessaoMeuGendaz(UsuarioEntity usuario) {
        String sessao = UUID.randomUUID().toString();
        UsuarioEntity usuarioBloqueado = usuarioRepository.findByIdForUpdate(usuario.getId())
                .orElseThrow(() -> new BusinessException("Usuario autenticado invalido."));
        usuarioBloqueado.setSessaoAtivaMeuGendaz(sessao);
        usuarioRepository.save(usuarioBloqueado);
        return sessao;
    }

    /**
     * Renova a sessão do Meu Gendaz de forma idempotente: se o token informado
     * ainda é o ativo, mantém o mesmo. Só gera um novo quando o informado não é mais o ativo.
     */
    @Transactional
    public synchronized String renovarSessaoMeuGendaz(UsuarioEntity usuario, String sessionTokenAtual) {
        if (sessionTokenAtual != null && !sessionTokenAtual.isBlank()
                && sessionTokenAtual.equals(usuario.getSessaoAtivaMeuGendaz())) {
            return sessionTokenAtual;
        }
        return criarSessaoMeuGendaz(usuario);
    }

    @Transactional(readOnly = true)
    public boolean sessaoValidaMeuGendaz(Long usuarioId, String sessao, Long empresaId) {
        if (usuarioId == null || sessao == null || sessao.isBlank() || empresaId == null) {
            return false;
        }
        return usuarioRepository.findById(usuarioId)
                .filter(usuario -> usuario.getEmpresa() != null)
                .filter(usuario -> empresaId.equals(usuario.getEmpresa().getId()))
                .filter(usuario -> usuario.getStatus() == StatusUsuario.ATIVO)
                .filter(usuario -> sessao.equals(usuario.getSessaoAtivaMeuGendaz()))
                .isPresent();
    }

    @Transactional
    public void encerrarSessaoMeuGendaz(Long usuarioId, String sessao) {
        if (usuarioId == null) {
            return;
        }
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuario autenticado invalido."));
        if (sessao == null || sessao.equals(usuario.getSessaoAtivaMeuGendaz())) {
            usuario.setSessaoAtivaMeuGendaz(null);
            usuarioRepository.save(usuario);
        }
    }
}
