package com.minhaempresa.gendaz.pagamento.controller;

import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarStatusPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.CriarPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.IniciarPagamentoPlanoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.VerificarPagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.WebhookPagamentoPlanoRequest;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayProperties;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.pagamento.service.PagamentoBulkService;
import com.minhaempresa.gendaz.auth.service.AuthService;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CookieHelper;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pagamentos")
@RequiredArgsConstructor
@Slf4j
public class PagamentoController {
    private final PagamentoService pagamentoService;
    private final PagamentoBulkService pagamentoBulkService;
    private final PaymentGatewayProperties paymentGatewayProperties;
    private final AuthService authService;

    @PostMapping
    public ResponseEntity<PagamentoResponse> criar(@Valid @RequestBody CriarPagamentoRequest request, HttpServletRequest http) {
        validarEmpresaAutenticada(http, request.empresaId());
        return ResponseEntity.ok(pagamentoService.criar(request));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<PagamentoResponse>> listarPorEmpresa(@PathVariable Long empresaId, HttpServletRequest http) {
        validarEmpresaAutenticada(http, empresaId);
        return ResponseEntity.ok(pagamentoService.listarPorEmpresa(empresaId));
    }

    @PatchMapping("/{id}/marcar-pago")
    public ResponseEntity<PagamentoResponse> marcarPago(@PathVariable Long id) {
        return ResponseEntity.ok(pagamentoService.marcarPago(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PagamentoResponse> atualizarStatus(@PathVariable Long id, @Valid @RequestBody AtualizarStatusPagamentoRequest request) {
        return ResponseEntity.ok(pagamentoService.atualizarStatus(id, request));
    }

    @PostMapping("/acoes-em-massa")
    public ResponseEntity<AcaoEmMassaResponse> acoesEmMassa(@Valid @RequestBody AcaoEmMassaPagamentoRequest request, HttpServletRequest http) {
        if (request.empresaId() != null) {
            validarEmpresaAutenticada(http, request.empresaId());
        }
        return ResponseEntity.ok(pagamentoBulkService.executar(request));
    }

    @GetMapping("/pendentes/contagem")
    public ResponseEntity<Map<String, Long>> contarPendentes(@RequestParam Long empresaId, HttpServletRequest http) {
        validarEmpresaAutenticada(http, empresaId);
        return ResponseEntity.ok(Map.of("count", pagamentoService.contarPendentes(empresaId)));
    }

    @PostMapping("/planos/pro/iniciar")
    public ResponseEntity<PagamentoPlanoResponse> iniciarPagamentoPro(@Valid @RequestBody IniciarPagamentoPlanoRequest request, HttpServletRequest http) {
        validarNaoAtendente(http);
        validarEmpresaAutenticada(http, request.empresaId());
        return ResponseEntity.ok(pagamentoService.iniciarPagamentoPlanoPro(request));
    }

    @GetMapping("/planos/empresa/{empresaId}")
    public ResponseEntity<List<PagamentoPlanoResponse>> listarPagamentosPlano(@PathVariable Long empresaId, HttpServletRequest http) {
        validarEmpresaAutenticada(http, empresaId);
        return ResponseEntity.ok(pagamentoService.listarPagamentosPlano(empresaId));
    }

    @GetMapping("/planos/empresa/{empresaId}/{pagamentoId}")
    public ResponseEntity<PagamentoPlanoResponse> consultarPagamentoPlano(@PathVariable Long empresaId, @PathVariable Long pagamentoId, HttpServletRequest http) {
        validarEmpresaAutenticada(http, empresaId);
        return ResponseEntity.ok(pagamentoService.consultarPagamentoPlano(empresaId, pagamentoId));
    }

    @GetMapping("/planos/empresa/{empresaId}/{pagamentoId}/verificar")
    public ResponseEntity<VerificarPagamentoPlanoResponse> verificarPagamentoPlano(@PathVariable Long empresaId, @PathVariable Long pagamentoId, HttpServletRequest http) {
        validarEmpresaAutenticada(http, empresaId);
        return ResponseEntity.ok(pagamentoService.verificarPagamentoPlano(empresaId, pagamentoId));
    }

    @GetMapping("/planos/empresa/{empresaId}/atual")
    public ResponseEntity<AssinaturaResponse> consultarPlanoAtual(@PathVariable Long empresaId, HttpServletRequest http) {
        validarEmpresaAutenticada(http, empresaId);
        return ResponseEntity.ok(pagamentoService.consultarPlanoAtual(empresaId));
    }

    @PostMapping("/webhook")
    public ResponseEntity<PagamentoPlanoResponse> receberWebhook(
            @RequestHeader(name = "X-Payment-Signature", required = false) String assinatura,
            @Valid @RequestBody WebhookPagamentoPlanoRequest request) {
        return ResponseEntity.ok(pagamentoService.processarWebhookPlano(request, assinatura));
    }

    @PostMapping("/planos/webhook")
    public ResponseEntity<PagamentoPlanoResponse> receberWebhookMercadoPago(
            @RequestHeader(name = "x-signature", required = false) String assinatura,
            @RequestHeader(name = "x-cakto-signature", required = false) String assinaturaCakto,
            @RequestHeader(name = "x-webhook-secret", required = false) String webhookSecret,
            @RequestHeader(name = "authorization", required = false) String authorization,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam Map<String, String> queryParams,
            @RequestBody(required = false) Map<String, Object> body) {
        if (usarWebhookCakto(body)) {
            String assinaturaFinal = primeiraNaoVazia(assinaturaCakto, webhookSecret, authorization);
            return ResponseEntity.ok(pagamentoService.processarWebhookCakto(body, assinaturaFinal));
        }
        String providerPaymentId = extrairPaymentId(queryParams, body);
        return ResponseEntity.ok(pagamentoService.processarWebhookMercadoPago(providerPaymentId, assinatura, requestId));
    }

    @PostMapping("/planos/webhook/cakto")
    public ResponseEntity<PagamentoPlanoResponse> receberWebhookCakto(
            @RequestHeader(name = "x-cakto-signature", required = false) String assinaturaCakto,
            @RequestHeader(name = "x-webhook-secret", required = false) String webhookSecret,
            @RequestHeader(name = "authorization", required = false) String authorization,
            @RequestParam Map<String, String> queryParams,
            @RequestBody(required = false) Map<String, Object> body) {
        log.info("Webhook Cakto recebido");
        if (body == null || body.isEmpty()) {
            throw new com.minhaempresa.gendaz.shared.BusinessException("Webhook da Cakto sem payload valido.");
        }
        String assinatura = primeiraNaoVazia(assinaturaCakto, webhookSecret, authorization);
        return ResponseEntity.ok(pagamentoService.processarWebhookCakto(body, assinatura));
    }

    @SuppressWarnings("unchecked")
    private String extrairPaymentId(Map<String, String> queryParams, Map<String, Object> body) {
        String dataId = queryParams.get("data.id");
        if (dataId == null || dataId.isBlank()) dataId = queryParams.get("data_id");
        if ((dataId == null || dataId.isBlank()) && body != null) {
            Object data = body.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Object id = ((Map<String, Object>) dataMap).get("id");
                if (id != null) dataId = String.valueOf(id);
            }
        }
        if ((dataId == null || dataId.isBlank()) && body != null && body.get("id") != null) {
            dataId = String.valueOf(body.get("id"));
        }
        return dataId;
    }

    private String primeiraNaoVazia(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) return valor;
        }
        return null;
    }

    private boolean usarWebhookCakto(Map<String, Object> body) {
        if ("CAKTO".equalsIgnoreCase(paymentGatewayProperties.getProvider())) {
            return true;
        }
        if (body == null || body.isEmpty()) {
            return false;
        }
        return body.containsKey("payment_reference")
                || body.containsKey("paymentReference")
                || body.containsKey("product_id")
                || body.containsKey("productId")
                || body.containsKey("sale_status")
                || body.containsKey("saleStatus");
    }

    private void validarNaoAtendente(HttpServletRequest http) {
        String sessao = CookieHelper.lerCookie(http, "Gendaz_session").orElse(null);
        PerfilUsuario perfil = authService.buscarUsuarioAutenticado(null, sessao).getPerfil();
        if (perfil == PerfilUsuario.ATENDENTE) {
            throw new BusinessException("Seu perfil nao permite comprar ou editar planos.");
        }
    }

    private void validarEmpresaAutenticada(HttpServletRequest http, Long empresaId) {
        UsuarioEntity usuario = authService.buscarUsuarioAutenticado(null, CookieHelper.lerCookie(http, "Gendaz_session").orElse(null));
        if (usuario.getEmpresa() == null || !usuario.getEmpresa().getId().equals(empresaId)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }
    }

    private Long extrairUsuarioId(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

