package com.minhaempresa.gendaz.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GendazSessionAuthenticationFilterReactivationTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @AfterEach
    void limparContextos() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    @Test
    void empresaInativaDeveAcessarConsultarPlanoAtual() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        UsuarioEntity usuario = usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.INATIVA);
        when(usuarioRepository.findBySessaoAtiva("sessão-valida")).thenReturn(Optional.of(usuario));
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/pagamentos/planos/empresa/30/atual");
        request.setCookies(new Cookie("Gendaz_session", "sessão-valida"));
        request.addHeader("Origin", "https://gendaz.site");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void empresaInativaDeveAcessarIniciarPagamentoBasico() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        UsuarioEntity usuario = usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.INATIVA);
        when(usuarioRepository.findBySessaoAtiva("sessão-valida")).thenReturn(Optional.of(usuario));
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/pagamentos/planos/basico/iniciar");
        request.setCookies(new Cookie("Gendaz_session", "sessão-valida"));
        request.addHeader("Origin", "https://gendaz.site");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void empresaInativaDeveAcessarIniciarPagamentoPro() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        UsuarioEntity usuario = usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.INATIVA);
        when(usuarioRepository.findBySessaoAtiva("sessão-valida")).thenReturn(Optional.of(usuario));
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/pagamentos/planos/pro/iniciar");
        request.setCookies(new Cookie("Gendaz_session", "sessão-valida"));
        request.addHeader("Origin", "https://gendaz.site");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void empresaInativaNaoDeveAcessarRotaComum() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        UsuarioEntity usuario = usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.INATIVA);
        when(usuarioRepository.findBySessaoAtiva("sessão-valida")).thenReturn(Optional.of(usuario));
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/dashboard/resumo");
        request.setCookies(new Cookie("Gendaz_session", "sessão-valida"));
        request.addHeader("Origin", "https://gendaz.site");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void empresaBloqueadaNaoDeveAcessarRotaDeReativacao() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        UsuarioEntity usuario = usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.BLOQUEADA);
        when(usuarioRepository.findBySessaoAtiva("sessão-valida")).thenReturn(Optional.of(usuario));
        
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pagamentos/planos/empresa/30/atual");
        request.setCookies(new Cookie("Gendaz_session", "sessão-valida"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void usuarioInativoNaoDeveAcessarRotaDeReativacao() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        UsuarioEntity usuario = usuario(20L, 30L, StatusUsuario.INATIVO, StatusEmpresa.INATIVA);
        when(usuarioRepository.findBySessaoAtiva("sessão-valida")).thenReturn(Optional.of(usuario));
        
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pagamentos/planos/empresa/30/atual");
        request.setCookies(new Cookie("Gendaz_session", "sessão-valida"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void empresaAtivaDeveAcessarRotaComum() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        UsuarioEntity usuario = usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.ATIVA);
        when(usuarioRepository.findBySessaoAtiva("sessão-valida")).thenReturn(Optional.of(usuario));
        
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/resumo");
        request.setCookies(new Cookie("Gendaz_session", "sessão-valida"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    private GendazSessionAuthenticationFilter filtro() {
        GendazSessionAuthenticationFilter filter = new GendazSessionAuthenticationFilter(usuarioRepository, null);
        ReflectionTestUtils.setField(filter, "frontendUrl", "https://gendaz.site");
        return filter;
    }

    private UsuarioEntity usuario(Long usuarioId, Long empresaId, StatusUsuario statusUsuario, StatusEmpresa statusEmpresa) {
        return UsuarioEntity.builder()
                .id(usuarioId)
                .nome("Usuario")
                .email("usuario@gendaz.test")
                .senha("hash")
                .perfil(PerfilUsuario.DONO)
                .status(statusUsuario)
                .empresa(EmpresaEntity.builder().id(empresaId).status(statusEmpresa).build())
                .aceitouTermos(true)
                .build();
    }
}