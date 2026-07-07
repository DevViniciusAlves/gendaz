package com.minhaempresa.agendapro.whatsapp.controller;

import com.minhaempresa.agendapro.auth.service.AuthService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.shared.CookieHelper;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.ConectarWhatsappRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConfigResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConnectRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConnectResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappPreferenciasRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappStatusResponse;
import com.minhaempresa.agendapro.whatsapp.service.WhatsappIntegrationProperties;
import com.minhaempresa.agendapro.whatsapp.service.WhatsappIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
public class WhatsappController {
    private final WhatsappIntegrationService whatsappService;
    private final AuthService authService;
    private final WhatsappIntegrationProperties properties;

    @GetMapping("/status")
    public ResponseEntity<WhatsappStatusResponse> status(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request
    ) {
        Long usuarioAutenticado = usuarioAutenticado(usuarioId, request);
        return ResponseEntity.<WhatsappStatusResponse>ok(whatsappService.status(usuarioAutenticado));
    }

    @GetMapping("/config/{tenantId}")
    public ResponseEntity<WhatsappConfigResponse> config(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request,
            @PathVariable Long tenantId
    ) {
        Long usuarioAutenticado = usuarioAutenticado(usuarioId, request);
        EmpresaEntity empresa = authService.buscarUsuarioAutenticado(usuarioAutenticado).getEmpresa();
        validarEmpresa(empresa, tenantId);
        return ResponseEntity.ok(whatsappService.contextoDaEmpresa(tenantId));
    }

    @PutMapping("/config/{tenantId}")
    public ResponseEntity<WhatsappConfigResponse> atualizarConfig(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request,
            @PathVariable Long tenantId,
            @Valid @RequestBody WhatsappPreferenciasRequest body
    ) {
        Long usuarioAutenticado = usuarioAutenticado(usuarioId, request);
        EmpresaEntity empresa = authService.buscarUsuarioAutenticado(usuarioAutenticado).getEmpresa();
        validarEmpresa(empresa, tenantId);
        if (body.empresaId() != null && !tenantId.equals(body.empresaId())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Empresa nao autorizada.");
        }
        WhatsappPreferenciasRequest requestConfig = new WhatsappPreferenciasRequest(
                tenantId,
                body.notificacoesAutomaticas(),
                body.secretariaIaAtiva(),
                body.descricaoEmpresa(),
                body.mensagemBoasVindas(),
                body.respostaHorarios(),
                body.respostaServicos(),
                body.respostaNaoEntende(),
                body.mensagemHumano()
        );
        whatsappService.atualizarPreferencias(requestConfig);
        return ResponseEntity.ok(whatsappService.contextoDaEmpresa(tenantId));
    }

    @PostMapping("/conectar")
    public ResponseEntity<WhatsappConnectResponse> iniciarConexao(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request,
            @Valid @RequestBody ConectarWhatsappRequest body
    ) {
        Long usuarioAutenticado = usuarioAutenticado(usuarioId, request);
        EmpresaEntity empresa = authService.buscarUsuarioAutenticado(usuarioAutenticado).getEmpresa();
        WhatsappConnectRequest bodyInterno = new WhatsappConnectRequest(empresa.getId(), body.phone());
        return ResponseEntity.<WhatsappConnectResponse>ok(whatsappService.conectar(usuarioAutenticado, bodyInterno));
    }

    @PostMapping("/desconectar")
    public ResponseEntity<WhatsappStatusResponse> desconectar(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request
    ) {
        Long usuarioAutenticado = usuarioAutenticado(usuarioId, request);
        return ResponseEntity.<WhatsappStatusResponse>ok(whatsappService.desconectar(usuarioAutenticado));
    }

    @GetMapping("/status/{tenantId}")
    public ResponseEntity<WhatsappStatusResponse> statusPorEmpresa(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request,
            @PathVariable Long tenantId
    ) {
        Long usuarioAutenticado = usuarioAutenticado(usuarioId, request);
        EmpresaEntity empresa = authService.buscarUsuarioAutenticado(usuarioAutenticado).getEmpresa();
        validarEmpresa(empresa, tenantId);
        return ResponseEntity.ok(whatsappService.status(usuarioAutenticado));
    }

    @PostMapping("/desconectar/{tenantId}")
    public ResponseEntity<WhatsappStatusResponse> desconectarPorEmpresa(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request,
            @PathVariable Long tenantId
    ) {
        Long usuarioAutenticado = usuarioAutenticado(usuarioId, request);
        EmpresaEntity empresa = authService.buscarUsuarioAutenticado(usuarioAutenticado).getEmpresa();
        validarEmpresa(empresa, tenantId);
        return ResponseEntity.<WhatsappStatusResponse>ok(whatsappService.desconectar(usuarioAutenticado));
    }

    @PostMapping("/status-update")
    public ResponseEntity<WhatsappStatusResponse> statusUpdate(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappStatusUpdateRequest body
    ) {
        validarTokenInterno(internalToken);
        return ResponseEntity.<WhatsappStatusResponse>ok(whatsappService.atualizarStatusEmpresa(body.empresaId(), body.status(), body.phoneNumber(), body.pairingCode()));
    }

    private Long usuarioAutenticado(Long usuarioId, HttpServletRequest request) {
        String sessionToken = CookieHelper.lerCookie(request, "agendapro_session").orElse(null);
        return authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
    }

    private void validarTokenInterno(String recebido) {
        String esperado = properties.internalToken();
        if (esperado.isBlank()) {
            return;
        }
        if (recebido == null || recebido.isBlank() || !esperado.equals(recebido.trim())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook interno nao autorizado.");
        }
    }

    private void validarEmpresa(EmpresaEntity empresa, Long tenantId) {
        if (empresa == null || tenantId == null || !tenantId.equals(empresa.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Empresa nao autorizada.");
        }
    }
}
