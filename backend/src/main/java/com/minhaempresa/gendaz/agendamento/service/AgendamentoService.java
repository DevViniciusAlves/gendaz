package com.minhaempresa.gendaz.agendamento.service;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AtualizarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.RemarcarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.mapper.AgendamentoMapper;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository.AgendamentoHorarioProjection;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.horarioatendimento.entity.HorarioAtendimentoEntity;
import com.minhaempresa.gendaz.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.gendaz.meugendazpromocao.service.MeuGendazPromocaoService;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.service.ServicoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.enums.TimezoneEnum;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgendamentoService {
    private final AgendamentoRepository agendamentoRepository;
    private final ClienteService clienteService;
    private final ServicoService servicoService;
    private final ProfissionalService profissionalService;
    private final EmpresaService empresaService;
    private final HorarioAtendimentoService horarioAtendimentoService;
    private final PagamentoRepository pagamentoRepository;
    private final AgendaBlockedDayService agendaBlockedDayService;
    private final SanitizacaoService sanitizacaoService;
    private final ResendEmailService resendEmailService;
    private final MeuGendazPromocaoService meuGendazPromocaoService;
    private final AgendamentoMapper mapper = new AgendamentoMapper();

    @Value("${app.timezone:America/Cuiaba}")
    private String appTimezone;

    @Transactional
    public AgendamentoResponse criar(CriarAgendamentoRequest request) {
        Map<String, Object> contextoInicio = new LinkedHashMap<>();
        contextoInicio.put("empresaId", request.empresaId());
        contextoInicio.put("clienteId", request.clienteId());
        contextoInicio.put("servicoId", request.servicoId());
        contextoInicio.put("profissionalId", request.profissionalId());
        contextoInicio.put("data", request.data());
        contextoInicio.put("horaInicio", request.horaInicio());
        contextoInicio.put("observacoes", request.observacoes());
        log.debug("[agendamento-debug] inicio criacao agendamento {}", contextoInicio);
        try {
            EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
            ClienteEntity cliente = clienteService.buscarEntidade(request.clienteId());
            ServicoEntity servico = servicoService.buscarEntidade(request.servicoId());
            ProfissionalEntity profissional = request.profissionalId() == null
                    ? profissionalService.buscarOuCriarAtendimentoPrincipal(empresa)
                    : profissionalService.buscarEntidade(request.profissionalId());
            LocalTime horaFim = request.horaInicio().plusMinutes(servico.getDuracaoMinutos());
            validarDataHorario(empresa.getId(), request.data(), request.horaInicio(), horaFim);
            validarDiaBloqueado(empresa.getId(), profissional.getId(), request.data());
            validarConflitoHorario(profissional.getId(), request.data(), request.horaInicio(), horaFim, null);
            AgendamentoEntity agendamento = AgendamentoEntity.builder()
                    .cliente(cliente)
                    .servico(servico)
                    .profissional(profissional)
                    .empresa(empresa)
                    .data(request.data())
                    .horaInicio(request.horaInicio())
                    .horaFim(horaFim)
                    .status(StatusAgendamento.PENDENTE)
                    .protocolo(gerarProtocoloSeNecessario(null))
                    .observacoes(sanitizacaoService.texto(request.observacoes()))
                    .build();
            AgendamentoEntity salvo = salvarAgendamentoComProtocolo(agendamento);
            Map<String, Object> contextoSucesso = new LinkedHashMap<>();
            contextoSucesso.put("agendamentoId", salvo.getId());
            contextoSucesso.put("protocolo", salvo.getProtocolo());
            contextoSucesso.put("empresaId", empresa.getId());
            contextoSucesso.put("clienteId", cliente.getId());
            contextoSucesso.put("clienteNome", cliente.getNome());
            contextoSucesso.put("servicoId", servico.getId());
            contextoSucesso.put("servicoNome", servico.getNome());
            contextoSucesso.put("profissionalId", profissional.getId());
            contextoSucesso.put("profissionalNome", profissional.getNome());
            contextoSucesso.put("data", salvo.getData());
            contextoSucesso.put("horaInicio", salvo.getHoraInicio());
            contextoSucesso.put("horaFim", salvo.getHoraFim());
            contextoSucesso.put("status", salvo.getStatus());
            log.info("[agendamento-debug] agendamento salvo com sucesso {}", contextoSucesso);
            try {
                criarPagamentoPendente(salvo, cliente, empresa, servico);
            } catch (Exception e) {
                Map<String, Object> contextoPagamentoErro = new LinkedHashMap<>();
                contextoPagamentoErro.put("agendamentoId", salvo.getId());
                contextoPagamentoErro.put("protocolo", salvo.getProtocolo());
                contextoPagamentoErro.put("empresaId", empresa.getId());
                contextoPagamentoErro.put("clienteId", cliente.getId());
                contextoPagamentoErro.put("servicoId", servico.getId());
                contextoPagamentoErro.put("profissionalId", profissional.getId());
                log.warn("[agendamento-debug] falha ao criar pagamento pendente. mensagem='{}' contexto={}", e.getMessage(), contextoPagamentoErro, e);
            }
            try {
                resendEmailService.enviarEmailNovoAgendamento(empresa, salvo);
            } catch (Exception e) {
                Map<String, Object> contextoEmailErro = new LinkedHashMap<>();
                contextoEmailErro.put("agendamentoId", salvo.getId());
                contextoEmailErro.put("protocolo", salvo.getProtocolo());
                contextoEmailErro.put("empresaId", empresa.getId());
                contextoEmailErro.put("clienteId", cliente.getId());
                contextoEmailErro.put("servicoId", servico.getId());
                contextoEmailErro.put("profissionalId", profissional.getId());
                log.error("[agendamento-debug] falha ao enviar email. mensagem='{}' contexto={}", e.getMessage(), contextoEmailErro, e);
            }
            try {
                resendEmailService.enviarConfirmacaoAgendamento(
                        cliente.getEmail(),
                        cliente.getNome(),
                        servico.getNome(),
                        profissional.getNome(),
                        salvo.getData(),
                        salvo.getHoraInicio(),
                        empresa.getNomeFantasia(),
                        empresa.getAgendamentoSlug()
                );
            } catch (Exception e) {
                Map<String, Object> contextoEmailConfirmacaoErro = new LinkedHashMap<>();
                contextoEmailConfirmacaoErro.put("agendamentoId", salvo.getId());
                contextoEmailConfirmacaoErro.put("protocolo", salvo.getProtocolo());
                contextoEmailConfirmacaoErro.put("clienteEmail", cliente.getEmail());
                log.error("[agendamento-debug] falha ao enviar email de confirmacao. mensagem='{}' contexto={}", e.getMessage(), contextoEmailConfirmacaoErro, e);
            }
            try {
                if (request.cupomCodigo() != null && !request.cupomCodigo().isBlank()) {
                    meuGendazPromocaoService.validarERegistrarUso(cliente, empresa, servico, request.cupomCodigo(), salvo.getId());
                }
            } catch (Exception e) {
                Map<String, Object> contextoCupomErro = new LinkedHashMap<>();
                contextoCupomErro.put("agendamentoId", salvo.getId());
                contextoCupomErro.put("protocolo", salvo.getProtocolo());
                contextoCupomErro.put("cupomCodigo", request.cupomCodigo());
                log.warn("[agendamento-debug] cupom nao aplicado. mensagem='{}' contexto={}", e.getMessage(), contextoCupomErro, e);
                throw new BusinessException(e.getMessage());
            }
            return mapper.toResponse(salvo);
        } catch (Exception e) {
            Map<String, Object> contextoErro = new LinkedHashMap<>();
            contextoErro.put("empresaId", request.empresaId());
            contextoErro.put("clienteId", request.clienteId());
            contextoErro.put("servicoId", request.servicoId());
            contextoErro.put("profissionalId", request.profissionalId());
            contextoErro.put("data", request.data());
            contextoErro.put("horaInicio", request.horaInicio());
            contextoErro.put("observacoes", request.observacoes());
            log.error("[agendamento-debug] erro ao criar agendamento. mensagem='{}' contexto={}", e.getMessage(), contextoErro, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return agendamentoRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarPorData(Long empresaId, LocalDate data) {
        validarEmpresaAtual(empresaId);
        return agendamentoRepository.findByEmpresaIdAndData(empresaId, data).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarPorCliente(Long clienteId) {
        return agendamentoRepository.findByClienteId(clienteId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarPorCliente(Long empresaId, Long clienteId) {
        validarEmpresaAtual(empresaId);
        return agendamentoRepository.findByEmpresaIdAndClienteId(empresaId, clienteId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<String> horariosDisponiveis(Long empresaId, Long profissionalId, Long servicoId, LocalDate data) {
        ServicoEntity servico = servicoService.buscarEntidade(servicoId);
        ProfissionalEntity profissional = null;
        boolean empresaSemProfissionaisReais = false;

        if (profissionalId == null) {
            var profissionaisAtivos = profissionalService.listarPorEmpresa(empresaId).stream()
                    .filter(item -> item.status() == com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO)
                    .toList();
            boolean todosSistema = !profissionaisAtivos.isEmpty()
                    && profissionaisAtivos.stream().allMatch(item -> {
                        ProfissionalEntity p = profissionalService.buscarEntidade(item.id());
                        return p.isSistema();
                    });
            if (todosSistema) {
                empresaSemProfissionaisReais = true;
            } else if (!profissionaisAtivos.isEmpty()) {
                profissional = profissionaisAtivos.stream()
                        .map(item -> profissionalService.buscarEntidade(item.id()))
                        .filter(p -> !p.isSistema())
                        .findFirst()
                        .orElse(null);
            }
        } else {
            profissional = profissionalService.buscarEntidade(profissionalId);
        }

        HorarioAtendimentoEntity horario = horarioAtendimentoService.obterHorarioEfetivo(empresaId, data);
        if (!horario.isAtivo() || horario.getHoraInicio() == null || horario.getHoraFim() == null) {
            return List.of();
        }

        List<AgendamentoHorarioProjection> agendados;
        if (empresaSemProfissionaisReais) {
            agendados = agendamentoRepository.findByEmpresaIdAndDataHorarios(empresaId, data);
        } else if (profissional != null) {
            agendados = agendamentoRepository.findByProfissionalIdAndData(profissional.getId(), data);
        } else {
            agendados = List.of();
        }
        List<String> horarios = new ArrayList<>();
        LocalTime horaAtual = horario.getHoraInicio();
        while (horaAtual.isBefore(horario.getHoraFim())) {
            LocalTime inicio = horaAtual;
            LocalTime fim = inicio.plusMinutes(servico.getDuracaoMinutos());

            if (fim.isAfter(horario.getHoraFim())) {
                break;
            }

            boolean dentroDoIntervalo = horario.getIntervaloInicio() != null
                    && horario.getIntervaloFim() != null
                    && inicio.isBefore(horario.getIntervaloFim())
                    && fim.isAfter(horario.getIntervaloInicio());
            boolean ocupado = agendados.stream()
                    .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                    .anyMatch(a -> inicio.isBefore(a.getHoraFim()) && fim.isAfter(a.getHoraInicio()));
            if (!ocupado && !dentroDoIntervalo) {
                horarios.add(inicio.toString());
            }

            horaAtual = horaAtual.plusMinutes(30);
        }
        return horarios;
    }

    @Transactional
    public AgendamentoResponse confirmar(Long id) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        if (agendamentoRepository.existsByProfissionalIdAndDataAndHoraInicioAndStatus(
                agendamento.getProfissional().getId(), agendamento.getData(), agendamento.getHoraInicio(), StatusAgendamento.CONFIRMADO)) {
            throw new ConflictException("Ja existe agendamento confirmado para este profissional neste horario.");
        }
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        return mapper.toResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional
    public AgendamentoResponse cancelar(Long id) {
        return alterarStatus(id, StatusAgendamento.CANCELADO);
    }

    @Transactional
    public AgendamentoResponse cancelar(Long id, Long empresaId) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        validarEmpresa(agendamento, empresaId);
        agendamento.setStatus(StatusAgendamento.CANCELADO);
        return mapper.toResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional
    public void excluir(Long id, Long empresaId) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        validarEmpresa(agendamento, empresaId);
        // lembretePagamentoRepository.deleteByAgendamento_Id(id);
        pagamentoRepository.deleteByAgendamentoId(id);
        agendamentoRepository.delete(agendamento);
    }

    @Transactional
    public AgendamentoResponse finalizar(Long id, Boolean pagamentoRealizado) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        agendamento.setStatus(StatusAgendamento.FINALIZADO);
        pagamentoRepository.findByAgendamento_Id(id).ifPresentOrElse(pagamento -> {
            boolean pago = pagamentoRealizado == null || Boolean.TRUE.equals(pagamentoRealizado);
            pagamento.setStatus(pago ? StatusPagamento.PAGO : StatusPagamento.PENDENTE);
            pagamento.setDataPagamento(pago ? LocalDateTime.now(ZoneId.of(appTimezone)) : null);
            pagamentoRepository.save(pagamento);
        }, () -> log.warn("[agendamento-debug] finalizar agendamento sem pagamento vinculado. agendamentoId={}", id));
        return mapper.toResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional
    public AgendamentoResponse iniciar(Long id) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        if (agendamento.getStatus() != StatusAgendamento.PENDENTE
                && agendamento.getStatus() != StatusAgendamento.CONFIRMADO
                && agendamento.getStatus() != StatusAgendamento.PAUSADO) {
            throw new BusinessException("Apenas agendamentos pendentes, confirmados ou pausados podem ser iniciados.");
        }
        agendamento.setStatus(StatusAgendamento.EM_ATENDIMENTO);
        return mapper.toResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional
    public AgendamentoResponse pausar(Long id) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        if (agendamento.getStatus() != StatusAgendamento.EM_ATENDIMENTO) {
            throw new BusinessException("Apenas agendamentos em atendimento podem ser pausados.");
        }
        agendamento.setStatus(StatusAgendamento.PAUSADO);
        return mapper.toResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional
    public AgendamentoResponse remarcar(Long id, RemarcarAgendamentoRequest request) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        LocalTime horaFim = request.horaInicio().plusMinutes(agendamento.getServico().getDuracaoMinutos());
        validarDataHorario(agendamento.getEmpresa().getId(), request.data(), request.horaInicio(), horaFim);
        validarDiaBloqueado(agendamento.getEmpresa().getId(), agendamento.getProfissional().getId(), request.data());
        agendamento.setData(request.data());
        agendamento.setHoraInicio(request.horaInicio());
        agendamento.setHoraFim(horaFim);
        validarConflitoHorario(agendamento.getProfissional().getId(), request.data(), agendamento.getHoraInicio(), agendamento.getHoraFim(), agendamento.getId());
        agendamento.setStatus(StatusAgendamento.PENDENTE);
        return mapper.toResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional
    public AgendamentoResponse remarcar(Long id, RemarcarAgendamentoRequest request, Long empresaId) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        validarEmpresa(agendamento, empresaId);
        LocalTime horaFim = request.horaInicio().plusMinutes(agendamento.getServico().getDuracaoMinutos());
        validarDataHorario(agendamento.getEmpresa().getId(), request.data(), request.horaInicio(), horaFim);
        validarDiaBloqueado(agendamento.getEmpresa().getId(), agendamento.getProfissional().getId(), request.data());
        agendamento.setData(request.data());
        agendamento.setHoraInicio(request.horaInicio());
        agendamento.setHoraFim(horaFim);
        validarConflitoHorario(agendamento.getProfissional().getId(), request.data(), agendamento.getHoraInicio(), agendamento.getHoraFim(), agendamento.getId());
        agendamento.setStatus(StatusAgendamento.PENDENTE);
        return mapper.toResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional
    public AgendamentoResponse atualizar(Long id, AtualizarAgendamentoRequest request) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        ClienteEntity cliente = clienteService.buscarEntidade(request.clienteId());
        ServicoEntity servico = servicoService.buscarEntidade(request.servicoId());
        ProfissionalEntity profissional = profissionalService.buscarEntidade(request.profissionalId());
        EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());

        LocalTime horaFim = request.horaInicio().plusMinutes(servico.getDuracaoMinutos());
        validarDataHorario(empresa.getId(), request.data(), request.horaInicio(), horaFim);
        validarDiaBloqueado(empresa.getId(), profissional.getId(), request.data());
        if (request.status() != StatusAgendamento.CANCELADO) {
            validarConflitoHorario(profissional.getId(), request.data(), request.horaInicio(), horaFim, agendamento.getId());
        }

        agendamento.setCliente(cliente);
        agendamento.setServico(servico);
        agendamento.setProfissional(profissional);
        agendamento.setEmpresa(empresa);
        agendamento.setData(request.data());
        agendamento.setHoraInicio(request.horaInicio());
        agendamento.setHoraFim(horaFim);
        agendamento.setStatus(request.status());
        agendamento.setObservacoes(sanitizacaoService.texto(request.observacoes()));

        return mapper.toResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional(readOnly = true)
    public AgendamentoEntity buscarEntidade(Long id) {
        AgendamentoEntity agendamento = agendamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento nao encontrado."));
        validarEmpresaAtual(agendamento.getEmpresa().getId());
        return agendamento;
    }

    private AgendamentoResponse alterarStatus(Long id, StatusAgendamento status) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        agendamento.setStatus(status);
        return mapper.toResponse(agendamentoRepository.save(agendamento));
    }

    private void validarEmpresa(AgendamentoEntity agendamento, Long empresaId) {
        if (empresaId != null && (agendamento.getEmpresa() == null || !empresaId.equals(agendamento.getEmpresa().getId()))) {
            throw new BusinessException("Agendamento nao pertence a empresa informada.");
        }
    }

    private void validarDiaBloqueado(Long empresaId, Long profissionalId, LocalDate data) {
        if (agendaBlockedDayService.diaBloqueado(empresaId, profissionalId, data)) {
            throw new BusinessException("A agenda esta bloqueada para esta data.");
        }
    }

    private void validarConflitoHorario(Long profissionalId, LocalDate data, LocalTime horaInicio, LocalTime horaFim, Long ignorarId) {
        if (agendamentoRepository.existeConflitoDeHorario(profissionalId, data, horaInicio, horaFim, StatusAgendamento.CANCELADO, ignorarId)) {
            throw new ConflictException("Ja existe agendamento para este profissional neste horario.");
        }
    }

    private void validarDataHorario(Long empresaId, LocalDate data, LocalTime horaInicio, LocalTime horaFim) {
        EmpresaEntity empresa = empresaService.buscarEntidade(empresaId);
        ZoneId zoneId = resolverZoneId(empresa.getTimezone());
        LocalDate hoje = LocalDate.now(zoneId);
        if (data.isBefore(hoje) || data.isAfter(hoje.plusYears(2))) {
            throw new BusinessException("Data do agendamento nao pode ser no passado.");
        }
        horarioAtendimentoService.validarHorarioAtendimento(empresaId, data, horaInicio, horaFim);
        if (data.isEqual(hoje) && horaInicio.isBefore(LocalTime.now(zoneId))) {
            throw new BusinessException("Nao e possivel criar agendamento em horario que ja passou.");
        }
    }

    private ZoneId resolverZoneId(String timezone) {
        String valor = timezone == null || timezone.isBlank()
                ? appTimezone
                : timezone;
        if (valor == null || valor.isBlank()) {
            valor = TimezoneEnum.AMERICA_CUIABA.getValue();
        }
        return ZoneId.of(valor);
    }

    private void criarPagamentoPendente(AgendamentoEntity agendamento, ClienteEntity cliente, EmpresaEntity empresa, ServicoEntity servico) {
        pagamentoRepository.save(PagamentoEntity.builder()
                .agendamento(agendamento)
                .cliente(cliente)
                .empresa(empresa)
                .valor(servico.getValor())
                .metodoPagamento(MetodoPagamento.OUTRO)
                .status(StatusPagamento.PENDENTE)
                .build());
    }

    private AgendamentoEntity salvarAgendamentoComProtocolo(AgendamentoEntity agendamento) {
        for (int tentativa = 0; tentativa < 3; tentativa += 1) {
            try {
                if (agendamento.getProtocolo() == null || agendamento.getProtocolo().isBlank()) {
                    agendamento.setProtocolo(gerarProtocoloSeNecessario(null));
                }
                return agendamentoRepository.save(agendamento);
            } catch (DataIntegrityViolationException ex) {
                if (String.valueOf(ex.getMessage()).toLowerCase().contains("protocolo")) {
                    agendamento.setProtocolo(null);
                    continue;
                }
                throw ex;
            }
        }
        throw new IllegalStateException("Nao foi possivel salvar o agendamento com protocolo unico.");
    }

    private String gerarProtocoloSeNecessario(String protocoloAtual) {
        if (protocoloAtual != null && !protocoloAtual.isBlank()) {
            return protocoloAtual;
        }
        for (int i = 0; i < 20; i++) {
            String protocolo = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
            if (!agendamentoRepository.existsByProtocolo(protocolo)) {
                return protocolo;
            }
        }
        throw new IllegalStateException("Nao foi possivel gerar protocolo unico para o agendamento.");
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = com.minhaempresa.gendaz.shared.CompanyContext.getCompanyId();
        if (companyId != null && empresaId != null && !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Agendamento nao encontrado.");
        }
    }
}

