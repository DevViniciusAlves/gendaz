package com.minhaempresa.gendaz.agendamento.service;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.FalhaAcaoItem;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orquestrador de operacoes em massa sobre agendamentos.
 *
 * <p>FRONTEIRA TRANSACIONAL (semantica oficial: falha por item / sucesso
 * parcial): este metodo NAO possui {@code @Transactional}. Cada item delega a
 * um metodo publico transacional do {@link AgendamentoService} (bean
 * diferente, via proxy Spring), de modo que cada item tenha sua propria
 * transacao fisica:
 *
 * <pre>
 * Bulk sem transacao global
 *         v
 * Item 1 -> transacao propria -> commit
 *         v
 * Item 2 -> transacao propria -> rollback (BusinessException)
 *         v
 * Item 3 -> transacao propria -> commit
 * </pre>
 *
 * <p>Com a propagacao padrao (REQUIRED) e SEM transacao externa, cada chamada
 * abre uma transacao nova e commita/rollbacka de forma independente. Uma
 * {@code BusinessException} do item 2 rollbacka SOMENTE o item 2: nunca marca
 * os itens 1/3 como rollback-only e nunca causa
 * {@code UnexpectedRollbackException} no commit global (que nao existe mais).
 *
 * <p>Este service NAO manipula estado diretamente: nenhum
 * {@code agendamento.setStatus(...)} nem
 * {@code agendamentoRepository.save/delete(...)} aqui. Todo dominio pertence
 * ao {@link AgendamentoService} (maquina de estados + locks
 * Agendamento -&gt; Pagamento -&gt; Empresa).
 */
@Service
@RequiredArgsConstructor
public class AgendamentoBulkService {
    private final PagamentoRepository pagamentoRepository;
    private final AgendamentoService agendamentoService;
    private final LogAtividadeService logAtividadeService;

    public AcaoEmMassaResponse executar(AcaoEmMassaAgendamentoRequest request) {
        validarQuantidade(request.ids());
        Long companyId = CompanyContext.requireCompanyId();
        if (request.empresaId() != null && !request.empresaId().equals(companyId)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }
        String acao = request.acao() == null ? "" : request.acao().trim().toUpperCase();
        if (acao.equals("PENDENTE")) {
            // Acao descontinuada: reset generico de status ressuscita estados
            // terminais (FINALIZADO/CANCELADO) e furava a maquina de estados.
            // Use as acoes especificas (cancelar, finalizar, reabrir).
            throw new BusinessException("Acao em massa PENDENTE descontinuada. Utilize cancelar, finalizar ou reabrir conforme o caso.");
        }
        Set<Long> idsUnicos = new HashSet<>(request.ids());
        List<FalhaAcaoItem> falhas = new ArrayList<>();
        int processados = 0;
        for (Long id : idsUnicos) {
            try {
                executarItem(id, acao, request, companyId);
                processados++;
            } catch (BusinessException | ResourceNotFoundException | ConflictException ex) {
                // Erro ESPERADO de negocio (status invalido, agendamento
                // finalizado, item inexistente/ de outra empresa, acao
                // incompativel): vira falha do item e o bulk continua.
                // Erro SISTEMICO inesperado (infra/conexao/banco) NAO e
                // capturado aqui: propaga para nao fingir que o bulk
                // funcionou normalmente.
                falhas.add(new FalhaAcaoItem(id, ex.getMessage()));
            }
        }
        return new AcaoEmMassaResponse(request.ids().size(), processados, falhas);
    }

    /**
     * Delega cada item a operacao central do {@link AgendamentoService}
     * (transacao propria por item, via proxy Spring). Bulk orquestra; o
     * service aplica dominio, maquina de estados e locks.
     */
    private void executarItem(Long id, String acao, AcaoEmMassaAgendamentoRequest request, Long companyId) {
        switch (acao) {
            case "FINALIZAR" -> {
                // Fonte unica de verdade: AgendamentoService.finalizar, com as
                // mesmas regras financeiras do fluxo individual (Caixa uma vez,
                // bloqueio de re-finalizacao, sem PAGO->PENDENTE). Sem
                // parametros de pagamento explicitos, o bulk nunca inventa
                // recebimento: preserva PAGO ja confirmado, caso contrario
                // finaliza sem pagamento (PENDENTE). Para receber dinheiro em
                // massa, informe pagamentoRealizado/metodoPagamento ou use o
                // bulk de pagamentos (MARCAR_COMO_PAGO).
                // Leitura read-only apenas para montar os argumentos; a decisao
                // de dominio (com lock Pagamento) acontece dentro do finalizar.
                Boolean pago = request.pagamentoRealizado();
                var metodo = request.metodoPagamento();
                var parcelas = request.parcelas();
                if (pago == null) {
                    PagamentoEntity existente = pagamentoRepository
                            .findByAgendamentoIdAndEmpresaId(id, companyId).orElse(null);
                    boolean jaPago = existente != null && existente.getStatus() == StatusPagamento.PAGO;
                    pago = jaPago;
                    if (jaPago) {
                        metodo = existente.getMetodoPagamento();
                        parcelas = existente.getParcelas();
                    }
                }
                agendamentoService.finalizar(id, pago, metodo, parcelas);
                logAtividadeService.registrar("AGENDAMENTO", id, verboAcao(acao) + " agendamento " + id);
            }
            case "CANCELAR" -> {
                // Regra central de cancelamento (estados + pagamento
                // pendente preservando PAGO). Estados terminais viram
                // falha do item, sem ressuscitar nem destruir nada.
                agendamentoService.cancelar(id, companyId);
                logAtividadeService.registrar("AGENDAMENTO", id, verboAcao(acao) + " agendamento " + id);
            }
            case "EXCLUIR" -> {
                // Mesma regra da exclusao individual: nunca destroi historico.
                agendamentoService.excluir(id, companyId);
                logAtividadeService.registrar("AGENDAMENTO", id, "Removeu agendamento " + id);
            }
            case "DESATIVAR" -> throw new BusinessException("Desativar nao e uma acao suportada para agendamentos.");
            default -> throw new BusinessException("Acao de agendamento nao suportada.");
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

    private String verboAcao(String acao) {
        return switch (acao) {
            case "FINALIZAR" -> "Finalizou";
            case "CANCELAR" -> "Cancelou";
            default -> "Alterou";
        };
    }
}
