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
 * <p>Regra obrigatoria CANCELADO: para {@code MARCAR_COMO_PAGO} e
 * {@code MARCAR_COMO_PENDENTE}, TODOS os IDs sao carregados/validados ANTES
 * do primeiro update ({@link #validarLoteSemCancelado}), em ordem crescente
 * de ID. Se existir PELO MENOS UM pagamento CANCELADO ja presente, a operacao
 * inteira falha com {@code BusinessException} e ZERO pagamentos sao
 * modificados — nunca {@code [PAGO, PAGO, falha]}. O {@link PagamentoService}
 * continua sendo a fonte principal da regra (segunda barreira por item, com
 * lock PESSIMISTIC_WRITE no momento do write). Limite honesto sob
 * concorrencia: transacoes sao por item (exigencia de PB-01..03 para IDs
 * inexistentes); se um item virar CANCELADO exatamente na janela entre a
 * pre-validacao e o seu processamento, aquele item falha sem virar PAGO e sem
 * tocar o Caixa (consistencia provada em PB-08: final sempre PAGO+caixa ou
 * CANCELADO+zero, nunca hibrido).
 *
 * <p>Para os demais casos (ex.: ID inexistente/de outra empresa), a semantica
 * e SUCESSO PARCIAL por item (resposta com {@code processados} +
 * {@code falhas}). Por isso este metodo NAO possui {@code @Transactional}:
 * cada item delega a um metodo publico transacional do {@link PagamentoService}.
 *
 * <p>EXCLUIR nunca tem trava previa aqui: delega direto a regra central para
 * preservar a idempotencia de {@code excluirPagamento(CANCELADO)}.
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
        // Ordem deterministica: evita deadlock nos locks por item (PAGAMENTO -> EMPRESA).
        List<Long> idsOrdenados = request.ids().stream().sorted().toList();
        // Pre-validacao atomica: carrega/valida TODOS os IDs ANTES do primeiro update.
        // Para MARCAR_COMO_PAGO / MARCAR_COMO_PENDENTE, se existir PELO MENOS UM
        // pagamento CANCELADO ja presente, falha a operacao inteira com ZERO alteracoes.
        // O PagamentoService continua sendo a fonte principal da regra de negocio
        // (segunda barreira por item, com lock PESSIMISTIC_WRITE no momento do write:
        // mesmo que um item vire CANCELADO na janela entre a pre-validacao e o seu
        // processamento, aquele item falha e nunca vira PAGO nem toca o Caixa).
        validarLoteSemCancelado(idsOrdenados, acao);
        List<FalhaAcaoItem> falhas = new ArrayList<>();
        int processados = 0;
        for (Long id : idsOrdenados) {
            try {
                executarItem(id, acao, request);
                processados++;
            } catch (BusinessException | ResourceNotFoundException | ConflictException ex) {
                // Erro ESPERADO de negocio (item inexistente/de outra empresa,
                // forma de pagamento invalida, ou item que virou CANCELADO na
                // janela entre a pre-validacao e o seu processamento): vira
                // falha do item e o bulk continua. O item cancelado em si nunca
                // e convertido (barreira por item no PagamentoService, com lock).
                // Erro SISTEMICO inesperado (infra/conexao/banco) NAO e capturado:
                // propaga para nao fingir que o bulk funcionou normalmente.
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
                pagamentoService.marcarPago(
                        id, new MarcarPagamentoPagoRequest(request.metodoPagamento(), request.parcelas()));
            }
            case "MARCAR_COMO_PENDENTE" -> {
                pagamentoService.atualizarStatus(
                        id, new AtualizarStatusPagamentoRequest(StatusPagamento.PENDENTE));
            }
            case "EXCLUIR" -> {
                // EXCLUIR preserva idempotencia de CANCELADO: delega direto a regra
                // central (PagamentoService.excluirPagamento), sem trava previa aqui.
                pagamentoService.excluirPagamento(id);
            }
            default -> throw new BusinessException("Acao de pagamento nao suportada.");
        }
    }

    private void validarLoteSemCancelado(List<Long> ids, String acao) {
        if (!"MARCAR_COMO_PAGO".equals(acao) && !"MARCAR_COMO_PENDENTE".equals(acao)) {
            return;
        }
        for (Long id : ids) {
            PagamentoEntity pagamento;
            try {
                pagamento = pagamentoService.buscarEntidade(id);
            } catch (BusinessException | ResourceNotFoundException ex) {
                // Item inexistente/de outra empresa: deixa o loop por item
                // transformar em falha individual; nao falha o lote inteiro.
                continue;
            }
            if (pagamento == null) {
                continue;
            }
            validarPagamentoNaoCancelado(pagamento);
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

    private void validarPagamentoNaoCancelado(PagamentoEntity pagamento) {
        if (pagamento != null && pagamento.getStatus() == StatusPagamento.CANCELADO) {
            throw new BusinessException("Pagamento cancelado não pode ser alterado.");
        }
    }

    private PagamentoEntity buscarEntidade(Long id) {
        return pagamentoService.buscarEntidade(id);
    }
}
