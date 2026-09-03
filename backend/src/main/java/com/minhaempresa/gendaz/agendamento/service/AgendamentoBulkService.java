package com.minhaempresa.gendaz.agendamento.service;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.FalhaAcaoItem;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
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
 * {@code agendamento.setStatus(...)}, nenhum
 * {@code agendamentoRepository.save/delete(...)} e nenhuma leitura de
 * {@code PagamentoEntity} para decidir regra financeira aqui. Em especial, o
 * bulk NUNCA calcula {@code jaPago} fora de transacao (TOCTOU): ele apenas
 * ordena "finalize preservando o estado financeiro atual" e a decisao
 * PAGO/PENDENTE acontece dentro do
 * {@link AgendamentoService#finalizarPreservandoPagamento}, depois dos locks
 * Agendamento -&gt; Pagamento. Todo dominio pertence ao
 * {@link AgendamentoService} (maquina de estados + locks
 * Agendamento -&gt; Pagamento -&gt; Empresa).
 *
 * <p>Auditoria: o service de dominio ja registra a acao de cada item
 * (uma acao de dominio = um log de dominio); o bulk nao registra novamente.
 */
@Service
@RequiredArgsConstructor
public class AgendamentoBulkService {
    private final AgendamentoService agendamentoService;

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
                // Sem informacao financeira explicita, preserva o estado
                // financeiro ATUAL (decidido sob lock dentro do service).
                // Para receber dinheiro em massa, informe
                // pagamentoRealizado/metodoPagamento ou use o bulk de
                // pagamentos (MARCAR_COMO_PAGO).
                if (request.pagamentoRealizado() == null
                        && request.metodoPagamento() == null
                        && request.parcelas() == null) {
                    agendamentoService.finalizarPreservandoPagamento(id, companyId);
                } else {
                    agendamentoService.finalizar(
                            id, request.pagamentoRealizado(), request.metodoPagamento(), request.parcelas());
                }
            }
            case "CANCELAR" -> {
                // Regra central de cancelamento (estados + pagamento
                // pendente preservando PAGO). Estados terminais viram
                // falha do item, sem ressuscitar nem destruir nada.
                agendamentoService.cancelar(id, companyId);
            }
            case "EXCLUIR" -> {
                // Mesma regra da exclusao individual: nunca destroi historico.
                agendamentoService.excluir(id, companyId);
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
}
