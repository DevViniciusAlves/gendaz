package com.minhaempresa.gendaz.admin.controller;

import com.minhaempresa.gendaz.admin.dto.AdminCrmDtos.AdminCrmEmpresaResponse;
import com.minhaempresa.gendaz.admin.dto.AdminCrmDtos.AdminEnviarMensagemRequest;
import com.minhaempresa.gendaz.admin.service.AdminCrmService;
import com.minhaempresa.gendaz.crm.dto.CrmDtos.HistoricoContatoResponse;
import com.minhaempresa.gendaz.shared.BusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/crm")
@RequiredArgsConstructor
public class AdminCrmController {

    private final AdminCrmService adminCrmService;

    @GetMapping("/empresas")
    public ResponseEntity<?> listarEmpresas(
            HttpServletRequest http,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) Integer period
    ) {
        List<AdminCrmEmpresaResponse> empresas = adminCrmService.listarEmpresas(
                tokenAdmin(http), segment, search, orderBy, period);
        return ResponseEntity.ok(Map.of("empresas", empresas, "total", empresas.size()));
    }

    @PostMapping("/empresas/{empresaId}/enviar-mensagem")
    public ResponseEntity<?> enviarMensagem(
            HttpServletRequest http,
            @PathVariable Long empresaId,
            @Valid @RequestBody AdminEnviarMensagemRequest request
    ) {
        try {
            Map<String, Object> resultado = adminCrmService.enviarMensagem(tokenAdmin(http), empresaId, request);
            return ResponseEntity.ok(resultado);
        } catch (BusinessException e) {
            return ResponseEntity.ok(Map.of("success", false, "mensagem", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "mensagem", "Erro interno: " + e.getMessage()));
        }
    }

    @GetMapping("/empresas/{empresaId}/historico-contatos")
    public ResponseEntity<List<HistoricoContatoResponse>> historicoContatos(
            HttpServletRequest http,
            @PathVariable Long empresaId
    ) {
        return ResponseEntity.ok(adminCrmService.historicoContatos(tokenAdmin(http), empresaId));
    }

    private String tokenAdmin(HttpServletRequest request) {
        String headerToken = request.getHeader("X-Admin-Token");
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("agendeasy_admin_session".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
