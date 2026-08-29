package com.minhaempresa.gendaz.promocao.controller;

import com.minhaempresa.gendaz.promocao.dto.PromocaoDtos.*;
import com.minhaempresa.gendaz.promocao.service.PromocaoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promocoes")
@RequiredArgsConstructor
public class PromocaoController {
    private final PromocaoService promocaoService;

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(value = "empresaId", required = false) Long empresaId) {
        return ResponseEntity.ok(promocaoService.listar(resolverEmpresaId(empresaId)));
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody @Valid PromocaoRequest request,
                                   @RequestParam(value = "empresaId", required = false) Long empresaId) {
        return ResponseEntity.status(201).body(promocaoService.criar(resolverEmpresaId(empresaId), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody @Valid PromocaoRequest request,
                                       @RequestParam(value = "empresaId", required = false) Long empresaId) {
        return ResponseEntity.ok(promocaoService.atualizar(resolverEmpresaId(empresaId), id, request));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<?> desativar(@PathVariable Long id,
                                       @RequestParam(value = "empresaId", required = false) Long empresaId) {
        promocaoService.desativar(resolverEmpresaId(empresaId), id);
        return ResponseEntity.ok(Map.of("mensagem", "Promocao desativada"));
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<?> ativar(@PathVariable Long id,
                                    @RequestParam(value = "empresaId", required = false) Long empresaId) {
        promocaoService.ativar(resolverEmpresaId(empresaId), id);
        return ResponseEntity.ok(Map.of("mensagem", "Promocao ativada"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluir(@PathVariable Long id,
                                     @RequestParam(value = "empresaId", required = false) Long empresaId) {
        promocaoService.excluir(resolverEmpresaId(empresaId), id);
        return ResponseEntity.ok(Map.of("mensagem", "Promocao excluida permanentemente"));
    }

    @PostMapping("/{id}/notificar")
    public ResponseEntity<?> notificar(@PathVariable Long id, @RequestBody @Valid PromocaoNotificarRequest request,
                                       @RequestParam(value = "empresaId", required = false) Long empresaId) {
        String mensagem = promocaoService.notificarClientes(resolverEmpresaId(empresaId), id, request);
        return ResponseEntity.ok(Map.of("mensagem", mensagem));
    }

    @GetMapping("/{id}/uso")
    public ResponseEntity<?> uso(@PathVariable Long id,
                                 @RequestParam(value = "empresaId", required = false) Long empresaId) {
        return ResponseEntity.ok(promocaoService.resumo(resolverEmpresaId(empresaId), id));
    }

    @GetMapping("/{id}/historico")
    public ResponseEntity<List<PromocaoUsoResponse>> historico(@PathVariable Long id,
                                                               @RequestParam(value = "empresaId", required = false) Long empresaId) {
        return ResponseEntity.ok(promocaoService.listarUsos(resolverEmpresaId(empresaId), id));
    }

    private Long resolverEmpresaId(Long empresaId) {
        Long empresaContexto = CompanyContext.requireCompanyId();
        if (empresaId != null && !empresaContexto.equals(empresaId)) {
                throw new BusinessException("Empresa da sessão não corresponde a Promocoes solicitadas.");
            }
        return empresaContexto;
    }
}

