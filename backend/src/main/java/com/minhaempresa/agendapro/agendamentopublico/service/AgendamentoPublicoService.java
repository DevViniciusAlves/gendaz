package com.minhaempresa.agendapro.agendamentopublico.service;

import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.agendapro.agendamento.service.AgendamentoService;
import com.minhaempresa.agendapro.agendamentopublico.dto.AgendamentoPublicoDtos.AgendamentoPublicoResponse;
import com.minhaempresa.agendapro.agendamentopublico.dto.AgendamentoPublicoDtos.BookingEmpresaResponse;
import com.minhaempresa.agendapro.agendamentopublico.dto.AgendamentoPublicoDtos.BookingProfissionalResponse;
import com.minhaempresa.agendapro.agendamentopublico.dto.AgendamentoPublicoDtos.BookingServicoResponse;
import com.minhaempresa.agendapro.agendamentopublico.dto.AgendamentoPublicoDtos.CriarAgendamentoPublicoRequest;
import com.minhaempresa.agendapro.configuracao.dto.HorarioAtendimentoDtos.HorarioAtendimentoResponse;
import com.minhaempresa.agendapro.assinatura.enums.StatusAssinatura;
import com.minhaempresa.agendapro.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.enums.StatusEmpresa;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;
import com.minhaempresa.agendapro.profissional.repository.ProfissionalRepository;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import com.minhaempresa.agendapro.servico.repository.ServicoRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import com.minhaempresa.agendapro.shared.enums.TimezoneEnum;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgendamentoPublicoService {
    private final EmpresaRepository empresaRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ClienteRepository clienteRepository;
    private final AgendamentoService agendamentoService;
    private final HorarioAtendimentoService horarioAtendimentoService;
    private final AssinaturaRepository assinaturaRepository;

    @Transactional(readOnly = true)
    public BookingEmpresaResponse carregar(String slugOuEmpresaId) {
        EmpresaEntity empresa = buscarEmpresa(slugOuEmpresaId);
        if (!empresaDisponivel(empresa)) {
            return new BookingEmpresaResponse(
                    empresa.getAgendamentoSlug(),
                    empresa.getNomeFantasia(),
                    false,
                    "Agendamento indisponivel no momento.",
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        List<BookingServicoResponse> servicos = servicoRepository.findByEmpresaId(empresa.getId()).stream()
                .filter(servico -> servico.getStatus() == StatusCadastro.ATIVO)
                .map(this::toServico)
                .toList();

        List<BookingProfissionalResponse> profissionais = profissionalRepository.findByEmpresaId(empresa.getId()).stream()
                .filter(profissional -> profissional.getStatus() == StatusCadastro.ATIVO)
                .map(this::toProfissional)
                .toList();

        List<HorarioAtendimentoResponse> horariosAtendimento = horarioAtendimentoService.listarPorEmpresa(empresa.getId());

        return new BookingEmpresaResponse(empresa.getAgendamentoSlug(), empresa.getNomeFantasia(), true, null, servicos, profissionais, horariosAtendimento);
    }

    @Transactional(readOnly = true)
    public List<String> horarios(String slugOuEmpresaId, Long profissionalId, Long servicoId, LocalDate data) {
        EmpresaEntity empresa = buscarEmpresaAtiva(slugOuEmpresaId);
        ZoneId zoneId = resolverZoneId(empresa.getTimezone());
        LocalDate hojeEmpresa = LocalDate.now(zoneId);
        if (data.isBefore(hojeEmpresa)) {
            return List.of();
        }
        validarRecursoDaEmpresa(empresa.getId(), servicoId, profissionalId);

        List<String> horarios = agendamentoService.horariosDisponiveis(empresa.getId(), profissionalId, servicoId, data);
        if (data.equals(hojeEmpresa)) {
            LocalTime agora = LocalTime.now(zoneId);
            return horarios.stream()
                    .filter(horario -> LocalTime.parse(horario).isAfter(agora))
                    .toList();
        }
        return horarios;
    }

    @Transactional
    public AgendamentoPublicoResponse agendar(String slugOuEmpresaId, CriarAgendamentoPublicoRequest request) {
        EmpresaEntity empresa = buscarEmpresaAtiva(slugOuEmpresaId);
        validarRecursoDaEmpresa(empresa.getId(), request.servicoId(), request.profissionalId());

        ClienteEntity cliente = buscarOuCriarCliente(empresa, request);
        String observacao = normalizarObservacao(request.observacao());
        AgendamentoResponse agendamento = agendamentoService.criar(new CriarAgendamentoRequest(
                cliente.getId(),
                request.servicoId(),
                request.profissionalId(),
                empresa.getId(),
                request.data(),
                request.horaInicio(),
                observacao == null ? "Criado pelo link publico de agendamento." : observacao
        ));

        return new AgendamentoPublicoResponse("Agendamento solicitado com sucesso.", agendamento);
    }

    private ClienteEntity buscarOuCriarCliente(EmpresaEntity empresa, CriarAgendamentoPublicoRequest request) {
        String telefone = request.clienteTelefone().replaceAll("\\D", "");
        return clienteRepository.findFirstByEmpresaIdAndTelefone(empresa.getId(), telefone)
                .map(cliente -> atualizarClientePublico(cliente, request))
                .orElseGet(() -> clienteRepository.save(ClienteEntity.builder()
                        .nome(normalizarNome(request.clienteNome()))
                        .telefone(telefone)
                        .email(normalizarEmail(request.clienteEmail()))
                        .observacoes(normalizarObservacao(request.observacao()))
                        .empresa(empresa)
                        .build()));
    }

    private ClienteEntity atualizarClientePublico(ClienteEntity cliente, CriarAgendamentoPublicoRequest request) {
        cliente.setNome(normalizarNome(request.clienteNome()));
        String email = normalizarEmail(request.clienteEmail());
        if (email != null) {
            cliente.setEmail(email);
        }
        String observacao = normalizarObservacao(request.observacao());
        if (observacao != null) {
            cliente.setObservacoes(observacao);
        }
        return clienteRepository.save(cliente);
    }

    private EmpresaEntity buscarEmpresaAtiva(String slugOuEmpresaId) {
        EmpresaEntity empresa = buscarEmpresa(slugOuEmpresaId);
        if (!empresaDisponivel(empresa)) {
            throw new BusinessException("Agendamento indisponivel no momento.");
        }
        return empresa;
    }

    private boolean empresaDisponivel(EmpresaEntity empresa) {
        if (empresa.getStatus() != StatusEmpresa.ATIVA) {
            return false;
        }
        return assinaturaRepository.findFirstByEmpresaIdOrderByIdDesc(empresa.getId())
                .map(assinatura -> assinatura.getStatus() == StatusAssinatura.ATIVA || assinatura.getStatus() == StatusAssinatura.TESTE)
                .orElse(false);
    }

    private EmpresaEntity buscarEmpresa(String slugOuEmpresaId) {
        if (slugOuEmpresaId == null || slugOuEmpresaId.isBlank() || slugOuEmpresaId.matches("\\d+")) {
            throw new ResourceNotFoundException("Empresa nao encontrada.");
        }
        return empresaRepository.findByAgendamentoSlug(slugOuEmpresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
    }

    private void validarRecursoDaEmpresa(Long empresaId, Long servicoId, Long profissionalId) {
        ServicoEntity servico = servicoRepository.findById(servicoId)
                .orElseThrow(() -> new ResourceNotFoundException("Servico nao encontrado."));
        if (!servico.getEmpresa().getId().equals(empresaId) || servico.getStatus() != StatusCadastro.ATIVO) {
            throw new BusinessException("Servico indisponivel.");
        }
        if (profissionalId != null) {
            ProfissionalEntity profissional = profissionalRepository.findById(profissionalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profissional nao encontrado."));
            if (!profissional.getEmpresa().getId().equals(empresaId) || profissional.getStatus() != StatusCadastro.ATIVO) {
                throw new BusinessException("Profissional indisponivel.");
            }
        }
    }

    private BookingServicoResponse toServico(ServicoEntity servico) {
        return new BookingServicoResponse(servico.getId(), servico.getNome(), servico.getDescricao(), servico.getDuracaoMinutos(), servico.getValor());
    }

    private BookingProfissionalResponse toProfissional(ProfissionalEntity profissional) {
        return new BookingProfissionalResponse(profissional.getId(), profissional.getNome(), profissional.getEspecialidade());
    }

    private String normalizarNome(String valor) {
        return valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
    }

    private String normalizarEmail(String valor) {
        String email = valor == null ? "" : valor.trim().toLowerCase();
        return email.isBlank() ? null : email;
    }

    private String normalizarObservacao(String valor) {
        String observacao = valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
        return observacao.isBlank() ? null : observacao;
    }

    private ZoneId resolverZoneId(String timezone) {
        String valor = timezone == null || timezone.isBlank()
                ? TimezoneEnum.AMERICA_CUIABA.getValue()
                : timezone;
        return ZoneId.of(valor);
    }
}
