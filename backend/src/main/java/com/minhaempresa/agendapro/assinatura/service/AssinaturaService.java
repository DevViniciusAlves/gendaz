package com.minhaempresa.agendapro.assinatura.service;

import com.minhaempresa.agendapro.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.agendapro.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.agendapro.assinatura.enums.StatusAssinatura;
import com.minhaempresa.agendapro.assinatura.mapper.AssinaturaMapper;
import com.minhaempresa.agendapro.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.plano.entity.PlanoEntity;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssinaturaService {
    private final AssinaturaRepository assinaturaRepository;
    private final AssinaturaMapper mapper = new AssinaturaMapper();

    @Transactional(readOnly = true)
    public List<AssinaturaResponse> listarPorEmpresa(Long empresaId) {
        return assinaturaRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public AssinaturaEntity criarTesteGratis(EmpresaEntity empresa, PlanoEntity plano) {
        LocalDate hoje = LocalDate.now();
        AssinaturaEntity assinatura = AssinaturaEntity.builder()
                .empresa(empresa)
                .plano(plano)
                .status(StatusAssinatura.TESTE)
                .dataInicio(hoje)
                .dataFim(hoje.plusDays(7))
                .dataInicioTeste(hoje)
                .dataFimTeste(hoje.plusDays(7))
                .build();
        return assinaturaRepository.save(assinatura);
    }

    @Transactional
    public AssinaturaEntity criarPendentePagamento(EmpresaEntity empresa, PlanoEntity plano) {
        LocalDate hoje = LocalDate.now();
        AssinaturaEntity assinatura = AssinaturaEntity.builder()
                .empresa(empresa)
                .plano(plano)
                .status(StatusAssinatura.PENDENTE_PAGAMENTO)
                .dataInicio(hoje)
                .build();
        return assinaturaRepository.save(assinatura);
    }

    @Transactional
    public Optional<AssinaturaEntity> buscarAtualPorEmpresa(Long empresaId) {
        Optional<AssinaturaEntity> assinatura = assinaturaRepository.findByEmpresaId(empresaId).stream()
                .max(Comparator
                        .comparingInt(this::prioridadeStatus)
                        .thenComparing(AssinaturaEntity::getDataInicio, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AssinaturaEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        assinatura.ifPresent(this::expirarTesteSeNecessario);
        return assinatura;
    }

    @Transactional
    public AssinaturaResponse buscarAtualResponsePorEmpresa(Long empresaId) {
        return buscarAtualPorEmpresa(empresaId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Assinatura nao encontrada."));
    }

    @Transactional
    public AssinaturaEntity ativarPlanoPago(EmpresaEntity empresa, PlanoEntity plano) {
        LocalDate hoje = LocalDate.now();
        assinaturaRepository.findByEmpresaId(empresa.getId()).forEach(assinatura -> {
            if (assinatura.getStatus() == StatusAssinatura.ATIVA || assinatura.getStatus() == StatusAssinatura.TESTE) {
                assinatura.setStatus(StatusAssinatura.CANCELADA);
                assinatura.setDataFim(hoje);
                assinaturaRepository.save(assinatura);
            }
        });

        Optional<AssinaturaEntity> pendente = assinaturaRepository.findByEmpresaId(empresa.getId()).stream()
                .filter(assinatura -> assinatura.getStatus() == StatusAssinatura.PENDENTE_PAGAMENTO
                        && assinatura.getPlano().getId().equals(plano.getId()))
                .findFirst();
        if (pendente.isPresent()) {
            AssinaturaEntity assinatura = pendente.get();
            assinatura.setStatus(StatusAssinatura.ATIVA);
            assinatura.setDataInicio(hoje);
            assinatura.setDataFim(hoje.plusMonths(1));
            return assinaturaRepository.save(assinatura);
        }

        AssinaturaEntity assinatura = AssinaturaEntity.builder()
                .empresa(empresa)
                .plano(plano)
                .status(StatusAssinatura.ATIVA)
                .dataInicio(hoje)
                .dataFim(hoje.plusMonths(1))
                .build();
        return assinaturaRepository.save(assinatura);
    }

    public AssinaturaResponse toResponse(AssinaturaEntity assinatura) {
        return mapper.toResponse(assinatura);
    }

    private void expirarTesteSeNecessario(AssinaturaEntity assinatura) {
        if (assinatura.getStatus() == StatusAssinatura.TESTE
                && assinatura.getDataFimTeste() != null
                && assinatura.getDataFimTeste().isBefore(LocalDate.now())) {
            assinatura.setStatus(StatusAssinatura.EXPIRADA);
            if (assinatura.getEmpresa() != null) {
                assinatura.getEmpresa().setStatus(com.minhaempresa.agendapro.empresa.enums.StatusEmpresa.INATIVA);
            }
            assinaturaRepository.save(assinatura);
        }
    }

    private int prioridadeStatus(AssinaturaEntity assinatura) {
        if (assinatura == null || assinatura.getStatus() == null) {
            return -1;
        }
        return switch (assinatura.getStatus()) {
            case ATIVA, TESTE -> 4;
            case PENDENTE_PAGAMENTO -> 3;
            case EXPIRADA -> 2;
            case CANCELADA -> 1;
        };
    }
}
