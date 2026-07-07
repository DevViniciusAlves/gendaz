package com.minhaempresa.agendapro.agendamento.service;

import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.FalhaAcaoItem;
import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.whatsapp.repository.WhatsappLembretePagamentoRepository;
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
    private final WhatsappLembretePagamentoRepository lembretePagamentoRepository;

    @Transactional
    public AcaoEmMassaResponse executar(AcaoEmMassaAgendamentoRequest request) {
        validarQuantidade(request.ids());
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            companyId = request.empresaId();
        }
        if (companyId == null) {
            throw new BusinessException("Empresa logada nao encontrada.");
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
                    case "CANCELAR" -> agendamento.setStatus(StatusAgendamento.CANCELADO);
                    case "PENDENTE" -> agendamento.setStatus(StatusAgendamento.PENDENTE);
                    case "EXCLUIR" -> {
                        lembretePagamentoRepository.deleteByAgendamento_Id(id);
                        pagamentoRepository.deleteByAgendamentoId(id);
                        agendamentoRepository.delete(agendamento);
                        processados++;
                        continue;
                    }
                    case "DESATIVAR" -> throw new BusinessException("Desativar nao e uma acao suportada para agendamentos.");
                    default -> throw new BusinessException("Acao de agendamento nao suportada.");
                }
                agendamentoRepository.save(agendamento);
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
}
