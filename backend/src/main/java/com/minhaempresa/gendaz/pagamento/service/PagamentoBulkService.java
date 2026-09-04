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
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestrador de operacoes em massa sobre pagamentos.
 *
 * <p>Regra obrigatoria CANCELADO (atomica, inclusive sob concorrencia): este
 * metodo roda em transacao UNICA ({@code @Transactional}). Para
 * {@code MARCAR_COMO_PAGO} e {@code MARCAR_COMO_PENDENTE}, TODOS os IDs sao
 * carregados com lock PESSIMISTIC_WRITE em ordem crescente ANTES do primeiro
 * update ({@link #carregarLoteComLock}) e os locks sao mantidos ate o
 * commit/rollback — um cancelamento concorrente de item do lote espera, nunca
 * atravessa o meio do processamento. Se existir PELO MENOS UM pagamento
 * CANCELADO (antes ou durante o lote), a operacao inteira falha com
 * {@code BusinessException} e ZERO pagamentos sao modificados — nunca
 * {@code [PAGO, PAGO, falha]}. O {@link PagamentoService} continua sendo a
 * fonte principal da regra (segunda barreira por item, no momento do write).
 *
 * <p>Para os demais casos (ex.: ID inexistente/de outra empresa), a semantica
 * e SUCESSO PARCIAL por item (resposta com {@code processados} +
 * {@code falhas}): nada foi escrito antes desses throws, entao seguir e
 * seguro dentro da mesma transacao.
 *
 * <p>EXCLUIR nunca tem trava previa aqui: delega direto a regra central para
 * preservar a idempotencia de {@code excluirPagamento(CANCELADO)}.
 */
@Service
@RequiredArgsConstructor
public class PagamentoBulkService {
    private final PagamentoService pagamentoService;
    private final PagamentoRepository pagamentoRepository;

    /**
     * Transacao UNICA do lote: os locks PESSIMISTIC_WRITE adquiridos na
     * pre-validacao sao mantidos ate o commit/rollback, de modo que um
     * cancelamento concorrente de item do lote espera em vez de atravessar o
     * meio do processamento. CANCELADO (antes ou durante o lote) propaga como
     * {@code BusinessException}: rollback total, ZERO alteracoes.
     */
    @Transactional
    public AcaoEmMassaResponse executar(AcaoEmMassaPagamentoRequest request) {
        validarQuantidade(request.ids());
        Long companyId = CompanyContext.requireCompanyId();
        if (request.empresaId() != null && !request.empresaId().equals(companyId)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }
        String acao = request.acao() == null ? "" : request.acao().trim().toUpperCase();
        if (!"MARCAR_COMO_PAGO".equals(acao) && !"MARCAR_COMO_PENDENTE".equals(acao) && !"EXCLUIR".equals(acao)) {
            throw new BusinessException("Acao de pagamento nao suportada.");
        }
        // Ordem deterministica crescente: evita deadlock nos locks (PAGAMENTO -> EMPRESA).
        List<Long> idsOrdenados = request.ids().stream().sorted().toList();
        // Fase 1 — preload com lock: TODOS os IDs com FOR UPDATE ANTES do
        // primeiro update; locks mantidos ate o commit/rollback desta transacao.
        // Para MARCAR_COMO_PAGO / MARCAR_COMO_PENDENTE, se existir PELO MENOS
        // UM pagamento CANCELADO, falha aqui com ZERO alteracoes. IDs
        // inexistentes/de outra empresa ficam de fora e viram falha por item.
        java.util.Map<Long, PagamentoEntity> lote = carregarLoteComLock(idsOrdenados, acao, companyId);
        // Fase 2 — aplica a regra central (mesmo nucleo do individual) item a
        // item, sem fronteira transacional intermediaria: sem envenenamento de
        // rollback-only e sem janela para cancelamento atravessar o lote.
        List<FalhaAcaoItem> falhas = new ArrayList<>();
        int processados = 0;
        for (Long id : idsOrdenados) {
            PagamentoEntity pagamento = lote.get(id);
            if (pagamento == null) {
                falhas.add(new FalhaAcaoItem(id, "Pagamento nao encontrado."));
                continue;
            }
            try {
                executarItem(pagamento, acao, request);
                processados++;
            } catch (BusinessException | ResourceNotFoundException | ConflictException ex) {
                // CANCELADO residual durante o lote: aborta tudo com rollback.
                if (isCanceladoError(ex) && ("MARCAR_COMO_PAGO".equals(acao) || "MARCAR_COMO_PENDENTE".equals(acao))) {
                    throw new BusinessException("Pagamento cancelado não pode ser alterado.");
                }
                // Erro ESPERADO de negocio (forma de pagamento invalida, PAGO no
                // EXCLUIR): vira falha do item e o bulk continua. Nenhum desses
                // throws ocorre apos escrita (checam antes de mutar), entao
                // seguir e seguro dentro desta transacao.
                // Erro SISTEMICO inesperado (infra/conexao/banco) NAO e capturado:
                // propaga para nao fingir que o bulk funcionou normalmente.
                falhas.add(new FalhaAcaoItem(id, ex.getMessage()));
            }
        }
        return new AcaoEmMassaResponse(request.ids().size(), processados, falhas);
    }

    /**
     * Aplica o nucleo de dominio do {@link PagamentoService} (mesmo codigo do
     * endpoint individual) na entidade ja lockada desta transacao. Sem chamada
     * a metodo {@code @Transactional} intermediario: nenhuma fronteira que
     * marque rollback-only e nenhuma janela para concorrente atravessar.
     */
    private void executarItem(PagamentoEntity pagamento, String acao, AcaoEmMassaPagamentoRequest request) {
        switch (acao) {
            case "MARCAR_COMO_PAGO" -> pagamentoService.aplicarMarcarPago(
                    pagamento, new MarcarPagamentoPagoRequest(request.metodoPagamento(), request.parcelas()));
            case "MARCAR_COMO_PENDENTE" -> pagamentoService.aplicarAtualizarStatus(
                    pagamento, new AtualizarStatusPagamentoRequest(StatusPagamento.PENDENTE));
            case "EXCLUIR" -> {
                // EXCLUIR preserva idempotencia de CANCELADO: delega direto a regra
                // central, sem trava previa aqui.
                pagamentoService.aplicarExclusao(pagamento);
            }
            default -> throw new BusinessException("Acao de pagamento nao suportada.");
        }
    }

    private java.util.Map<Long, PagamentoEntity> carregarLoteComLock(List<Long> ids, String acao, Long companyId) {
        java.util.Map<Long, PagamentoEntity> lote = new java.util.LinkedHashMap<>();
        for (Long id : ids) {
            // Lock PESSIMISTIC_WRITE segurado ate o fim da transacao do lote:
            // concorrente que tente cancelar um item do lote espera, nunca
            // atravessa o meio do processamento.
            Optional<PagamentoEntity> opt = pagamentoRepository.findByIdAndEmpresaIdForUpdate(id, companyId);
            if (opt.isEmpty()) {
                // Item inexistente/de outra empresa: vira falha individual na fase 2.
                continue;
            }
            PagamentoEntity pagamento = opt.get();
            if (("MARCAR_COMO_PAGO".equals(acao) || "MARCAR_COMO_PENDENTE".equals(acao))) {
                validarPagamentoNaoCancelado(pagamento);
            }
            lote.put(id, pagamento);
        }
        return lote;
    }

    private void validarQuantidade(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("Selecione pelo menos um item.");
        }
        if (ids.size() > 10) {
            throw new BusinessException("Você pode selecionar no máximo 10 itens por vez.");
        }
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException("Identificador de pagamento inválido.");
        }
    }

    private void validarPagamentoNaoCancelado(PagamentoEntity pagamento) {
        if (pagamento != null && pagamento.getStatus() == StatusPagamento.CANCELADO) {
            throw new BusinessException("Pagamento cancelado não pode ser alterado.");
        }
    }

    private boolean isCanceladoError(RuntimeException ex) {
        String msg = ex == null || ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(java.util.Locale.ROOT);
        return msg.contains("cancelado");
    }
}
