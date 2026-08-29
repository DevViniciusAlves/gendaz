package com.minhaempresa.gendaz.financeiro.caixadespesas.controller;

import com.minhaempresa.gendaz.financeiro.caixadespesas.dto.CaixaDespesasDtos.AdicionarCaixaDespesasRequest;
import com.minhaempresa.gendaz.financeiro.caixadespesas.dto.CaixaDespesasDtos.CaixaDespesasTotaisResponse;
import com.minhaempresa.gendaz.financeiro.caixadespesas.dto.CaixaDespesasDtos.HistoricoResponse;
import com.minhaempresa.gendaz.financeiro.caixadespesas.service.CaixaDespesasService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class CaixaDespesasController {

    private final CaixaDespesasService service;
    private final UsuarioAutenticadoProvider usuarioProvider;

    @PostMapping("/{id}/caixa/adicionar")
    public ResponseEntity<CaixaDespesasTotaisResponse> adicionarCaixa(
            @PathVariable Long id, @Valid @RequestBody AdicionarCaixaDespesasRequest request) {
        usuarioProvider.exigirEmpresa(id);
        exigirDonoOuAdmin();
        return ResponseEntity.ok(service.adicionarCaixaManual(id, request.valor(), request.obs(), usuarioProvider.exigirUsuarioId()));
    }

    @PostMapping("/{id}/despesas/adicionar")
    public ResponseEntity<CaixaDespesasTotaisResponse> adicionarDespesas(
            @PathVariable Long id, @Valid @RequestBody AdicionarCaixaDespesasRequest request) {
        usuarioProvider.exigirEmpresa(id);
        exigirDonoOuAdmin();
        return ResponseEntity.ok(service.adicionarDespesasManual(id, request.valor(), request.obs(), usuarioProvider.exigirUsuarioId()));
    }

    @DeleteMapping("/{id}/caixa/{logId}")
    public ResponseEntity<CaixaDespesasTotaisResponse> removerCaixa(@PathVariable Long id, @PathVariable Long logId) {
        usuarioProvider.exigirEmpresa(id);
        exigirDonoOuAdmin();
        return ResponseEntity.ok(service.removerCaixaManual(id, logId, usuarioProvider.exigirUsuarioId()));
    }

    @DeleteMapping("/{id}/despesas/{logId}")
    public ResponseEntity<CaixaDespesasTotaisResponse> removerDespesas(@PathVariable Long id, @PathVariable Long logId) {
        usuarioProvider.exigirEmpresa(id);
        exigirDonoOuAdmin();
        return ResponseEntity.ok(service.removerDespesasManual(id, logId, usuarioProvider.exigirUsuarioId()));
    }

    @PostMapping("/{id}/despesas/remover")
    public ResponseEntity<CaixaDespesasTotaisResponse> removerValorDespesas(
            @PathVariable Long id, @Valid @RequestBody AdicionarCaixaDespesasRequest request) {
        usuarioProvider.exigirEmpresa(id);
        exigirDonoOuAdmin();
        return ResponseEntity.ok(service.removerValorDespesasManual(id, request.valor(), request.obs(), usuarioProvider.exigirUsuarioId()));
    }

    @PostMapping("/{id}/caixa/remover")
    public ResponseEntity<CaixaDespesasTotaisResponse> removerValorCaixa(
            @PathVariable Long id, @Valid @RequestBody AdicionarCaixaDespesasRequest request) {
        usuarioProvider.exigirEmpresa(id);
        exigirDonoOuAdmin();
        return ResponseEntity.ok(service.removerValorCaixaManual(id, request.valor(), request.obs(), usuarioProvider.exigirUsuarioId()));
    }

    @GetMapping("/{id}/caixa-despesas/totais")
    public ResponseEntity<CaixaDespesasTotaisResponse> totais(@PathVariable Long id) {
        usuarioProvider.exigirEmpresa(id);
        return ResponseEntity.ok(service.buscarTotais(id));
    }

    @GetMapping("/{id}/caixa-despesas/historico")
    public ResponseEntity<HistoricoResponse> historico(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {
        usuarioProvider.exigirEmpresa(id);
        return ResponseEntity.ok(service.listarHistorico(id, page, limit));
    }

    private void exigirDonoOuAdmin() {
        PerfilUsuario perfil = usuarioProvider.exigirPerfil();
        if (perfil != PerfilUsuario.DONO && perfil != PerfilUsuario.SUPER_ADMIN) {
            throw new BusinessException("Seu perfil nao permite gerenciar o caixa e as despesas.");
        }
    }
}
