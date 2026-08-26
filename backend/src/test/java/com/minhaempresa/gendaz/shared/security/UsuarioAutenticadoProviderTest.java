package com.minhaempresa.gendaz.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.SessaoExpiradaException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class UsuarioAutenticadoProviderTest {
    @Mock
    private UsuarioRepository usuarioRepository;

    @AfterEach
    void limparContextos() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    @Test
    void exigirUsuarioIdUsaPrincipalDoSecurityContext() {
        autenticar(20L);
        UsuarioAutenticadoProvider provider = new UsuarioAutenticadoProvider(usuarioRepository);

        assertEquals(20L, provider.exigirUsuarioId());
    }

    @Test
    void exigirUsuarioFalhaSemSecurityContext() {
        UsuarioAutenticadoProvider provider = new UsuarioAutenticadoProvider(usuarioRepository);

        assertThrows(SessaoExpiradaException.class, provider::exigirUsuarioId);
    }

    @Test
    void exigirUsuarioCarregaPorIdComEmpresa() {
        autenticar(20L);
        UsuarioEntity usuario = UsuarioEntity.builder()
                .id(20L)
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .empresa(EmpresaEntity.builder().id(10L).build())
                .build();
        when(usuarioRepository.findByIdComEmpresa(20L)).thenReturn(Optional.of(usuario));
        UsuarioAutenticadoProvider provider = new UsuarioAutenticadoProvider(usuarioRepository);

        assertEquals(10L, provider.exigirUsuario().getEmpresa().getId());
    }

    @Test
    void exigirEmpresaFalhaFechadoSemCompanyContext() {
        UsuarioAutenticadoProvider provider = new UsuarioAutenticadoProvider(usuarioRepository);

        assertThrows(BusinessException.class, () -> provider.exigirEmpresa(10L));
    }

    @Test
    void exigirEmpresaBloqueiaEmpresaDivergente() {
        CompanyContext.setCompanyId(10L);
        UsuarioAutenticadoProvider provider = new UsuarioAutenticadoProvider(usuarioRepository);

        assertThrows(BusinessException.class, () -> provider.exigirEmpresa(99L));
    }

    @Test
    void exigirEmpresaPermiteEmpresaDoContexto() {
        CompanyContext.setCompanyId(10L);
        UsuarioAutenticadoProvider provider = new UsuarioAutenticadoProvider(usuarioRepository);

        provider.exigirEmpresa(10L);
    }

    private void autenticar(Long usuarioId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuarioId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_DONO"))
        ));
    }
}
