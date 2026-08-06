package com.minhaempresa.agendapro.admin.service;

import com.minhaempresa.agendapro.admin.dto.AdminAssinaturaDtos.AssinaturaAdminResponse;
import com.minhaempresa.agendapro.admin.dto.AdminAssinaturaDtos.EditarAssinaturaRequest;
import com.minhaempresa.agendapro.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.agendapro.assinatura.enums.StatusAssinatura;
import com.minhaempresa.agendapro.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.agendapro.plano.entity.PlanoEntity;
import com.minhaempresa.agendapro.plano.service.PlanoService;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
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

    private final AssinaturaRepository assinaturaRepository;
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
                    && a.getDataFim() != null && !a.getDataFim().isBefore(hoje)
                    && (a.getStatus() == StatusAssinatura.ATIVA || a.getStatus() == StatusAssinatura.TESTE);
            boolean isAtual = !encontrouAtual
                    && (vigente || a.getStatus() == StatusAssinatura.ATIVA || a.getStatus() == StatusAssinatura.TESTE);
            if (isAtual) {
                encontrouAtual = true;
            }
            long dias = a.getDataInicio() != null && a.getDataFim() != null
                    ? ChronoUnit.DAYS.between(a.getDataInicio(), a.getDataFim())
                    : 0;
            long diasRestantes = a.getDataFim() != null
                    ? Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), a.getDataFim()))
                    : 0;

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
                LocalDate novaDataInicio = anterior.getDataFim().plusDays(1);
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
