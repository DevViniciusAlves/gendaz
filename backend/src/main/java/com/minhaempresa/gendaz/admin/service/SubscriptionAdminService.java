package com.minhaempresa.gendaz.admin.service;

import com.minhaempresa.gendaz.admin.dto.AdminAssinaturaDtos.AssinaturaAdminResponse;
import com.minhaempresa.gendaz.admin.dto.AdminAssinaturaDtos.CriarAssinaturaRequest;
import com.minhaempresa.gendaz.admin.dto.AdminAssinaturaDtos.EditarAssinaturaRequest;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionAdminService {

    private static final int LIMITE_PLANOS_ATIVOS = 2;
    private static final int DIAS_PADRAO = 30;

    private final AssinaturaRepository assinaturaRepository;
    private final EmpresaRepository empresaRepository;
    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final PlanoService planoService;

    @Transactional(readOnly = true)
    public List<AssinaturaAdminResponse> listarAssinaturas(Long empresaId) {
        List<AssinaturaEntity> todas = assinaturaRepository.findByEmpresaId(empresaId);
        todas.sort(Comparator.comparing(AssinaturaEntity::getDataInicio, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AssinaturaEntity::getId));

        boolean encontrouAtual = false;
        List<AssinaturaAdminResponse> resultado = new ArrayList<>();
        LocalDate hoje = LocalDate.now();

        for (AssinaturaEntity a : todas) {
            boolean vigente = a.getDataInicio() != null && !a.getDataInicio().isAfter(hoje)
                    && a.getDataFim() != null && a.getDataFim().isAfter(hoje)
                    && (a.getStatus() == StatusAssinatura.ATIVA || a.getStatus() == StatusAssinatura.TESTE);
            boolean isAtual = !encontrouAtual
                    && (vigente || a.getStatus() == StatusAssinatura.ATIVA || a.getStatus() == StatusAssinatura.TESTE);
            if (isAtual) {
                encontrouAtual = true;
            }
            long dias = a.getDataInicio() != null && a.getDataFim() != null
                    ? ChronoUnit.DAYS.between(a.getDataInicio(), a.getDataFim())
                    : 0;
            long diasRestantes = diasRestantes(a.getDataInicio(), a.getDataFim());

            resultado.add(new AssinaturaAdminResponse(
                    a.getId(),
                    a.getPlano().getNome(),
                    a.getPlano().getId(),
                    a.getStatus(),
                    a.getDataInicio(),
                    a.getDataFim(),
                    dias,
                    isAtual,
                    diasRestantes
            ));
        }

        return resultado;
    }

    private long diasRestantes(LocalDate dataInicio, LocalDate dataFim) {
        if (dataFim == null) return 0;
        LocalDate referencia = dataInicio != null && dataInicio.isAfter(LocalDate.now())
                ? dataInicio
                : LocalDate.now();
        return Math.max(0, ChronoUnit.DAYS.between(referencia, dataFim));
    }

    @Transactional
    public List<AssinaturaAdminResponse> editarAssinatura(Long empresaId, Long subscriptionId, EditarAssinaturaRequest request) {
        AssinaturaEntity assinatura = assinaturaRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Assinatura nao encontrada."));

        if (!assinatura.getEmpresa().getId().equals(empresaId)) {
            throw new BusinessException("Assinatura nao pertence a esta empresa.");
        }

        if (request.planoId() != null) {
            PlanoEntity plano = planoService.buscarEntidade(request.planoId());
            assinatura.setPlano(plano);
        }

        if (request.status() != null) {
            assinatura.setStatus(request.status());
        }

        LocalDate dataInicio = request.dataInicio() != null ? request.dataInicio() : assinatura.getDataInicio();
        assinatura.setDataInicio(dataInicio);

        if (request.dataFim() != null) {
            assinatura.setDataFim(request.dataFim());
        } else if (request.dias() != null) {
            if (request.dias() < 1) {
                throw new BusinessException("Dias minimos: 1.");
            }
            assinatura.setDataFim(dataInicio.plusDays(request.dias()));
        }

        assinaturaRepository.save(assinatura);

        List<AssinaturaEntity> fila = assinaturaRepository.findByEmpresaId(empresaId);
        fila.sort(Comparator.comparing(AssinaturaEntity::getDataInicio, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AssinaturaEntity::getId));

        recalcularFila(fila, subscriptionId);

        return listarAssinaturas(empresaId);
    }

    @Transactional
    public List<AssinaturaAdminResponse> criarAssinatura(Long empresaId, CriarAssinaturaRequest request) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
        PlanoEntity plano = planoService.buscarEntidade(request.planoId());

        if (contarFilaAtiva(empresaId) >= LIMITE_PLANOS_ATIVOS) {
            throw new BusinessException("Voce ja possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.");
        }

        LocalDate hoje = LocalDate.now();
        LocalDate dataInicio = request.dataInicio() != null ? request.dataInicio() : proximaDataInicio(empresaId, hoje);
        int dias = request.dias() != null ? request.dias() : DIAS_PADRAO;
        if (dias < 1) {
            throw new BusinessException("Dias minimos: 1.");
        }
        LocalDate dataFim = request.dataFim() != null ? request.dataFim() : dataInicio.plusDays(dias);
        StatusAssinatura status = request.status() != null ? request.status() : StatusAssinatura.ATIVA;

        AssinaturaEntity nova = AssinaturaEntity.builder()
                .empresa(empresa)
                .plano(plano)
                .status(status)
                .dataInicio(dataInicio)
                .dataFim(dataFim)
                .build();
        assinaturaRepository.save(nova);

        // Ao atribuir um plano válido e ativo, garantir que a empresa volte a ATIVA
        // Esta regra é consistente com processarExpiracaoEFila, que seta INATIVA
        // quando não há planos vigentes. O inverso também deve ser verdadeiro.
        if (empresa.getStatus() != StatusEmpresa.ATIVA) {
            empresa.setStatus(StatusEmpresa.ATIVA);
            empresaRepository.save(empresa);
        }

        List<AssinaturaEntity> fila = assinaturaRepository.findByEmpresaId(empresaId);
        fila.sort(Comparator.comparing(AssinaturaEntity::getDataInicio, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AssinaturaEntity::getId));
        recalcularFila(fila, nova.getId());

        return listarAssinaturas(empresaId);
    }

    /**
     * Remove um plano da conta do cliente. Pagamentos que apontavam para a
     * assinatura sao desvinculados antes da exclusao. Os planos restantes sao
     * reencadeados e, quando nenhum plano com vigencia resta, a conta fica
     * INATIVA.
     */
    @Transactional
    public List<AssinaturaAdminResponse> removerAssinatura(Long empresaId, Long subscriptionId) {
        AssinaturaEntity assinatura = assinaturaRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Assinatura nao encontrada."));

        if (!assinatura.getEmpresa().getId().equals(empresaId)) {
            throw new BusinessException("Assinatura nao pertence a esta empresa.");
        }

        List<PagamentoPlanoEntity> pagamentos = pagamentoPlanoRepository.findByAssinaturaId(subscriptionId);
        pagamentos.forEach(p -> p.setAssinatura(null));
        pagamentoPlanoRepository.saveAll(pagamentos);

        assinaturaRepository.delete(assinatura);

        reordenarFilaAposRemocao(empresaId, assinatura);

        return listarAssinaturas(empresaId);
    }

    /**
     * Reencadeia a fila de planos ativos (ATIVA ou TESTE) apos a remocao de um
     * plano. Se o plano removido estava em vigor, o proximo passa a valer hoje.
     * Planos que vinham depois do removido sao deslocados para cima. Quando nao
     * sobra nenhum plano com vigencia futura, a conta e marcada como INATIVA.
     */
    private void reordenarFilaAposRemocao(Long empresaId, AssinaturaEntity removida) {
        LocalDate hoje = LocalDate.now();

        List<AssinaturaEntity> ativas = assinaturaRepository.findByEmpresaId(empresaId).stream()
                .filter(a -> a.getStatus() == StatusAssinatura.ATIVA || a.getStatus() == StatusAssinatura.TESTE)
                .filter(a -> a.getDataFim() == null || a.getDataFim().isAfter(hoje))
                .sorted(Comparator.comparing(AssinaturaEntity::getDataInicio, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AssinaturaEntity::getId))
                .toList();

        if (ativas.isEmpty()) {
            empresaRepository.findById(empresaId)
                    .filter(e -> e.getStatus() != StatusEmpresa.INATIVA)
                    .ifPresent(e -> {
                        e.setStatus(StatusEmpresa.INATIVA);
                        empresaRepository.save(e);
                    });
            return;
        }

        boolean removidaEraAtual = removida.getDataInicio() != null && !removida.getDataInicio().isAfter(hoje);
        int idxInicio = 0;

        if (removidaEraAtual) {
            AssinaturaEntity primeira = ativas.get(0);
            long dias = diasDe(primeira);
            primeira.setDataInicio(hoje);
            primeira.setDataFim(hoje.plusDays(dias));
            assinaturaRepository.save(primeira);
            idxInicio = 1;
        } else {
            LocalDate referencia = removida.getDataInicio() != null ? removida.getDataInicio() : hoje;
            while (idxInicio < ativas.size()
                    && (ativas.get(idxInicio).getDataInicio() == null
                    || !ativas.get(idxInicio).getDataInicio().isAfter(referencia))) {
                idxInicio++;
            }
        }

        for (int i = idxInicio; i < ativas.size(); i++) {
            AssinaturaEntity anterior = ativas.get(i - 1);
            AssinaturaEntity atual = ativas.get(i);
            if (anterior.getDataFim() != null) {
                long dias = diasDe(atual);
                LocalDate inicio = anterior.getDataFim();
                atual.setDataInicio(inicio);
                atual.setDataFim(inicio.plusDays(dias));
                assinaturaRepository.save(atual);
            }
        }
    }

    private long diasDe(AssinaturaEntity a) {
        long dias = a.getDataInicio() != null && a.getDataFim() != null
                ? ChronoUnit.DAYS.between(a.getDataInicio(), a.getDataFim())
                : DIAS_PADRAO;
        return Math.max(dias, 1);
    }

    /**
     * Quantidade de planos na fila de vigencia (ATIVA ou TESTE, ainda nao vencidos).
     * Base do limite de 2 planos simultaneos.
     */
    private long contarFilaAtiva(Long empresaId) {
        LocalDate hoje = LocalDate.now();
        return assinaturaRepository.findByEmpresaId(empresaId).stream()
                .filter(a -> a.getStatus() == StatusAssinatura.ATIVA || a.getStatus() == StatusAssinatura.TESTE)
                .filter(a -> a.getDataFim() == null || a.getDataFim().isAfter(hoje))
                .count();
    }

    /**
     * Proxima data de inicio para um novo plano: dia seguinte ao fim do ultimo
     * plano ativo da fila (ou hoje quando nao ha fila).
     */
    private LocalDate proximaDataInicio(Long empresaId, LocalDate hoje) {
        List<AssinaturaEntity> fila = assinaturaRepository.findByEmpresaId(empresaId).stream()
                .filter(a -> a.getStatus() == StatusAssinatura.ATIVA || a.getStatus() == StatusAssinatura.TESTE)
                .filter(a -> a.getDataFim() == null || a.getDataFim().isAfter(hoje))
                .sorted(Comparator
                        .comparing(AssinaturaEntity::getDataInicio, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AssinaturaEntity::getId))
                .toList();
        if (fila.isEmpty()) {
            return hoje;
        }
        AssinaturaEntity ultima = fila.get(fila.size() - 1);
        LocalDate fim = ultima.getDataFim() == null ? hoje : ultima.getDataFim();
        return fim;
    }

    private void recalcularFila(List<AssinaturaEntity> fila, Long aPartirDeId) {
        int idxInicio = 0;
        for (int i = 0; i < fila.size(); i++) {
            if (fila.get(i).getId().equals(aPartirDeId)) {
                idxInicio = i;
                break;
            }
        }

        for (int i = idxInicio + 1; i < fila.size(); i++) {
            AssinaturaEntity anterior = fila.get(i - 1);
            AssinaturaEntity atual = fila.get(i);

            if (anterior.getDataFim() != null) {
                LocalDate novaDataInicio = anterior.getDataFim();
                long diasAtuais = atual.getDataInicio() != null && atual.getDataFim() != null
                        ? ChronoUnit.DAYS.between(atual.getDataInicio(), atual.getDataFim())
                        : 30;
                diasAtuais = Math.max(diasAtuais, 1);

                atual.setDataInicio(novaDataInicio);
                atual.setDataFim(novaDataInicio.plusDays(diasAtuais));
                assinaturaRepository.save(atual);
            }
        }
    }
}

