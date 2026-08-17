package com.minhaempresa.gendaz.assinatura.service;

import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.mapper.AssinaturaMapper;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssinaturaService {
    private static final int MESES_POR_PERIODO = 1;
    private static final int LIMITE_PLANOS_ATIVOS = 2;

    private final AssinaturaRepository assinaturaRepository;
    private final AssinaturaMapper mapper = new AssinaturaMapper();

    @Transactional(readOnly = true)
    public List<AssinaturaResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
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

    /**
     * Fila de planos com vigencia futura (nao vencidos): assinaturas ATIVA ou
     * TESTE cujo dataFim ainda nao passou de hoje. E a base do limite de 2
     * planos ativos e do encadeamento em sequencia.
     */
    public List<AssinaturaEntity> buscarFilaAtiva(Long empresaId) {
        LocalDate hoje = LocalDate.now();
        return assinaturaRepository.findByEmpresaId(empresaId).stream()
                .filter(a -> a.getStatus() == StatusAssinatura.ATIVA
                        || a.getStatus() == StatusAssinatura.TESTE)
                .filter(a -> a.getDataFim() == null || a.getDataFim().isAfter(hoje))
                .sorted(Comparator
                        .comparing(AssinaturaEntity::getDataInicio, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AssinaturaEntity::getId))
                .toList();
    }

    /**
     * Assinatura em vigor agora: a da fila cujo periodo (dataInicio..dataFim)
     * contem a data de hoje. Quando o plano atual vence e o proximo da fila
     * comeca, o retorno muda automaticamente (e com ele os acessos do painel).
     */
    @Transactional
    public Optional<AssinaturaEntity> buscarAtualPorEmpresa(Long empresaId) {
        LocalDate hoje = LocalDate.now();
        processarExpiracaoEFila(empresaId, hoje);

        List<AssinaturaEntity> fila = buscarFilaAtiva(empresaId);
        Optional<AssinaturaEntity> vigente = fila.stream()
                .filter(a -> a.getDataInicio() != null && !a.getDataInicio().isAfter(hoje)
                        && a.getDataFim() != null && a.getDataFim().isAfter(hoje))
                .findFirst();
        if (vigente.isPresent()) {
            return vigente;
        }
        // Fallback para dados legados sem dataFim
        return fila.stream()
                .filter(a -> a.getDataFim() == null)
                .findFirst();
    }

    @Transactional
    public AssinaturaResponse buscarAtualResponsePorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return buscarAtualPorEmpresa(empresaId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Assinatura nao encontrada."));
    }

    /**
     * Ativa uma assinatura paga e a encadeia na fila de planos (sem cancelar a
     * atual). Se a assinatura vinculada/pendente for do mesmo plano, ativa ela;
     * senao cria uma nova assinatura ATIVA que passa a valer somente depois que
     * o ultimo plano da fila terminar.
     */
    @Transactional
    public AssinaturaEntity ativarPlanoPago(EmpresaEntity empresa, PlanoEntity plano) {
        return ativarPlanoPago(empresa, plano, null);
    }

    @Transactional
    public AssinaturaEntity ativarPlanoPago(EmpresaEntity empresa, PlanoEntity plano, AssinaturaEntity assinaturaVinculada) {
        LocalDate hoje = LocalDate.now();
        List<AssinaturaEntity> fila = buscarFilaAtiva(empresa.getId());

        long contagemPlanosAdicionais = fila.stream()
                .filter(a -> assinaturaVinculada == null || !a.getId().equals(assinaturaVinculada.getId()))
                .count();

        if (contagemPlanosAdicionais >= LIMITE_PLANOS_ATIVOS) {
            throw new BusinessException("Voce ja possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.");
        }

        // 1) Renovacao: a assinatura vinculada ao pagamento ja existe e e do mesmo plano
        if (assinaturaVinculada != null && assinaturaVinculada.getPlano().getId().equals(plano.getId())) {
            assinaturaVinculada.setStatus(StatusAssinatura.ATIVA);
            // Idempotencia: se ja esta em vigor (nao venceu), mantem onde esta.
            if (assinaturaVinculada.getDataFim() != null && assinaturaVinculada.getDataFim().isAfter(hoje)) {
                return assinaturaRepository.save(assinaturaVinculada);
            }
            encadearNaFila(empresa.getId(), assinaturaVinculada, hoje, assinaturaVinculada.getId());
            return assinaturaRepository.save(assinaturaVinculada);
        }

        // 2) Existe assinatura PENDENTE_PAGAMENTO do mesmo plano aguardando aprovacao
        Optional<AssinaturaEntity> pendente = assinaturaRepository.findByEmpresaId(empresa.getId()).stream()
                .filter(a -> a.getStatus() == StatusAssinatura.PENDENTE_PAGAMENTO
                        && a.getPlano().getId().equals(plano.getId()))
                .findFirst();
        if (pendente.isPresent()) {
            AssinaturaEntity alvo = pendente.get();
            alvo.setStatus(StatusAssinatura.ATIVA);
            encadearNaFila(empresa.getId(), alvo, hoje, alvo.getId());
            return assinaturaRepository.save(alvo);
        }

        // 3) Comprou durante o teste gratuito: o teste continua vigente e o plano pago entra na fila.
        // A regra que cancelava o teste foi removida para preservar os dias restantes.

        // 4) Padrao: nova assinatura ATIVA encadeada apos o ultimo plano da fila
        AssinaturaEntity nova = AssinaturaEntity.builder()
                .empresa(empresa)
                .plano(plano)
                .status(StatusAssinatura.ATIVA)
                .build();
        encadearNaFila(empresa.getId(), nova, hoje, null);
        return assinaturaRepository.save(nova);
    }

    public AssinaturaResponse toResponse(AssinaturaEntity assinatura) {
        return mapper.toResponse(assinatura);
    }

    /**
     * Reencadeia as assinaturas futuras a partir de uma assinatura alterada
     * (usado por ajustes manuais do admin): cada proxima comeca no dia seguinte
     * ao termino da anterior, preservando a duracao de cada uma.
     */
    @Transactional
    public void reposicionarFuturas(Long empresaId, Long aPartirDeId) {
        List<AssinaturaEntity> fila = buscarFilaAtiva(empresaId);
        int idx = -1;
        for (int i = 0; i < fila.size(); i++) {
            if (fila.get(i).getId().equals(aPartirDeId)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return;
        }
        for (int i = idx + 1; i < fila.size(); i++) {
            AssinaturaEntity anterior = fila.get(i - 1);
            AssinaturaEntity atual = fila.get(i);
            if (anterior.getDataFim() == null) {
                continue;
            }
            LocalDate novaInicio = anterior.getDataFim();
            long dias = atual.getDataInicio() != null && atual.getDataFim() != null
                    ? ChronoUnit.DAYS.between(atual.getDataInicio(), atual.getDataFim())
                    : 30;
            dias = Math.max(dias, 1);
            atual.setDataInicio(novaInicio);
            atual.setDataFim(novaInicio.plusDays(dias));
            assinaturaRepository.save(atual);
        }
    }

    /**
     * Marca como EXPIRADA as assinaturas que ja venceram, ativa a proxima da
     * fila quando ela ja comecou e ajusta o status da empresa (INATIVA quando
     * nao ha nenhum plano com vigencia futura).
     */
    private void processarExpiracaoEFila(Long empresaId, LocalDate hoje) {
        List<AssinaturaEntity> todas = assinaturaRepository.findByEmpresaId(empresaId);

        for (AssinaturaEntity a : todas) {
            boolean venceu = (a.getStatus() == StatusAssinatura.ATIVA
                    || a.getStatus() == StatusAssinatura.TESTE)
                    && a.getDataFim() != null
                    && !a.getDataFim().isAfter(hoje);
            if (venceu) {
                a.setStatus(StatusAssinatura.EXPIRADA);
                assinaturaRepository.save(a);
            }
        }

        // Removido: transição PENDENTE_PAGAMENTO → ATIVA baseada apenas na data é incorreta.
        // Assinatura paga só pode ser ativada por pagamento aprovado ou ação administrativa explícita.

        List<AssinaturaEntity> fila = buscarFilaAtiva(empresaId);
        if (fila.isEmpty()) {
            todas.stream()
                    .map(AssinaturaEntity::getEmpresa)
                    .filter(empresa -> empresa != null && empresa.getStatus() == StatusEmpresa.ATIVA)
                    .findFirst()
                    .ifPresent(empresa -> {
                        empresa.setStatus(StatusEmpresa.INATIVA);
                        assinaturaRepository.flush();
                    });
        }
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.requireCompanyId();
        if (empresaId == null || !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Assinatura nao encontrada.");
        }
    }

    private void encadearNaFila(Long empresaId, AssinaturaEntity alvo, LocalDate hoje, Long ignorarId) {
        List<AssinaturaEntity> fila = buscarFilaAtiva(empresaId);
        if (ignorarId != null) {
            fila = fila.stream().filter(a -> !a.getId().equals(ignorarId)).toList();
        }
        LocalDate dataInicio;
        if (fila.isEmpty()) {
            dataInicio = hoje;
        } else {
            AssinaturaEntity ultima = fila.get(fila.size() - 1);
            LocalDate fim = ultima.getDataFim() == null ? hoje : ultima.getDataFim();
            dataInicio = fim;
        }
        alvo.setDataInicio(dataInicio);
        alvo.setDataFim(dataInicio.plusMonths(MESES_POR_PERIODO));
    }
}

