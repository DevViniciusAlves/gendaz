package com.minhaempresa.gendaz.cliente.service;

import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.AcaoEmMassaClienteRequest;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.FalhaAcaoItem;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Processa acoes em massa sobre clientes (ATIVAR / DESATIVAR / EXCLUIR).
 *
 * Cada item do lote e tratado com propagacao de excecao isolada: um item
 * invalido nao desfaz os itens ja processados nem aborta os itens restantes.
 * As regras reais (soft-delete, anonimizacao, auditoria) sao delegadas ao
 * ClienteService, garantindo que bulk e individual usem a mesma rotina.
 */
@Service
@RequiredArgsConstructor
public class ClienteBulkService {
    private final ClienteService clienteService;

    public AcaoEmMassaResponse processar(AcaoEmMassaClienteRequest request) {
        validarQuantidade(request.ids());
        Long companyId = CompanyContext.requireCompanyId();
        if (request.empresaId() != null && !request.empresaId().equals(companyId)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }

        String acao = request.acao() == null ? "" : request.acao().trim().toUpperCase(Locale.ROOT);
        Long empresaId = companyId;

        Set<Long> idsUnicos = new LinkedHashSet<>(request.ids());
        List<FalhaAcaoItem> falhas = new ArrayList<>();
        int processados = 0;

        for (Long id : idsUnicos) {
            try {
                switch (acao) {
                    case "ATIVAR" -> clienteService.alterarStatus(id, empresaId, StatusCadastro.ATIVO);
                    case "DESATIVAR" -> clienteService.alterarStatus(id, empresaId, StatusCadastro.INATIVO);
                    case "EXCLUIR" -> clienteService.excluir(id, empresaId);
                    default -> throw new BusinessException("Acao em massa invalida: " + request.acao());
                }
                processados++;
            } catch (RuntimeException ex) {
                falhas.add(new FalhaAcaoItem(id, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
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
