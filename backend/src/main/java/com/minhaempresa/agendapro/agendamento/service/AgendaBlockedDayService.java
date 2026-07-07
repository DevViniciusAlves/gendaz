package com.minhaempresa.agendapro.agendamento.service;

import com.minhaempresa.agendapro.agendamento.dto.AgendaBlockedDayDtos.BloquearDiaRequest;
import com.minhaempresa.agendapro.agendamento.dto.AgendaBlockedDayDtos.DiaBloqueadoResponse;
import com.minhaempresa.agendapro.agendamento.entity.AgendaBlockedDayEntity;
import com.minhaempresa.agendapro.agendamento.mapper.AgendaBlockedDayMapper;
import com.minhaempresa.agendapro.agendamento.repository.AgendaBlockedDayRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.service.EmpresaService;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;
import com.minhaempresa.agendapro.profissional.service.ProfissionalService;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgendaBlockedDayService {
    private final AgendaBlockedDayRepository repository;
    private final EmpresaService empresaService;
    private final ProfissionalService profissionalService;
    private final AgendaBlockedDayMapper mapper = new AgendaBlockedDayMapper();

    @Transactional(readOnly = true)
    public List<DiaBloqueadoResponse> listar(Long empresaId) {
        return repository.findByEmpresaIdOrderByDataAsc(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public DiaBloqueadoResponse bloquear(BloquearDiaRequest request) {
        if (request.data().isBefore(LocalDate.now())) {
            throw new BusinessException("Nao e possivel bloquear uma data passada.");
        }

        EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
        ProfissionalEntity profissional = null;
        if (request.profissionalId() != null) {
            profissional = profissionalService.buscarEntidade(request.profissionalId());
            if (!profissional.getEmpresa().getId().equals(empresa.getId())) {
                throw new BusinessException("Profissional nao pertence a empresa informada.");
            }
        }

        if (diaBloqueado(empresa.getId(), profissional == null ? null : profissional.getId(), request.data())) {
            throw new BusinessException("Este dia ja esta bloqueado.");
        }

        AgendaBlockedDayEntity entity = AgendaBlockedDayEntity.builder()
                .empresa(empresa)
                .profissional(profissional)
                .data(request.data())
                .motivo(normalizarMotivo(request.motivo()))
                .build();
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional
    public void desbloquear(Long id, Long empresaId) {
        AgendaBlockedDayEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dia bloqueado nao encontrado."));
        if (empresaId != null && !empresaId.equals(entity.getEmpresa().getId())) {
            throw new BusinessException("Bloqueio nao pertence a empresa informada.");
        }
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public boolean diaBloqueado(Long empresaId, Long profissionalId, LocalDate data) {
        if (repository.existsByEmpresaIdAndDataAndProfissionalIsNull(empresaId, data)) {
            return true;
        }
        return profissionalId != null && repository.existsByEmpresaIdAndProfissionalIdAndData(empresaId, profissionalId, data);
    }

    private String normalizarMotivo(String motivo) {
        String texto = motivo == null ? "" : motivo.trim().replaceAll("\\s+", " ");
        return texto.isBlank() ? null : texto;
    }
}
