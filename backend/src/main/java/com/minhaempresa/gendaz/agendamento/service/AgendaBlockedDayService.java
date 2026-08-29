package com.minhaempresa.gendaz.agendamento.service;

import com.minhaempresa.gendaz.agendamento.dto.AgendaBlockedDayDtos.BloquearDiaRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendaBlockedDayDtos.DiaBloqueadoResponse;
import com.minhaempresa.gendaz.agendamento.entity.AgendaBlockedDayEntity;
import com.minhaempresa.gendaz.agendamento.mapper.AgendaBlockedDayMapper;
import com.minhaempresa.gendaz.agendamento.repository.AgendaBlockedDayRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
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
    private final LogAtividadeService logAtividadeService;
    private final AgendaBlockedDayMapper mapper = new AgendaBlockedDayMapper();

    @Transactional(readOnly = true)
    public List<DiaBloqueadoResponse> listar(Long empresaId) {
        Long empresaAutenticadaId = CompanyContext.requireCompanyId();
        CompanyContext.exigirEmpresa(empresaId);
        return repository.findByEmpresaIdOrderByDataAsc(empresaAutenticadaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public DiaBloqueadoResponse bloquear(BloquearDiaRequest request) {
        Long empresaAutenticadaId = CompanyContext.requireCompanyId();
        CompanyContext.exigirEmpresa(request.empresaId());

        if (request.data().isBefore(LocalDate.now())) {
            throw new BusinessException("Nao e possivel bloquear uma data passada.");
        }

        EmpresaEntity empresa = empresaService.buscarEntidade(empresaAutenticadaId);
        ProfissionalEntity profissional = null;
        if (request.profissionalId() != null) {
            profissional = profissionalService.buscarEntidade(request.profissionalId());
            if (profissional.getEmpresa() == null || !profissional.getEmpresa().getId().equals(empresaAutenticadaId)) {
                throw new BusinessException("Profissional não pertence a empresa informada.");
            }
        }

        if (diaBloqueado(empresaAutenticadaId, profissional == null ? null : profissional.getId(), request.data())) {
            throw new BusinessException("Este dia ja esta bloqueado.");
        }

        AgendaBlockedDayEntity entity = AgendaBlockedDayEntity.builder()
                .empresa(empresa)
                .profissional(profissional)
                .data(request.data())
                .motivo(normalizarMotivo(request.motivo()))
                .build();
        AgendaBlockedDayEntity salvo = repository.save(entity);
        logAtividadeService.registrar("DIA_BLOQUEADO", salvo.getId(), "Bloqueou dia " + entity.getData());
        return mapper.toResponse(salvo);
    }

    @Transactional
    public void desbloquear(Long id, Long empresaId) {
        Long empresaAutenticadaId = CompanyContext.requireCompanyId();
        CompanyContext.exigirEmpresa(empresaId);

        AgendaBlockedDayEntity entity = repository.findByIdAndEmpresaId(id, empresaAutenticadaId)
                .orElseThrow(() -> new ResourceNotFoundException("Dia bloqueado não encontrado."));
        LocalDate data = entity.getData();
        repository.delete(entity);
        logAtividadeService.registrar("DIA_BLOQUEADO", id, "Desbloqueou dia " + data);
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

