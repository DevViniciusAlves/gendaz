package com.minhaempresa.gendaz.agendamento.service;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.FalhaAcaoItem;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgendamentoBulkService {
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PagamentoService pagamentoService;
    private final AgendamentoService agendamentoService;
    private final LogAtividadeService logAtividadeService;

    @Transactional
    public AcaoEmMassaResponse executar(AcaoEmMassaAgendamentoRequest request) {
        validarQuantidade(request.ids());
        Long companyId = CompanyContext.requireCompanyId();
        if (request.empresaId() != null && !request.empresaId().equals(companyId)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }
        String acao = request.acao() == null ? "" : request.acao().trim().toUpperCase();
        Set<Long> idsUnicos = new HashSet<>(request.ids());
        List<FalhaAcaoItem> falhas = new ArrayList<>();
        int processados = 0;
        for (Long id : idsUnicos) {
            try {
                AgendamentoEntity agendamento = agendamentoRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Agendamento nao encontrado."));
                if (!agendamento.getEmpresa().getId().equals(companyId)) {
                    throw new ResourceNotFoundException("Agendamento nao encontrado.");
                }
                switch (acao) {
                    case "FINALIZAR" -> {
                        // Fonte unica de verdade: reutiliza AgendamentoService.finalizar,
                        // com as mesmas regras financeiras do fluxo individual
                        // (Caixa uma vez, bloqueio de re-finalizacao, sem PAGO->PENDENTE).
                        // Sem parametros de pagamento explicitos, o bulk nunca inventa
                        // recebimento: preserva PAGO ja confirmado, caso contrario
                        // finaliza sem pagamento (PENDENTE). Para receber dinheiro em
                        // massa, informe pagamentoRealizado/metodoPagamento ou use o
                        // bulk de pagamentos (MARCAR_COMO_PAGO).
                        Boolean pago = request.pagamentoRealizado();
                        var metodo = request.metodoPagamento();
                        var parcelas = request.parcelas();
                        if (pago == null) {
                            boolean jaPago = pagamentoRepository.findByAgendamentoIdAndEmpresaId(id, companyId)
                                    .map(p -> p.getStatus() == StatusPagamento.PAGO)
                                    .orElse(false);
                            pago = jaPago;
                            if (jaPago) {
                                PagamentoEntity existente = pagamentoRepository.findByAgendamentoIdAndEmpresaId(id, companyId).orElse(null);
                                if (existente != null) {
                                    metodo = existente.getMetodoPagamento();
                                    parcelas = existente.getParcelas();
                                }
                            }
                        }
                        agendamentoService.finalizar(id, pago, metodo, parcelas);
                        logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), verboAcao(acao) + " agendamento " + agendamento.getId());
                        processados++;
                        continue;
                    }
                    case "CANCELAR" -> {
                        agendamento.setStatus(StatusAgendamento.CANCELADO);
                        pagamentoService.cancelarPagamentoPendenteDoAgendamento(id, companyId);
                    }
                    case "PENDENTE" -> agendamento.setStatus(StatusAgendamento.PENDENTE);
                    case "EXCLUIR" -> {
                        // Mesma regra da exclusao individual: nunca destroi historico.
                        agendamentoService.excluir(id, companyId);
                        logAtividadeService.registrar("AGENDAMENTO", id, "Removeu agendamento " + id);
                        processados++;
                        continue;
                    }
                    case "DESATIVAR" -> throw new BusinessException("Desativar nao e uma acao suportada para agendamentos.");
                    default -> throw new BusinessException("Acao de agendamento nao suportada.");
                }
                agendamentoRepository.save(agendamento);
                logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), verboAcao(acao) + " agendamento " + agendamento.getId());
                processados++;
            } catch (RuntimeException ex) {
                falhas.add(new FalhaAcaoItem(id, ex.getMessage()));
            }
        }
        return new AcaoEmMassaResponse(request.ids().size(), processados, falhas);
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
            case "PENDENTE" -> "Marcou como pendente";
            default -> "Alterou";
        };
    }
}

