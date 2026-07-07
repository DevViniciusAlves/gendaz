package com.minhaempresa.agendapro.pagamento.mapper;

import com.minhaempresa.agendapro.pagamento.dto.PagamentoDtos.PagamentoResponse;
import com.minhaempresa.agendapro.pagamento.entity.PagamentoEntity;
import com.minhaempresa.agendapro.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.agendapro.pagamento.entity.PagamentoPlanoEntity;

public class PagamentoMapper {
    public PagamentoResponse toResponse(PagamentoEntity pagamento) {
        Long agendamentoId = pagamento.getAgendamento() == null ? null : pagamento.getAgendamento().getId();
        return new PagamentoResponse(
                pagamento.getId(),
                agendamentoId,
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
                pagamento.getCaktoRefId(),
                pagamento.getCustomerName(),
                pagamento.getCustomerEmail(),
                pagamento.getCustomerPhone(),
                pagamento.getCustomerDocType(),
                pagamento.getCustomerDocNumber(),
                pagamento.getAntifraudReference(),
                pagamento.getCheckoutUrl(),
                pagamento.getPixCopiaECola(),
                pagamento.getPixQrCodeBase64(),
                pagamento.getSubscriptionId(),
                pagamento.getDataCriacao(),
                pagamento.getDataExpiracao(),
                pagamento.getDataPagamento()
        );
    }
}
