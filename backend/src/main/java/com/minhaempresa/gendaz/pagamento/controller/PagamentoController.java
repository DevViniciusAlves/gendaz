package com.minhaempresa.gendaz.pagamento.controller;

import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarStatusPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.CriarPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.IniciarPagamentoPlanoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoResponse;

import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.VerificarPagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.pagamento.service.PagamentoBulkService;
import com.minhaempresa.gendaz.pagamento.service.StripeWebhookService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pagamentos")
@RequiredArgsConstructor
@Slf4j
public class PagamentoController {
    private final PagamentoService pagamentoService;
    private final PagamentoBulkService pagamentoBulkService;
    private final StripeWebhookService stripeWebhookService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @PostMapping
    public ResponseEntity<PagamentoResponse> criar(@Valid @RequestBody CriarPagamentoRequest request, HttpServletRequest http) {
        validarEmpresaAutenticada(request.empresaId());
        return ResponseEntity.ok(pagamentoService.criar(request));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<PagamentoResponse>> listarPorEmpresa(@PathVariable Long empresaId, HttpServletRequest http) {
        validarEmpresaAutenticada(empresaId);
        return ResponseEntity.ok(pagamentoService.listarPorEmpresa(empresaId));
    }

    @PatchMapping("/{id}/marcar-pago")
    public ResponseEntity<PagamentoResponse> marcarPago(@PathVariable Long id, @Valid @RequestBody MarcarPagamentoPagoRequest request) {
        return ResponseEntity.ok(pagamentoService.marcarPago(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PagamentoResponse> atualizarStatus(@PathVariable Long id, @Valid @RequestBody AtualizarStatusPagamentoRequest request) {
        return ResponseEntity.ok(pagamentoService.atualizarStatus(id, request));
    }

    @PostMapping("/acoes-em-massa")
    public ResponseEntity<AcaoEmMassaResponse> acoesEmMassa(@Valid @RequestBody AcaoEmMassaPagamentoRequest request, HttpServletRequest http) {
        if (request.empresaId() != null) {
            validarEmpresaAutenticada(request.empresaId());
        }
        return ResponseEntity.ok(pagamentoBulkService.executar(request));
    }

    @GetMapping("/pendentes/contagem")
    public ResponseEntity<Map<String, Long>> contarPendentes(@RequestParam Long empresaId, HttpServletRequest http) {
        validarEmpresaAutenticada(empresaId);
        return ResponseEntity.ok(Map.of("count", pagamentoService.contarPendentes(empresaId)));
    }

    @PostMapping("/planos/pro/iniciar")
    public ResponseEntity<PagamentoPlanoResponse> iniciarPagamentoPro(@Valid @RequestBody IniciarPagamentoPlanoRequest request, HttpServletRequest http) {
        validarNaoAtendente();
        validarEmpresaAutenticada(request.empresaId());
        return ResponseEntity.ok(pagamentoService.iniciarPagamentoPlanoPro(request));
    }

    @PostMapping("/planos/basico/iniciar")
    public ResponseEntity<PagamentoPlanoResponse> iniciarPagamentoBasico(@Valid @RequestBody IniciarPagamentoPlanoRequest request, HttpServletRequest http) {
        validarNaoAtendente();
        validarEmpresaAutenticada(request.empresaId());
        return ResponseEntity.ok(pagamentoService.iniciarPagamentoPlano(
                request.empresaId(),
                "BASICO",
                request.metodoPagamento(),
                request.customerName(),
                request.customerEmail(),
                request.customerPhone(),
                request.antifraudProfilingAttemptReference(),
                request.forceNew() != null && request.forceNew()
        ));
    }


    @GetMapping("/planos/empresa/{empresaId}")
    public ResponseEntity<List<PagamentoPlanoResponse>> listarPagamentosPlano(@PathVariable Long empresaId, HttpServletRequest http) {
        validarEmpresaAutenticada(empresaId);
        return ResponseEntity.ok(pagamentoService.listarPagamentosPlano(empresaId));
    }

    @GetMapping("/planos/empresa/{empresaId}/{pagamentoId}")
    public ResponseEntity<PagamentoPlanoResponse> consultarPagamentoPlano(@PathVariable Long empresaId, @PathVariable Long pagamentoId, HttpServletRequest http) {
        validarEmpresaAutenticada(empresaId);
        return ResponseEntity.ok(pagamentoService.consultarPagamentoPlano(empresaId, pagamentoId));
    }

    @GetMapping("/planos/empresa/{empresaId}/{pagamentoId}/verificar")
    public ResponseEntity<VerificarPagamentoPlanoResponse> verificarPagamentoPlano(@PathVariable Long empresaId, @PathVariable Long pagamentoId, HttpServletRequest http) {
        validarEmpresaAutenticada(empresaId);
        return ResponseEntity.ok(pagamentoService.verificarPagamentoPlano(empresaId, pagamentoId));
    }

    @GetMapping("/planos/empresa/{empresaId}/atual")
    public ResponseEntity<AssinaturaResponse> consultarPlanoAtual(@PathVariable Long empresaId, HttpServletRequest http) {
        validarEmpresaAutenticada(empresaId);
        return ResponseEntity.ok(pagamentoService.consultarPlanoAtual(empresaId));
    }

    @PostMapping("/webhook/stripe")
    public ResponseEntity<Void> receberWebhookStripe(
            @RequestHeader(name = "Stripe-Signature", required = false) String assinatura,
            HttpServletRequest request) {
        try {
            String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            stripeWebhookService.processar(payload, assinatura);
            return ResponseEntity.ok().build();
        } catch (BusinessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nao foi possivel ler webhook Stripe.", ex);
        }
    }

    private void validarNaoAtendente() {
        PerfilUsuario perfil = usuarioAutenticadoProvider.exigirPerfil();
        if (perfil == PerfilUsuario.ATENDENTE) {
            throw new BusinessException("Seu perfil nao permite comprar ou editar planos.");
        }
    }

    private void validarEmpresaAutenticada(Long empresaId) {
        usuarioAutenticadoProvider.exigirEmpresa(empresaId);
    }

}

