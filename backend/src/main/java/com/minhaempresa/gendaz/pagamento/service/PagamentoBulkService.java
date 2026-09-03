package com.minhaempresa.gendaz.pagamento.service;

import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.financeiro.caixadespesas.service.CaixaDespesasService;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.FalhaAcaoItem;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
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
    private final FormaPagamentoEmpresaService formaPagamentoEmpresaService;
    private final CaixaDespesasService caixaDespesasService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @Transactional
    public AcaoEmMassaResponse executar(AcaoEmMassaPagamentoRequest request) {
        validarQuantidade(request.ids());
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new BusinessException("Empresa logada nao encontrada.");
        }
        if (request.empresaId() != null && !request.empresaId().equals(companyId)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }
        String acao = request.acao() == null ? "" : request.acao().trim().toUpperCase();
        Set<Long> idsUnicos = new HashSet<>(request.ids());
        List<FalhaAcaoItem> falhas = new ArrayList<>();
        int processados = 0;
        for (Long id : idsUnicos) {
            try {
                PagamentoEntity pagamento = pagamentoRepository.findByIdAndEmpresaIdForUpdate(id, companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Pagamento nao encontrado."));
                StatusPagamento statusAnterior = pagamento.getStatus();
                // EXCLUIR nunca apaga historico financeiro: pagamento confirmado
                // (PAGO) e bloqueado e vira falha do item, com orientacao para o
                // fluxo explicito de cancelamento/estorno. Pendente vira CANCELADO
                // (cancelamento logico, sem movimentar Caixa porque nada foi
                // registrado). Todo caminho passa pelo save + regras de Caixa abaixo.
                switch (acao) {
                    case "MARCAR_COMO_PAGO" -> {
                        formaPagamentoEmpresaService.validarPagamentoManual(companyId, request.metodoPagamento(), request.parcelas());
                        var metodo = formaPagamentoEmpresaService.normalizarMetodoManual(request.metodoPagamento());
                        pagamento.setStatus(StatusPagamento.PAGO);
                        pagamento.setMetodoPagamento(metodo);
                        pagamento.setParcelas(formaPagamentoEmpresaService.normalizarParcelas(metodo, request.parcelas()));
                        pagamento.setDataPagamento(LocalDateTime.now());
                    }
                    case "MARCAR_COMO_PENDENTE" -> {
                        pagamento.setStatus(StatusPagamento.PENDENTE);
                        pagamento.setMetodoPagamento(null);
                        pagamento.setParcelas(null);
                        pagamento.setDataPagamento(null);
                    }
                    case "EXCLUIR" -> {
                        if (statusAnterior == StatusPagamento.PAGO) {
                            throw new BusinessException("Pagamento confirmado nao pode ser excluido. Utilize o cancelamento/estorno explicito do pagamento.");
                        }
                        if (statusAnterior == StatusPagamento.PENDENTE || statusAnterior == StatusPagamento.PAYMENT_PENDING) {
                            pagamento.setStatus(StatusPagamento.CANCELADO);
                        }
                    }
                    default -> throw new BusinessException("Acao de pagamento nao suportada.");
                }
                pagamentoRepository.save(pagamento);
                if (acao.equals("MARCAR_COMO_PAGO") && statusAnterior != StatusPagamento.PAGO) {
                    caixaDespesasService.registrarPagamentoAprovado(pagamento);
                } else if (acao.equals("MARCAR_COMO_PENDENTE") && statusAnterior == StatusPagamento.PAGO) {
                    caixaDespesasService.registrarPagamentoRemovido(pagamento, usuarioAutenticadoProvider.exigirUsuarioId());
                }
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

