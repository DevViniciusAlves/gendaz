package com.minhaempresa.gendaz.pagamento.service;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarFormasPagamentoEmpresaRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.FormasPagamentoEmpresaResponse;
import com.minhaempresa.gendaz.pagamento.entity.FormaPagamentoEmpresaEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.repository.FormaPagamentoEmpresaRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FormaPagamentoEmpresaService {
    public static final int MAX_PARCELAS = 12;

    private final FormaPagamentoEmpresaRepository repository;
    private final EmpresaService empresaService;

    @Transactional(readOnly = true)
    public FormasPagamentoEmpresaResponse buscar(Long empresaId) {
        Long empresaResolvida = resolverEmpresaAtual(empresaId);
        return repository.findByEmpresaId(empresaResolvida)
                .map(this::toResponse)
                .orElseGet(() -> padraoResponse(empresaResolvida));
    }

    @Transactional
    public FormasPagamentoEmpresaResponse atualizar(Long empresaId, AtualizarFormasPagamentoEmpresaRequest request) {
        Long empresaResolvida = resolverEmpresaAtual(empresaId);
        EmpresaEntity empresa = empresaService.buscarEntidade(empresaResolvida);
        FormaPagamentoEmpresaEntity config = repository.findByEmpresaId(empresaResolvida)
                .orElseGet(() -> FormaPagamentoEmpresaEntity.builder().empresa(empresa).build());
        config.setPixAtivo(request.pixAtivo());
        config.setDebitoAtivo(request.debitoAtivo());
        config.setCreditoAtivo(request.creditoAtivo());
        config.setParceladoAtivo(request.creditoAtivo() && request.parceladoAtivo());
        config.setDinheiroAtivo(request.dinheiroAtivo());
        return toResponse(repository.save(config));
    }

    @Transactional(readOnly = true)
    public void validarPagamentoManual(Long empresaId, MetodoPagamento metodoPagamento, Integer parcelas) {
        if (metodoPagamento == null) {
            throw new BusinessException("Informe a forma de pagamento.");
        }
        FormasPagamentoEmpresaResponse config = buscar(empresaId);
        MetodoPagamento metodo = normalizarMetodoManual(metodoPagamento);
        int parcelasResolvidas = parcelas == null ? 1 : parcelas;
        if (parcelasResolvidas < 1 || parcelasResolvidas > MAX_PARCELAS) {
            throw new BusinessException("Quantidade de parcelas invalida.");
        }
        switch (metodo) {
            case PIX -> {
                if (!config.pixAtivo()) throw new BusinessException("Pix não esta habilitado para esta empresa.");
                validarSemParcelamento(parcelasResolvidas);
            }
            case DEBITO -> {
                if (!config.debitoAtivo()) throw new BusinessException("Debito não esta habilitado para esta empresa.");
                validarSemParcelamento(parcelasResolvidas);
            }
            case CREDITO -> {
                if (!config.creditoAtivo()) throw new BusinessException("Credito não esta habilitado para esta empresa.");
                if (parcelasResolvidas > 1 && !config.parceladoAtivo()) {
                    throw new BusinessException("Parcelamento não esta habilitado para esta empresa.");
                }
            }
            case DINHEIRO -> {
                if (!config.dinheiroAtivo()) throw new BusinessException("Dinheiro não esta habilitado para esta empresa.");
                validarSemParcelamento(parcelasResolvidas);
            }
            default -> throw new BusinessException("Forma de pagamento manual invalida.");
        }
    }

    public MetodoPagamento normalizarMetodoManual(MetodoPagamento metodoPagamento) {
        if (metodoPagamento == MetodoPagamento.CARTAO || metodoPagamento == MetodoPagamento.CREDIT_CARD) {
            return MetodoPagamento.CREDITO;
        }
        return metodoPagamento;
    }

    public Integer normalizarParcelas(MetodoPagamento metodoPagamento, Integer parcelas) {
        MetodoPagamento metodo = normalizarMetodoManual(metodoPagamento);
        if (metodo != MetodoPagamento.CREDITO) {
            return null;
        }
        return parcelas == null ? 1 : parcelas;
    }

    private void validarSemParcelamento(int parcelas) {
        if (parcelas > 1) {
            throw new BusinessException("Esta forma de pagamento não aceita parcelamento.");
        }
    }

    private FormasPagamentoEmpresaResponse toResponse(FormaPagamentoEmpresaEntity config) {
        return new FormasPagamentoEmpresaResponse(
                config.getEmpresa().getId(),
                config.isPixAtivo(),
                config.isDebitoAtivo(),
                config.isCreditoAtivo(),
                config.isParceladoAtivo(),
                config.isDinheiroAtivo(),
                MAX_PARCELAS
        );
    }

    private FormasPagamentoEmpresaResponse padraoResponse(Long empresaId) {
        return new FormasPagamentoEmpresaResponse(empresaId, true, true, true, false, true, MAX_PARCELAS);
    }

    private Long resolverEmpresaAtual(Long empresaId) {
        Long empresaContexto = CompanyContext.requireCompanyId();
        if (empresaId != null && !empresaContexto.equals(empresaId)) {
            throw new BusinessException("Empresa da sessão não corresponde ao recurso solicitado.");
        }
        return empresaContexto;
    }
}
