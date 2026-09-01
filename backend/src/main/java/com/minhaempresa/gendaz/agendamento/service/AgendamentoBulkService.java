package com.minhaempresa.gendaz.agendamento.service;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.FalhaAcaoItem;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
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
                    case "FINALIZAR" -> agendamento.setStatus(StatusAgendamento.FINALIZADO);
                    case "CANCELAR" -> {
                        agendamento.setStatus(StatusAgendamento.CANCELADO);
                        pagamentoService.cancelarPagamentoPendenteDoAgendamento(id, companyId);
                    }
                    case "PENDENTE" -> agendamento.setStatus(StatusAgendamento.PENDENTE);
                    case "EXCLUIR" -> {
                        pagamentoRepository.deleteByAgendamentoIdAndEmpresaId(id, companyId);
                        agendamentoRepository.delete(agendamento);
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

