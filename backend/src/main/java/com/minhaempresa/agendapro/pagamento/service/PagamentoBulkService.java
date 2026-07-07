package com.minhaempresa.agendapro.pagamento.service;

import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import com.minhaempresa.agendapro.pagamento.dto.PagamentoDtos.AcaoEmMassaPagamentoRequest;
import com.minhaempresa.agendapro.pagamento.dto.PagamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.agendapro.pagamento.dto.PagamentoDtos.FalhaAcaoItem;
import com.minhaempresa.agendapro.pagamento.entity.PagamentoEntity;
import com.minhaempresa.agendapro.pagamento.enums.StatusPagamento;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PagamentoBulkService {
    private final PagamentoRepository pagamentoRepository;

    @Transactional
    public AcaoEmMassaResponse executar(AcaoEmMassaPagamentoRequest request) {
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
                PagamentoEntity pagamento = pagamentoRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Pagamento nao encontrado."));
                if (!pagamento.getEmpresa().getId().equals(companyId)) {
                    throw new ResourceNotFoundException("Pagamento nao encontrado.");
                }
                switch (acao) {
                    case "MARCAR_COMO_PAGO" -> {
                        pagamento.setStatus(StatusPagamento.PAGO);
                        pagamento.setDataPagamento(LocalDateTime.now());
                    }
                    case "MARCAR_COMO_PENDENTE" -> {
                        pagamento.setStatus(StatusPagamento.PENDENTE);
                        pagamento.setDataPagamento(null);
                    }
                    case "EXCLUIR" -> {
                        pagamentoRepository.delete(pagamento);
                        processados++;
                        continue;
                    }
                    default -> throw new BusinessException("Acao de pagamento nao suportada.");
                }
                pagamentoRepository.save(pagamento);
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
