package com.minhaempresa.gendaz.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.auth.websocket.SessionWebSocketHandler;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.gendaz.meugendazacesso.repository.MeuGendazAcessoRepository;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsuarioSessionServiceTest {
    @Mock UsuarioRepository usuarioRepository;
    @Mock MeuGendazAcessoRepository meuGendazAcessoRepository;
    @Mock SessionWebSocketHandler sessionWebSocketHandler;
    @InjectMocks UsuarioSessionService service;

    @Test
    void encerrarSessaoNaoAceitaTokenNullComoAutorizacao() {
        service.encerrarSessao(null);

        verify(usuarioRepository, never()).findBySessaoAtiva(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void encerrarSessaoNaoAceitaTokenVazioOuBlank() {
        service.encerrarSessao("");
        service.encerrarSessao("   ");

        verify(usuarioRepository, never()).findBySessaoAtiva(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void encerrarSessaoComTokenInexistenteNaoAlteraUsuario() {
        when(usuarioRepository.findBySessaoAtiva("token-inexistente")).thenReturn(Optional.empty());

        service.encerrarSessao("token-inexistente");

        verify(usuarioRepository).findBySessaoAtiva("token-inexistente");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void encerrarSessaoEncerraSomenteUsuarioDonoDoToken() {
        UsuarioEntity usuario = UsuarioEntity.builder().id(1L).sessaoAtiva("token-a").build();
        when(usuarioRepository.findBySessaoAtiva("token-a")).thenReturn(Optional.of(usuario));

        service.encerrarSessao("token-a");

        assertEquals(null, usuario.getSessaoAtiva());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void encerrarSessaoMeuGendazNaoAceitaTokenNullComoAutorizacao() {
        service.encerrarSessaoMeuGendaz(10L, null);

        verify(meuGendazAcessoRepository, never()).findById(any());
        verify(meuGendazAcessoRepository, never()).save(any());
    }

    @Test
    void encerrarSessaoMeuGendazNaoAceitaTokenVazioOuBlank() {
        service.encerrarSessaoMeuGendaz(10L, "");
        service.encerrarSessaoMeuGendaz(10L, "   ");

        verify(meuGendazAcessoRepository, never()).findById(any());
        verify(meuGendazAcessoRepository, never()).save(any());
    }

    @Test
    void encerrarSessaoMeuGendazComTokenErradoNaoAlteraAcesso() {
        MeuGendazAcessoEntity acesso = MeuGendazAcessoEntity.builder()
                .id(20L)
                .empresa(EmpresaEntity.builder().id(1L).build())
                .sessaoAtiva("token-b")
                .build();
        when(meuGendazAcessoRepository.findById(20L)).thenReturn(Optional.of(acesso));

        service.encerrarSessaoMeuGendaz(20L, "token-a");

        assertEquals("token-b", acesso.getSessaoAtiva());
        verify(meuGendazAcessoRepository, never()).save(any());
    }

    @Test
    void encerrarSessaoMeuGendazComTokenCorretoEncerraSessao() {
        MeuGendazAcessoEntity acesso = MeuGendazAcessoEntity.builder()
                .id(10L)
                .empresa(EmpresaEntity.builder().id(1L).build())
                .sessaoAtiva("token-a")
                .build();
        when(meuGendazAcessoRepository.findById(10L)).thenReturn(Optional.of(acesso));

        service.encerrarSessaoMeuGendaz(10L, "token-a");

        assertEquals(null, acesso.getSessaoAtiva());
        verify(meuGendazAcessoRepository).save(acesso);
    }
}
