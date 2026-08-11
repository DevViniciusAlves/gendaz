package com.minhaempresa.gendaz.horarioatendimento.service;

import com.minhaempresa.gendaz.configuracao.dto.HorarioAtendimentoDtos.HorarioAtendimentoItemRequest;
import com.minhaempresa.gendaz.configuracao.dto.HorarioAtendimentoDtos.HorarioAtendimentoResponse;
import com.minhaempresa.gendaz.configuracao.dto.HorarioAtendimentoDtos.SalvarHorariosAtendimentoRequest;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.horarioatendimento.entity.HorarioAtendimentoEntity;
import com.minhaempresa.gendaz.horarioatendimento.enums.DiaSemanaAtendimento;
import com.minhaempresa.gendaz.horarioatendimento.repository.HorarioAtendimentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class HorarioAtendimentoService {
    private final HorarioAtendimentoRepository repository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<HorarioAtendimentoResponse> listarPorUsuario(Long usuarioId) {
        EmpresaEntity empresa = buscarEmpresaDoUsuario(usuarioId);
        return listarPorEmpresa(empresa.getId());
    }

    @Transactional(readOnly = true)
    public List<HorarioAtendimentoResponse> listarPorEmpresa(Long empresaId) {
        Map<DiaSemanaAtendimento, HorarioAtendimentoEntity> porDia = carregarMapeado(empresaId);
        return DiaSemanaAtendimento.ordemPadrao().stream()
                .map(dia -> toResponse(empresaId, porDia.getOrDefault(dia, padrao(empresaId, dia))))
                .toList();
    }

    @Transactional
    public List<HorarioAtendimentoResponse> salvar(Long usuarioId, SalvarHorariosAtendimentoRequest request) {
        EmpresaEntity empresa = buscarEmpresaDoUsuario(usuarioId);
        validarPayload(request);
        Map<DiaSemanaAtendimento, HorarioAtendimentoItemRequest> porDia = new EnumMap<>(DiaSemanaAtendimento.class);
        request.horarios().forEach(item -> {
            if (porDia.putIfAbsent(item.diaSemana(), item) != null) {
                throw new BusinessException("Cada dia da semana deve aparecer apenas uma vez.");
            }
        });
        if (porDia.size() != DiaSemanaAtendimento.values().length) {
            throw new BusinessException("Informe os horarios de todos os dias da semana.");
        }

        for (DiaSemanaAtendimento dia : DiaSemanaAtendimento.ordemPadrao()) {
            HorarioAtendimentoItemRequest item = porDia.get(dia);
            HorarioAtendimentoEntity entity = repository.findByEmpresaIdAndDiaSemana(empresa.getId(), dia)
                    .orElseGet(() -> HorarioAtendimentoEntity.builder()
                            .empresa(empresa)
                            .diaSemana(dia)
                            .build());
            aplicar(entity, item);
            repository.save(entity);
        }

        return listarPorEmpresa(empresa.getId());
    }

    @Transactional(readOnly = true)
    public HorarioAtendimentoEntity obterHorarioEfetivo(Long empresaId, LocalDate data) {
        DiaSemanaAtendimento dia = DiaSemanaAtendimento.from(data.getDayOfWeek());
        return repository.findByEmpresaIdAndDiaSemana(empresaId, dia)
                .orElseGet(() -> padrao(empresaId, dia));
    }

    @Transactional(readOnly = true)
    public void validarHorarioAtendimento(Long empresaId, LocalDate data, LocalTime horaInicio, LocalTime horaFim) {
        HorarioAtendimentoEntity horario = obterHorarioEfetivo(empresaId, data);
        if (!horario.isAtivo()) {
            throw new BusinessException("A empresa nao atende neste dia.");
        }
        if (horaInicio == null || horaFim == null) {
            throw new BusinessException("Horario invalido.");
        }
        if (!horaInicio.isBefore(horaFim)) {
            throw new BusinessException("Horario de inicio deve ser menor que horario de fim.");
        }
        if (horario.getHoraInicio() != null && horaInicio.isBefore(horario.getHoraInicio())) {
            throw new BusinessException("Horario fora do horario de atendimento.");
        }
        if (horario.getHoraFim() != null && horaFim.isAfter(horario.getHoraFim())) {
            throw new BusinessException("Horario fora do horario de atendimento.");
        }
        if (horario.getIntervaloInicio() != null && horario.getIntervaloFim() != null
                && horaInicio.isBefore(horario.getIntervaloFim())
                && horaFim.isAfter(horario.getIntervaloInicio())) {
            throw new BusinessException("Horario indisponivel durante o intervalo de atendimento.");
        }
    }

    @Transactional(readOnly = true)
    public boolean horarioAbertoNoDia(Long empresaId, LocalDate data) {
        return obterHorarioEfetivo(empresaId, data).isAtivo();
    }

    private void validarPayload(SalvarHorariosAtendimentoRequest request) {
        request.horarios().forEach(item -> {
            if (item.ativo() == null) {
                throw new BusinessException("Informe se o dia esta ativo ou inativo.");
            }
            if (item.horaInicio() != null && item.horaFim() != null && !item.horaInicio().isBefore(item.horaFim())) {
                throw new BusinessException("Hora de inicio deve ser menor que hora de fim.");
            }
            if ((item.intervaloInicio() == null) != (item.intervaloFim() == null)) {
                throw new BusinessException("Informe o inicio e o fim do intervalo.");
            }
            if (Boolean.TRUE.equals(item.ativo()) && (item.horaInicio() == null || item.horaFim() == null)) {
                throw new BusinessException("Dias ativos precisam de horario de inicio e fim.");
            }
            if (item.horaInicio() != null && item.horaFim() != null && item.intervaloInicio() != null && item.intervaloFim() != null) {
                if (!item.intervaloInicio().isAfter(item.horaInicio()) || !item.intervaloFim().isBefore(item.horaFim())) {
                    throw new BusinessException("Intervalo deve ficar dentro do expediente.");
                }
                if (!item.intervaloInicio().isBefore(item.intervaloFim())) {
                    throw new BusinessException("Intervalo de inicio deve ser menor que o fim.");
                }
            }
        });
    }

    private void aplicar(HorarioAtendimentoEntity entity, HorarioAtendimentoItemRequest item) {
        entity.setAtivo(Boolean.TRUE.equals(item.ativo()));
        entity.setHoraInicio(item.horaInicio());
        entity.setHoraFim(item.horaFim());
        entity.setIntervaloInicio(item.intervaloInicio());
        entity.setIntervaloFim(item.intervaloFim());
    }

    private Map<DiaSemanaAtendimento, HorarioAtendimentoEntity> carregarMapeado(Long empresaId) {
        Map<DiaSemanaAtendimento, HorarioAtendimentoEntity> mapa = new EnumMap<>(DiaSemanaAtendimento.class);
        repository.findByEmpresaIdOrderByDiaSemanaAsc(empresaId).forEach(entity -> mapa.put(entity.getDiaSemana(), entity));
        return mapa;
    }

    private HorarioAtendimentoEntity padrao(Long empresaId, DiaSemanaAtendimento dia) {
        boolean ativo = dia.getOrdem() >= 1 && dia.getOrdem() <= 5;
        return HorarioAtendimentoEntity.builder()
                .empresa(EmpresaEntity.builder().id(empresaId).build())
                .diaSemana(dia)
                .ativo(ativo)
                .horaInicio(ativo ? LocalTime.of(8, 0) : null)
                .horaFim(ativo ? LocalTime.of(18, 0) : null)
                .build();
    }

    private HorarioAtendimentoResponse toResponse(Long empresaId, HorarioAtendimentoEntity entity) {
        return new HorarioAtendimentoResponse(
                entity.getId(),
                entity.getDiaSemana(),
                entity.getDiaSemana().getRotulo(),
                entity.isAtivo(),
                entity.getHoraInicio(),
                entity.getHoraFim(),
                entity.getIntervaloInicio(),
                entity.getIntervaloFim()
        );
    }

    private EmpresaEntity buscarEmpresaDoUsuario(Long usuarioId) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado."));
        if (usuario.getEmpresa() == null) {
            throw new BusinessException("Usuario sem empresa nao possui horarios de atendimento.");
        }
        return usuario.getEmpresa();
    }
}

