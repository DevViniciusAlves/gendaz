package com.minhaempresa.gendaz.pagamento.mapper;

import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoResponse;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;

public class PagamentoMapper {
    public PagamentoResponse toResponse(PagamentoEntity pagamento) {
        Long agendamentoId = pagamento.getAgendamento() == null ? null : pagamento.getAgendamento().getId();
        String protocolo = pagamento.getAgendamento() == null ? null : pagamento.getAgendamento().getProtocolo();
        String servicoNome = pagamento.getAgendamento() == null || pagamento.getAgendamento().getServico() == null
                ? null
                : pagamento.getAgendamento().getServico().getNome();
        return new PagamentoResponse(
                pagamento.getId(),
                agendamentoId,
                protocolo,
                servicoNome,
                pagamento.getCliente().getId(),
                pagamento.getCliente().getNome(),
                pagamento.getEmpresa().getId(),
                pagamento.getValor(),
                pagamento.getMetodoPagamento(),
                pagamento.getStatus(),
                pagamento.getDataPagamento()
        );
    }

    public PagamentoPlanoResponse toPlanoResponse(PagamentoPlanoEntity pagamento) {
        return new PagamentoPlanoResponse(
                pagamento.getId(),
                pagamento.getEmpresa().getId(),
                pagamento.getEmpresa().getNomeFantasia(),
                pagamento.getPlano().getId(),
                pagamento.getPlano().getNome(),
                pagamento.getValor(),
                pagamento.getMetodoPagamento(),
                pagamento.getStatus(),
                pagamento.getProvider(),
                pagamento.getProviderPaymentId(),
                pagamento.getExternalReference(),
                pagamento.getPaymentReference(),
                pagamento.getCustomerName(),
                pagamento.getCustomerEmail(),
                pagamento.getCustomerPhone(),
                pagamento.getCustomerDocType(),
                pagamento.getCustomerDocNumber(),
                pagamento.getAntifraudReference(),
                pagamento.getCheckoutUrl(),
                pagamento.getSubscriptionId(),
                pagamento.getStripeSessionId(),
                pagamento.getStripeCustomerId(),
                pagamento.getDataCriacao(),
                pagamento.getDataExpiracao(),
                pagamento.getDataPagamento()
        );
    }
}

