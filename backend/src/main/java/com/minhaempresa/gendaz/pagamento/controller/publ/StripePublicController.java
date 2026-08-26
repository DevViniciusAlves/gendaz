package com.minhaempresa.gendaz.pagamento.controller.publ;

import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.StripeCheckoutStatusRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.VerificarPagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/pagamentos/stripe")
@RequiredArgsConstructor
public class StripePublicController {
    private final PagamentoService pagamentoService;

    @PostMapping("/checkout/status")
    public VerificarPagamentoPlanoResponse verificarStatus(@RequestBody StripeCheckoutStatusRequest request) {
        return pagamentoService.verificarStatusPagamentoPublico(request.sessionId());
    }
}
