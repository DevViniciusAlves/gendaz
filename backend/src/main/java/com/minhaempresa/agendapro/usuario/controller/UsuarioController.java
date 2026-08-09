package com.minhaempresa.agendapro.usuario.controller;

import com.minhaempresa.agendapro.usuario.dto.UsuarioDtos.AtualizarUsuarioRequest;
import com.minhaempresa.agendapro.usuario.dto.MembresiaDtos.AceitarConviteRequest;
import com.minhaempresa.agendapro.usuario.dto.MembresiaDtos.CriarConviteRequest;
import com.minhaempresa.agendapro.usuario.dto.MembresiaDtos.ConviteEmpresaResponse;
import com.minhaempresa.agendapro.usuario.dto.MembresiaDtos.ConvitePublicoResponse;
import com.minhaempresa.agendapro.usuario.dto.MembresiaDtos.MembroEmpresaResponse;
import com.minhaempresa.agendapro.usuario.dto.MembresiaDtos.RecusarConviteRequest;
import com.minhaempresa.agendapro.usuario.dto.UsuarioDtos.UsuarioResponse;
import com.minhaempresa.agendapro.auth.service.AuthService;
import com.minhaempresa.agendapro.shared.CookieHelper;
import com.minhaempresa.agendapro.usuario.service.UsuarioService;
import com.minhaempresa.agendapro.usuario.service.MembresiaService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {
    private final UsuarioService usuarioService;
    private final MembresiaService membresiaService;
    private final AuthService authService;

    @GetMapping("/empresa/{empresaId}/membros")
    public ResponseEntity<List<MembroEmpresaResponse>> listarMembros(@PathVariable Long empresaId) {
        return ResponseEntity.ok(membresiaService.listarMembros(empresaId));
    }

    @GetMapping("/empresa/{empresaId}/convites")
    public ResponseEntity<List<ConviteEmpresaResponse>> listarConvites(@PathVariable Long empresaId) {
        return ResponseEntity.ok(membresiaService.listarConvites(empresaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @PostMapping("/empresa/{empresaId}/convites")
    public ResponseEntity<ConviteEmpresaResponse> criarConvite(
            @PathVariable Long empresaId,
            @Valid @RequestBody CriarConviteRequest request,
            HttpServletRequest http
    ) {
        Long usuarioId = extrairUsuario(http);
        return ResponseEntity.ok(membresiaService.criarConvite(empresaId, usuarioId, request.nome(), request.telefone(), request.email()));
    }

    @PostMapping("/convites/{conviteId}/reenviar")
    public ResponseEntity<ConviteEmpresaResponse> reenviarConvite(@PathVariable Long conviteId, HttpServletRequest http) {
        Long usuarioId = extrairUsuario(http);
        Long empresaId = authService.buscarUsuarioAutenticado(usuarioId, CookieHelper.lerCookie(http, "agendapro_session").orElse(null)).getEmpresa().getId();
        return ResponseEntity.ok(membresiaService.reenviarConvite(empresaId, usuarioId, conviteId));
    }

    @DeleteMapping("/convites/{conviteId}")
    public ResponseEntity<ConviteEmpresaResponse> cancelarConvite(@PathVariable Long conviteId, HttpServletRequest http) {
        Long usuarioId = extrairUsuario(http);
        Long empresaId = authService.buscarUsuarioAutenticado(usuarioId, CookieHelper.lerCookie(http, "agendapro_session").orElse(null)).getEmpresa().getId();
        return ResponseEntity.ok(membresiaService.cancelarConvite(empresaId, usuarioId, conviteId));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<MembroEmpresaResponse> desativar(@PathVariable Long id, HttpServletRequest http) {
        Long usuarioId = extrairUsuario(http);
        Long empresaId = authService.buscarUsuarioAutenticado(usuarioId, CookieHelper.lerCookie(http, "agendapro_session").orElse(null)).getEmpresa().getId();
        return ResponseEntity.ok(membresiaService.desativarMembro(empresaId, usuarioId, id));
    }

    @PatchMapping("/{id}/reativar")
    public ResponseEntity<MembroEmpresaResponse> reativar(@PathVariable Long id, HttpServletRequest http) {
        Long usuarioId = extrairUsuario(http);
        Long empresaId = authService.buscarUsuarioAutenticado(usuarioId, CookieHelper.lerCookie(http, "agendapro_session").orElse(null)).getEmpresa().getId();
        return ResponseEntity.ok(membresiaService.reativarMembro(empresaId, usuarioId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MembroEmpresaResponse> remover(@PathVariable Long id, HttpServletRequest http) {
        Long usuarioId = extrairUsuario(http);
        Long empresaId = authService.buscarUsuarioAutenticado(usuarioId, CookieHelper.lerCookie(http, "agendapro_session").orElse(null)).getEmpresa().getId();
        return ResponseEntity.ok(membresiaService.removerMembro(empresaId, usuarioId, id));
    }

    @PostMapping("/{id}/transferir-propriedade")
    public ResponseEntity<MembroEmpresaResponse> transferir(@PathVariable Long id, HttpServletRequest http) {
        Long usuarioId = extrairUsuario(http);
        Long empresaId = authService.buscarUsuarioAutenticado(usuarioId, CookieHelper.lerCookie(http, "agendapro_session").orElse(null)).getEmpresa().getId();
        return ResponseEntity.ok(membresiaService.transferirPropriedade(empresaId, usuarioId, id));
    }

    @PostMapping("/convites/aceitar")
    public ResponseEntity<MembroEmpresaResponse> aceitarConvite(@Valid @RequestBody AceitarConviteRequest request) {
        return ResponseEntity.ok(membresiaService.aceitarConvite(request.token(), request));
    }

    @PostMapping("/convites/recusar")
    public ResponseEntity<ConviteEmpresaResponse> recusarConvite(@Valid @RequestBody RecusarConviteRequest request) {
        return ResponseEntity.ok(membresiaService.recusarConvite(request.token()));
    }

    @GetMapping("/convites/publico")
    public ResponseEntity<ConvitePublicoResponse> convitePublico(@RequestParam String token) {
        return ResponseEntity.ok(membresiaService.convitePublico(token));
    }

    @GetMapping("/empresa/{empresaId}/resumo")
    public ResponseEntity<?> resumo(@PathVariable Long empresaId) {
        return ResponseEntity.ok(java.util.Map.of(
                "limite", membresiaService.limiteEmpresa(empresaId),
                "usados", membresiaService.contarUsados(empresaId)
        ));
    }

    private Long extrairUsuario(HttpServletRequest http) {
        String cookie = CookieHelper.lerCookie(http, "agendapro_session").orElse(null);
        String header = http.getHeader("X-Usuario-Id");
        Long usuarioId = header == null || header.isBlank() ? null : Long.valueOf(header);
        return authService.buscarUsuarioAutenticado(usuarioId, cookie).getId();
    }
}
