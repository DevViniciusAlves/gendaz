package com.minhaempresa.gendaz.pagamento.service;

import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarStatusPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.FalhaAcaoItem;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orquestrador de operacoes em massa sobre pagamentos.
 *
 * <p>Semantica real do produto: SUCESSO PARCIAL (resposta com
 * {@code processados} + {@code falhas} por item). Por isso este metodo NAO
 * possui {@code @Transactional}: cada item delega a um metodo publico
 * transacional do {@link PagamentoService} (bean diferente, via proxy
 * Spring), de modo que cada item tenha sua propria transacao fisica. Uma
 * {@code BusinessException} no item 2 rollbacka SOMENTE o item 2 — nunca
 * marca os demais como rollback-only (sem {@code UnexpectedRollbackException}
 * nem rollback total silencioso).
 *
 * <p>Este service NAO implementa regra financeira: nenhum
 * {@code pagamento.setStatus(...)}, nenhum save/delete e nenhuma leitura de
 * status para decidir write posterior aqui. Toda decisao acontece dentro do
 * {@link PagamentoService}, depois do lock PESSIMISTIC_WRITE do pagamento
 * (ordem: PAGAMENTO -&gt; EMPRESA quando ha Caixa).
 */
@Service
@RequiredArgsConstructor
public class PagamentoBulkService {
    private final PagamentoService pagamentoService;

    public AcaoEmMassaResponse executar(AcaoEmMassaPagamentoRequest request) {
        validarQuantidade(request.ids());
        Long companyId = CompanyContext.requireCompanyId();
        if (request.empresaId() != null && !request.empresaId().equals(companyId)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }
        String acao = request.acao() == null ? "" : request.acao().trim().toUpperCase();
        List<FalhaAcaoItem> falhas = new ArrayList<>();
        int processados = 0;
        for (Long id : request.ids()) {
            try {
                executarItem(id, acao, request);
                processados++;
            } catch (BusinessException | ResourceNotFoundException | ConflictException ex) {
                // Erro ESPERADO de negocio (item inexistente/de outra empresa,
                // status incompativel, forma de pagamento invalida): vira falha
                // do item e o bulk continua. Erro SISTEMICO inesperado
                // (infra/conexao/banco) NAO e capturado: propaga para nao
                // fingir que o bulk funcionou normalmente.
                falhas.add(new FalhaAcaoItem(id, ex.getMessage()));
            }
        }
        return new AcaoEmMassaResponse(request.ids().size(), processados, falhas);
    }

    /**
     * Delega cada item a operacao central do {@link PagamentoService}
     * (transacao propria por item, via proxy Spring). Bulk orquestra; o
     * service aplica dominio, locks e Caixa.
     */
private void executarItem(Long id, String acao, AcaoEmMassaPagamentoRequest request) {
        switch (acao) {
            case "MARCAR_COMO_PAGO" -> {
                validarPagamentoNaoCancelado(id);
                pagamentoService.marcarPago(
                        id, new MarcarPagamentoPagoRequest(request.metodoPagamento(), request.parcelas()));
            }
            case "MARCAR_COMO_PENDENTE" -> {
                validarPagamentoNaoCancelado(id);
                pagamentoService.atualizarStatus(
                        id, new AtualizarStatusPagamentoRequest(StatusPagamento.PENDENTE));
            }
            case "EXCLUIR" -> {
                validarPagamentoNaoCancelado(id);
                pagamentoService.excluirPagamento(id);
            }
            default -> throw new BusinessException("Acao de pagamento nao suportada.");
        }
    }

    private void validarQuantidade(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("Selecione pelo menos um item.");
        }
        if (ids.size() > 10) {
            throw new BusinessException("Você pode selecionar no máximo 10 itens por vez.");
        }
    }

    private void validarPagamentoNaoCancelado(Long id) {
        PagamentoEntity pagamento = pagamentoService.buscarEntidade(id);
        if (pagamento.getStatus() == StatusPagamento.CANCELADO) {
            throw new BusinessException("Pagamento cancelado não pode ser alterado.");
        }
    }

    private void validarPagamentoNaoCancelado(PagamentoEntity pagamento) {
        if (pagamento.getStatus() == StatusPagamento.CANCELADO) {
            throw new BusinessException("Pagamento cancelado não pode ser alterado.");
        }
    }

    private PagamentoEntity buscarEntidade(Long id) {
        return pagamentoService.buscarEntidade(id);
    }
}
