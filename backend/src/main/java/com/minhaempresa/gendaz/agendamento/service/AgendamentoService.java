package com.minhaempresa.gendaz.agendamento.service;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AtualizarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.RemarcarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.mapper.AgendamentoMapper;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository.AgendamentoHorarioProjection;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.agendamento.event.AgendamentoCriadoEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.financeiro.caixadespesas.service.CaixaDespesasService;
import com.minhaempresa.gendaz.horarioatendimento.entity.HorarioAtendimentoEntity;
import com.minhaempresa.gendaz.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.gendaz.meugendazpromocao.dto.MeuGendazPromocaoDtos.CupomAplicadoResult;
import com.minhaempresa.gendaz.meugendazpromocao.service.MeuGendazPromocaoService;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.pagamento.service.FormaPagamentoEmpresaService;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.service.ServicoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.enums.TimezoneEnum;
import java.math.BigDecimal;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final FormaPagamentoEmpresaService formaPagamentoEmpresaService;
    private final LogAtividadeService logAtividadeService;
    private final CaixaDespesasService caixaDespesasService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    @Autowired
    @Lazy
    private PagamentoService pagamentoService;
    private final AgendamentoMapper mapper = new AgendamentoMapper();


    @Value("${app.timezone:America/Sao_Paulo}")
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
        log.debug("[agendamento-debug] inicio criacao agendamento {}", contextoInicio);
        try {
            EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
            ClienteEntity cliente = clienteService.buscarEntidadeOperacional(request.clienteId());
            ServicoEntity servico = servicoService.buscarEntidadeOperacional(request.servicoId());
            ProfissionalEntity profissionalResolvido = request.profissionalId() == null
                    ? profissionalParaSemPreferencia(empresa, request.data())
                    : profissionalService.buscarEntidade(request.profissionalId());
            // Mutex da agenda: a partir daqui a verificacao de conflito de
            // intervalo + save sao atomicos contra criar/remarcar/atualizar
            // concorrentes do MESMO profissional. O lock vem ANTES do check.
            ProfissionalEntity profissional = profissionalService.buscarEntidadeParaReserva(
                    profissionalResolvido.getId(), empresa.getId());
            validarProfissionalAgendamento(empresa, profissional, request.data());
            int duracao = servico.getDuracaoMinutos() != null ? servico.getDuracaoMinutos() : 30;
            LocalTime horaFim = request.horaInicio().plusMinutes(duracao);
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
            contextoSucesso.put("empresaId", empresa.getId());
            contextoSucesso.put("clienteId", cliente.getId());
            contextoSucesso.put("servicoId", servico.getId());
            contextoSucesso.put("profissionalId", profissional.getId());
            contextoSucesso.put("status", salvo.getStatus());
            log.info("[agendamento-debug] agendamento salvo com sucesso {}", contextoSucesso);

            BigDecimal valorOriginal = servico.getValor() != null ? servico.getValor() : BigDecimal.ZERO;
            BigDecimal desconto = BigDecimal.ZERO;
            if (request.cupomCodigo() != null && !request.cupomCodigo().isBlank()) {
                try {
                    CupomAplicadoResult cupom = meuGendazPromocaoService.aplicarCupomAoAgendamento(
                            cliente, empresa, servico, request.cupomCodigo(), salvo.getId());
                    if (cupom != null) {
                        desconto = cupom.desconto() != null ? cupom.desconto() : BigDecimal.ZERO;
                        salvo.setCupomCodigo(cupom.codigo());
                        salvo.setTipoPromocaoAplicada(cupom.tipo());
                        salvo.setValorPromocaoAplicada(cupom.valorPromocao());
                        salvo.setPromocaoOrigemId(cupom.promocaoOrigemId());
                    }
                } catch (Exception e) {
                    Map<String, Object> contextoCupomErro = new LinkedHashMap<>();
                    contextoCupomErro.put("agendamentoId", salvo.getId());
                    log.warn("[agendamento-debug] cupom nao aplicado. erroTipo={} contexto={}", e.getClass().getSimpleName(), contextoCupomErro);
                }
            }
            BigDecimal valorFinal = valorOriginal.subtract(desconto).max(BigDecimal.ZERO);
            salvo.setValorOriginal(valorOriginal);
            salvo.setValorDesconto(desconto);
            salvo.setValorFinal(valorFinal);
            salvo = agendamentoRepository.save(salvo);
            logAtividadeService.registrar("AGENDAMENTO", salvo.getId(), "Criou agendamento para " + cliente.getNome());

            final AgendamentoEntity agendamentoFinal = salvo;
            final ClienteEntity clienteFinal = cliente;
            final EmpresaEntity empresaFinal = empresa;
            final ServicoEntity servicoFinal = servico;
            final ProfissionalEntity profissionalFinal = profissional;
            // Pagamento criado na MESMA transacao do agendamento: se falhar,
            // o agendamento sofre rollback e nao persiste estado parcial
            // (agendamento sem o pagamento obrigatorio). Nao engolir excecao.
            criarPagamentoPendente(agendamentoFinal, clienteFinal, empresaFinal);
            eventPublisher.publishEvent(new AgendamentoCriadoEvent(
                    agendamentoFinal.getId(),
                    empresaFinal.getId(),
                    empresaFinal.getNomeFantasia(),
                    empresaFinal.getAgendamentoSlug(),
                    empresaFinal.getEmail(),
                    clienteFinal.getId(),
                    clienteFinal.getNome(),
                    clienteFinal.getEmail(),
                    servicoFinal.getId(),
                    servicoFinal.getNome(),
                    profissionalFinal.getId(),
                    profissionalFinal.getNome(),
                    agendamentoFinal.getData(),
                    agendamentoFinal.getHoraInicio()
            ));
            return mapper.toResponse(agendamentoFinal);
        } catch (Exception e) {
            Map<String, Object> contextoErro = new LinkedHashMap<>();
            contextoErro.put("empresaId", request.empresaId());
            contextoErro.put("clienteId", request.clienteId());
            contextoErro.put("servicoId", request.servicoId());
            contextoErro.put("profissionalId", request.profissionalId());
            log.error("[agendamento-debug] erro ao criar agendamento. erroTipo={} contexto={}", e.getClass().getSimpleName(), contextoErro);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return agendamentoRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarPorEmpresa(Long empresaId, boolean operacional) {
        validarEmpresaAtual(empresaId);
        if (operacional) {
            return agendamentoRepository.findByEmpresaIdOperacional(empresaId, com.minhaempresa.gendaz.shared.enums.StatusCadastro.EXCLUIDO)
                    .stream().map(mapper::toResponse).toList();
        }
        return agendamentoRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarPorData(Long empresaId, LocalDate data) {
        validarEmpresaAtual(empresaId);
        return agendamentoRepository.findByEmpresaIdAndDataOperacional(empresaId, data, com.minhaempresa.gendaz.shared.enums.StatusCadastro.EXCLUIDO)
                .stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarPorCliente(Long clienteId) {
        Long companyId = CompanyContext.requireCompanyId();
        clienteService.buscarEntidadeOperacional(clienteId);
        return agendamentoRepository.findByEmpresaIdAndClienteId(companyId, clienteId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarPorCliente(Long empresaId, Long clienteId) {

        validarEmpresaAtual(empresaId);
        clienteService.buscarEntidadeOperacional(clienteId);
        return agendamentoRepository.findByEmpresaIdAndClienteId(empresaId, clienteId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<String> horariosDisponiveis(Long empresaId, Long profissionalId, Long servicoId, LocalDate data) {
        ServicoEntity servico = servicoService.buscarEntidadeOperacional(servicoId);
        ProfissionalEntity profissional = null;
        boolean empresaSemProfissionaisReais = false;

        if (profissionalId == null) {
            var profissionaisAtivos = profissionalService.listarPorEmpresa(empresaId).stream()
                    .filter(item -> item.status() == com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO)
                    .map(item -> profissionalService.buscarEntidade(item.id()))
                    .filter(java.util.Objects::nonNull)
                    .filter(item -> profissionalService.trabalhaNoDia(item, data))
                    .toList();

            boolean todosSistema = !profissionaisAtivos.isEmpty()
                    && profissionaisAtivos.stream().allMatch(item -> {
                        ProfissionalEntity p = item;
                        return p != null && p.isSistema();
                    });
            if (todosSistema) {
                empresaSemProfissionaisReais = true;
            } else if (!profissionaisAtivos.isEmpty()) {
                profissional = profissionaisAtivos.stream()
                        .filter(p -> !p.isSistema())
                        .findFirst()
                        .orElse(null);
            }
        } else {
            profissional = profissionalService.buscarEntidade(profissionalId);
            if (!profissionalService.trabalhaNoDia(profissional, data)) {
                return List.of();
            }
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
        int intervaloMinutos = Math.max(horarioAtendimentoService.resolverIntervaloMinutos(horario), 1);
        LocalTime horaAtual = horario.getHoraInicio();
        while (horaAtual.isBefore(horario.getHoraFim())) {
            LocalTime inicio = horaAtual;
            int duracao = servico.getDuracaoMinutos() != null ? servico.getDuracaoMinutos() : 30;
            LocalTime fim = inicio.plusMinutes(duracao);

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

            horaAtual = horaAtual.plusMinutes(intervaloMinutos);
        }
        return horarios;
    }

    @Transactional
    public AgendamentoResponse confirmar(Long id) {
        AgendamentoEntity agendamento = carregarOperacionalParaAtualizacao(id, null);
        TransicaoStatusAgendamento.exigirConfirmacao(agendamento.getStatus());
        ProfissionalEntity profissional = profissionalService.buscarEntidadeParaReserva(
                agendamento.getProfissional().getId(), agendamento.getEmpresa().getId());
        agendamento.setProfissional(profissional);
        if (agendamentoRepository.existsByProfissionalIdAndDataAndHoraInicioAndStatus(
                agendamento.getProfissional().getId(), agendamento.getData(), agendamento.getHoraInicio(), StatusAgendamento.CONFIRMADO)) {
            throw new ConflictException("Ja existe agendamento confirmado para este profissional neste horario.");
        }
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        AgendamentoResponse response = mapper.toResponse(agendamentoRepository.save(agendamento));
        try {
            logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Confirmou agendamento de " + agendamento.getCliente().getNome());
        } catch (Exception e) {
            log.warn("[agendamento-debug] falha ao registrar log de confirmacao. erroTipo={}", e.getClass().getSimpleName());
        }
        return response;
    }

    @Transactional
    public AgendamentoResponse cancelar(Long id, Long empresaId) {
        AgendamentoEntity agendamento = carregarParaAtualizacao(id, empresaId);
        TransicaoStatusAgendamento.exigirCancelamentoOperacional(agendamento.getStatus());
        agendamento.setStatus(StatusAgendamento.CANCELADO);
        AgendamentoResponse response = mapper.toResponse(agendamentoRepository.save(agendamento));
        pagamentoService.cancelarPagamentoPendenteDoAgendamento(id, agendamento.getEmpresa().getId());
        try {
            logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Cancelou agendamento de " + agendamento.getCliente().getNome());
        } catch (Exception e) {
            log.warn("[agendamento-debug] falha ao registrar log de cancelamento. erroTipo={}", e.getClass().getSimpleName());
        }
        return response;
    }

    /**
     * Cancelamento self-service (Meu Gendaz). O clienteId deve sempre vir da
     * sessao autenticada, nunca do body. Mesma prova de propriedade do
     * remarcarParaCliente: empresa + cliente validados atomicamente.
     */
    @Transactional
    public AgendamentoResponse cancelarParaCliente(Long id, Long empresaId, Long clienteId) {
        AgendamentoEntity agendamento = carregarDoClienteParaAtualizacao(id, empresaId, clienteId);
        TransicaoStatusAgendamento.exigirCancelamentoCliente(agendamento.getStatus());
        agendamento.setStatus(StatusAgendamento.CANCELADO);
        AgendamentoResponse response = mapper.toResponse(agendamentoRepository.save(agendamento));
        pagamentoService.cancelarPagamentoPendenteDoAgendamento(id, agendamento.getEmpresa().getId());
        try {
            logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Cancelou agendamento de " + agendamento.getCliente().getNome());
        } catch (Exception e) {
            log.warn("[agendamento-debug] falha ao registrar log de cancelamento. erroTipo={}", e.getClass().getSimpleName());
        }
        return response;
    }

    /**
     * Exclusao OPERACIONAL de agendamento (soft delete): remove da Agenda,
     * preserva o banco para Financeiro, Relatorios e auditoria. NUNCA faz
     * DELETE fisico no fluxo normal. Obedece integralmente a maquina de
     * estados ({@code TransicaoStatusAgendamento.exigirExclusao}), ANTES de
     * qualquer leitura de pagamento ou modificacao:
     * - PENDENTE / CONFIRMADO / EM_ATENDIMENTO: status -> CANCELADO +
     *   excluidoAgenda = true; pagamento PENDENTE -> CANCELADO (PAGO intacto,
     *   via regra central).
     * - CANCELADO: idempotente — apenas garante excluidoAgenda = true, sem
     *   ressuscitar pagamento e sem mexer no Caixa.
     * - FINALIZADO: mantem o status (historico nao e falsificado), apenas
     *   excluidoAgenda = true; pagamento PAGO continua PAGO, Caixa intacto.
     * - PAUSADO: bloqueado com erro de negocio, sem tocar em pagamento,
     *   Caixa, movimentacao ou no proprio registro.
     * Excluir nunca e porta alternativa para transicao proibida.
     */
    @Transactional
    public void excluir(Long id, Long empresaId) {
        // Lock 1 (AGENDAMENTO) antes de qualquer leitura: a exclusao valida o
        // estado protegido e so depois toca no pagamento (lock 2, via
        // cancelarPagamentoPendenteDoAgendamento). Nunca o inverso.
        AgendamentoEntity agendamento = carregarParaAtualizacao(id, empresaId);
        TransicaoStatusAgendamento.exigirExclusao(agendamento.getStatus());
        // FINALIZADO aconteceu de verdade: nao vira CANCELADO (nao falsifica
        // o historico); apenas sai da Agenda operacional via excluidoAgenda.
        if (agendamento.getStatus() != StatusAgendamento.FINALIZADO) {
            agendamento.setStatus(StatusAgendamento.CANCELADO);
        }
        agendamento.setExcluidoAgenda(true);
        agendamentoRepository.save(agendamento);
        pagamentoService.cancelarPagamentoPendenteDoAgendamento(id, agendamento.getEmpresa().getId());
        logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Excluiu agendamento de " + agendamento.getCliente().getNome());
    }

    /**
     * Finalizacao de agendamento — unica fonte de verdade do dominio.
     * Usada pelo endpoint individual, pelo Meu Gendaz (via fluxos internos)
     * e pelas operacoes em massa. Regras formais de transicao financeira:
     *
     * - Agendamento ja FINALIZADO nao pode ser finalizado novamente
     *   (bloqueio server-side; nao depende da UI).
     * - Origem permitida: EM_ATENDIMENTO ou PAUSADO (maquina de estados).
     * - PENDENTE -> PAGO registra entrada no Caixa UMA unica vez.
     * - PAGO -> PAGO nao registra novamente (idempotente).
     * - PAGO -> PENDENTE via finalizacao e BLOQUEADO: dinheiro ja registrado
     *   nao pode sumir implicitamente. O desfazimento exige a operacao
     *   financeira explicita (atualizacao de status do pagamento com estorno).
     */
    @Transactional
    public AgendamentoResponse finalizar(Long id, Boolean pagamentoRealizado, MetodoPagamento metodoPagamento, Integer parcelas) {
        // Ordem oficial de locks: 1.AGENDAMENTO -> 2.PAGAMENTO -> 3.EMPRESA/Caixa
        // (via CaixaDespesasService.registrarPagamentoAprovado). O status do
        // agendamento e lido SOMENTE depois do lock (linha abaixo); a decisao
        // da maquina nunca usa estado pre-lock.
        AgendamentoEntity agendamento = carregarOperacionalParaAtualizacao(id, null);
        TransicaoStatusAgendamento.exigirFinalizacao(agendamento.getStatus());
        boolean pago = pagamentoRealizado == null || Boolean.TRUE.equals(pagamentoRealizado);
        PagamentoEntity pagamento = pagamentoRepository
                .findByAgendamentoIdAndEmpresaIdForUpdate(id, agendamento.getEmpresa().getId())
                .orElse(null);
        return aplicarFinalizacao(agendamento, pagamento, pago, metodoPagamento, parcelas);
    }

    /**
     * Finalizacao em massa preservando o estado financeiro ATUAL — a decisao
     * PAGO/PENDENTE acontece AQUI, depois dos locks, nunca a partir de uma
     * leitura stale feita pelo orquestrador do bulk (TOCTOU). Fluxo:
     * LOCK Agendamento -&gt; validar estado -&gt; LOCK Pagamento -&gt; ler
     * status atual -&gt; decidir -&gt; alterar (-&gt; LOCK Empresa so se o
     * Caixa precisar mudar) -&gt; commit.
     *
     * <p>Regras: PAGO permanece PAGO sem novo lancamento no Caixa; PENDENTE
     * permanece PENDENTE sem inventar recebimento; CANCELADO falha com
     * {@code BusinessException} (sem ressuscitar silenciosamente para PAGO ou
     * PENDENTE — a regularizacao pertence a operacao financeira explicita);
     * sem pagamento vinculado, apenas conclui o agendamento (regra atual).
     */
    @Transactional
    public AgendamentoResponse finalizarPreservandoPagamento(Long id, Long empresaId) {
        AgendamentoEntity agendamento = carregarOperacionalParaAtualizacao(id, empresaId);
        TransicaoStatusAgendamento.exigirFinalizacao(agendamento.getStatus());
        PagamentoEntity pagamento = pagamentoRepository
                .findByAgendamentoIdAndEmpresaIdForUpdate(id, agendamento.getEmpresa().getId())
                .orElse(null);
        boolean pago = pagamento != null && pagamento.getStatus() == StatusPagamento.PAGO;
        MetodoPagamento metodo = pago ? pagamento.getMetodoPagamento() : null;
        Integer parcelas = pago ? pagamento.getParcelas() : null;
        return aplicarFinalizacao(agendamento, pagamento, pago, metodo, parcelas);
    }

    /**
     * FONTE UNICA da transicao financeira de finalizacao. Chamadores
     * (finalizar explicito e finalizarPreservandoPagamento) ja adquiriram os
     * locks nesta ordem: 1.AGENDAMENTO, 2.PAGAMENTO. O Caixa (3.EMPRESA) so e
     * tocado quando ha transicao real para PAGO.
     */
    private AgendamentoResponse aplicarFinalizacao(AgendamentoEntity agendamento, PagamentoEntity pagamento,
            boolean pago, MetodoPagamento metodoPagamento, Integer parcelas) {
        if (pagamento != null) {
            StatusPagamento statusAnterior = pagamento.getStatus();
            if (statusAnterior == StatusPagamento.CANCELADO) {
                throw new BusinessException("Pagamento cancelado. Regularize o pagamento pela operacao financeira explicita antes de finalizar.");
            }
            if (!pago && statusAnterior == StatusPagamento.PAGO) {
                throw new BusinessException("Pagamento ja confirmado. Utilize a operacao de estorno/correcao do pagamento em vez de finalizar como nao pago.");
            }
            if (pago) {
                formaPagamentoEmpresaService.validarPagamentoManual(agendamento.getEmpresa().getId(), metodoPagamento, parcelas);
                MetodoPagamento metodo = formaPagamentoEmpresaService.normalizarMetodoManual(metodoPagamento);
                pagamento.setStatus(StatusPagamento.PAGO);
                pagamento.setMetodoPagamento(metodo);
                pagamento.setParcelas(formaPagamentoEmpresaService.normalizarParcelas(metodo, parcelas));
                pagamento.setDataPagamento(LocalDateTime.now(ZoneId.of(appTimezone)));
            } else {
                pagamento.setStatus(StatusPagamento.PENDENTE);
                pagamento.setMetodoPagamento(null);
                pagamento.setParcelas(null);
                pagamento.setDataPagamento(null);
            }
            pagamentoRepository.save(pagamento);
            if (pago && statusAnterior != StatusPagamento.PAGO) {
                caixaDespesasService.registrarPagamentoAprovado(pagamento);
            }
        } else {
            log.warn("[agendamento-debug] finalizar agendamento sem pagamento vinculado. agendamentoId={}", agendamento.getId());
        }
        agendamento.setStatus(StatusAgendamento.FINALIZADO);
        AgendamentoResponse response = mapper.toResponse(agendamentoRepository.save(agendamento));
        try {
            logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Concluiu agendamento de " + agendamento.getCliente().getNome());
        } catch (Exception e) {
            log.warn("[agendamento-debug] falha ao registrar log de finalizacao. erroTipo={}", e.getClass().getSimpleName());
        }
        return response;
    }

    @Transactional
    public AgendamentoResponse iniciar(Long id) {
        AgendamentoEntity agendamento = carregarOperacionalParaAtualizacao(id, null);
        TransicaoStatusAgendamento.exigirInicio(agendamento.getStatus());
        agendamento.setStatus(StatusAgendamento.EM_ATENDIMENTO);
        AgendamentoResponse response = mapper.toResponse(agendamentoRepository.save(agendamento));
        try {
            logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Iniciou atendimento de " + agendamento.getCliente().getNome());
        } catch (Exception e) {
            log.warn("[agendamento-debug] falha ao registrar log de inicio. erroTipo={}", e.getClass().getSimpleName());
        }
        return response;
    }

    @Transactional
    public AgendamentoResponse pausar(Long id) {
        AgendamentoEntity agendamento = carregarOperacionalParaAtualizacao(id, null);
        TransicaoStatusAgendamento.exigirPausa(agendamento.getStatus());
        agendamento.setStatus(StatusAgendamento.PAUSADO);
        AgendamentoResponse response = mapper.toResponse(agendamentoRepository.save(agendamento));
        try {
            logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Pausou atendimento de " + agendamento.getCliente().getNome());
        } catch (Exception e) {
            log.warn("[agendamento-debug] falha ao registrar log de pausa. erroTipo={}", e.getClass().getSimpleName());
        }
        return response;
    }

    /**
     * Retomada de atendimento pausado: PAUSADO -> EM_ATENDIMENTO.
     * Acao explicita; o endpoint generico de edicao nao altera esse status.
     */
    @Transactional
    public AgendamentoResponse retomar(Long id) {
        AgendamentoEntity agendamento = carregarOperacionalParaAtualizacao(id, null);
        TransicaoStatusAgendamento.exigirRetomada(agendamento.getStatus());
        agendamento.setStatus(StatusAgendamento.EM_ATENDIMENTO);
        AgendamentoResponse response = mapper.toResponse(agendamentoRepository.save(agendamento));
        try {
            logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Retomou atendimento de " + agendamento.getCliente().getNome());
        } catch (Exception e) {
            log.warn("[agendamento-debug] falha ao registrar log de retomada. erroTipo={}", e.getClass().getSimpleName());
        }
        return response;
    }

    /**
     * Reabertura de atendimento finalizado por engano: FINALIZADO -> EM_ATENDIMENTO.
     * UNICA saida normal de FINALIZADO. Operacao puramente operacional:
     * NAO altera pagamento, Caixa, dataPagamento, metodoPagamento, parcelas
     * nem movimentacao financeira. Correcao de pagamento pertence ao fluxo
     * explicito de estorno/correcao.
     */
    @Transactional
    public AgendamentoResponse reabrir(Long id) {
        AgendamentoEntity agendamento = carregarOperacionalParaAtualizacao(id, null);
        TransicaoStatusAgendamento.exigirReabertura(agendamento.getStatus());
        agendamento.setStatus(StatusAgendamento.EM_ATENDIMENTO);
        AgendamentoResponse response = mapper.toResponse(agendamentoRepository.save(agendamento));
        try {
            logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Reabriu atendimento de " + agendamento.getCliente().getNome());
        } catch (Exception e) {
            log.warn("[agendamento-debug] falha ao registrar log de reabertura. erroTipo={}", e.getClass().getSimpleName());
        }
        return response;
    }

    @Transactional
    public AgendamentoResponse remarcar(Long id, RemarcarAgendamentoRequest request) {
        AgendamentoEntity agendamento = carregarOperacionalParaAtualizacao(id, null);
        return aplicarRemarcacao(agendamento, request);
    }

    @Transactional
    public AgendamentoResponse remarcar(Long id, RemarcarAgendamentoRequest request, Long empresaId) {
        AgendamentoEntity agendamento = carregarOperacionalParaAtualizacao(id, empresaId);
        return aplicarRemarcacao(agendamento, request);
    }

    /**
     * Remarcacao self-service (Meu Gendaz). O clienteId deve sempre vir da
     * sessao autenticada, nunca do body. Valida empresa E propriedade do
     * agendamento de forma atomica para impedir IDOR/BOLA entre clientes
     * da mesma empresa. Nao vaza o proprietario real em caso de falha.
     */
    @Transactional
    public AgendamentoResponse remarcarParaCliente(Long id, RemarcarAgendamentoRequest request, Long empresaId, Long clienteId) {
        AgendamentoEntity agendamento = carregarDoClienteParaAtualizacao(id, empresaId, clienteId);
        return aplicarRemarcacao(agendamento, request);
    }

    private AgendamentoResponse aplicarRemarcacao(AgendamentoEntity agendamento, RemarcarAgendamentoRequest request) {
        StatusAgendamento destino = TransicaoStatusAgendamento.destinoReagendamento(agendamento.getStatus());
        // O agendamento ja esta travado pelo chamador (ordem: AGENDAMENTO ->
        // PROFISSIONAL). Trava a agenda do profissional DESTINO antes de
        // qualquer verificacao de disponibilidade/conflito.
        ProfissionalEntity profissionalTravado = profissionalService.buscarEntidadeParaReserva(
                agendamento.getProfissional().getId(), agendamento.getEmpresa().getId());
        agendamento.setProfissional(profissionalTravado);
        int duracao = agendamento.getServico().getDuracaoMinutos() != null ? agendamento.getServico().getDuracaoMinutos() : 30;
        LocalTime horaFim = request.horaInicio().plusMinutes(duracao);
        validarProfissionalAgendamento(agendamento.getEmpresa(), agendamento.getProfissional(), request.data());
        validarDataHorario(agendamento.getEmpresa().getId(), request.data(), request.horaInicio(), horaFim);
        validarDiaBloqueado(agendamento.getEmpresa().getId(), agendamento.getProfissional().getId(), request.data());
        agendamento.setData(request.data());
        agendamento.setHoraInicio(request.horaInicio());
        agendamento.setHoraFim(horaFim);
        validarConflitoHorario(agendamento.getProfissional().getId(), request.data(), agendamento.getHoraInicio(), agendamento.getHoraFim(), agendamento.getId());
        agendamento.setStatus(destino);
        AgendamentoResponse response = mapper.toResponse(agendamentoRepository.save(agendamento));
        try {
            logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Reagendou agendamento de " + agendamento.getCliente().getNome());
        } catch (Exception e) {
            log.warn("[agendamento-debug] falha ao registrar log de reagendamento. erroTipo={}", e.getClass().getSimpleName());
        }
        return response;
    }

    /**
     * ORDEM GLOBAL DE LOCKS (oficial): 1.AGENDAMENTO -> 2.PROFISSIONAL ->
     * 3.PAGAMENTO -> 4.EMPRESA/Caixa. Nem todo fluxo precisa de todos, mas
     * nenhum fluxo pode inverter os locks que compartilha. Todo writer deste
     * service carrega o Agendamento COM PESSIMISTIC_WRITE ANTES de ler
     * getStatus(); fluxos de reserva (criar/remarcar/atualizar) travam ainda
     * a agenda do profissional DESTINO ANTES de verificar conflito de
     * intervalo. Nunca inverter (ex.: Profissional antes de Agendamento num
     * fluxo que precisa dos dois) para nao causar deadlock nem decisao sobre
     * estado stale.
     */
    private AgendamentoEntity carregarParaAtualizacao(Long id, Long empresaId) {
        AgendamentoEntity agendamento = agendamentoRepository
                .findByIdAndEmpresaIdForUpdate(id, CompanyContext.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento nao encontrado."));
        validarEmpresa(agendamento, empresaId);
        return agendamento;
    }

    private AgendamentoEntity carregarOperacionalParaAtualizacao(Long id, Long empresaId) {
        AgendamentoEntity agendamento = carregarParaAtualizacao(id, empresaId);
        if (agendamento.getCliente() != null
                && agendamento.getCliente().getStatus()
                        == com.minhaempresa.gendaz.shared.enums.StatusCadastro.EXCLUIDO) {
            throw new BusinessException("Agendamento de cliente excluido não pode ser acessado operacionalmente.");
        }
        return agendamento;
    }

    /**
     * Variante com lock para fluxos self-service (Meu Gendaz): mesma prova
     * de propriedade do buscarParaCliente, porem com PESSIMISTIC_WRITE, para
     * que o cliente tambem decida sobre o estado protegido. O clienteId vem
     * sempre da sessao autenticada, nunca do body.
     */
    private AgendamentoEntity carregarDoClienteParaAtualizacao(Long id, Long empresaId, Long clienteId) {
        AgendamentoEntity agendamento = agendamentoRepository
                .findByIdAndEmpresaIdAndClienteIdForUpdate(id, empresaId, clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento nao encontrado."));
        if (agendamento.getCliente() != null
                && agendamento.getCliente().getStatus()
                        == com.minhaempresa.gendaz.shared.enums.StatusCadastro.EXCLUIDO) {
            throw new BusinessException("Agendamento de cliente excluido não pode ser acessado operacionalmente.");
        }
        return agendamento;
    }

    @Transactional
    public AgendamentoResponse atualizar(Long id, AtualizarAgendamentoRequest request) {
        // Lock do Agendamento ANTES de qualquer leitura: sem ele, um update
        // poderia persistir uma versao stale e desfazer uma finalizacao
        // concorrente (lost update de estado + dinheiro).
        AgendamentoEntity agendamento = carregarOperacionalParaAtualizacao(id, null);
        // Edicao generica nao e porta de escape: apenas transicoes simples
        // autorizadas pela maquina de estados. Acoes especiais usam metodos
        // proprios (iniciar, pausar, retomar, finalizar, reabrir, cancelar).
        TransicaoStatusAgendamento.exigirEdicaoStatus(agendamento.getStatus(), request.status());
        ClienteEntity cliente = clienteService.buscarEntidadeOperacional(request.clienteId());
        ServicoEntity servico = servicoService.buscarEntidadeOperacional(request.servicoId());
        ProfissionalEntity profissional = profissionalService.buscarEntidade(request.profissionalId());
        EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
        // A edicao pode mover a reserva (profissional/data/hora/duracao):
        // trava a agenda do profissional DESTINO (ordem: AGENDAMENTO ->
        // PROFISSIONAL) antes de validar disponibilidade/conflito.
        profissional = profissionalService.buscarEntidadeParaReserva(profissional.getId(), empresa.getId());

        validarProfissionalAgendamento(empresa, profissional, request.data());
        int duracao = servico.getDuracaoMinutos() != null ? servico.getDuracaoMinutos() : 30;
        LocalTime horaFim = request.horaInicio().plusMinutes(duracao);
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

        logAtividadeService.registrar("AGENDAMENTO", agendamento.getId(), "Editou agendamento de " + cliente.getNome());
        AgendamentoResponse response = mapper.toResponse(agendamentoRepository.save(agendamento));
        if (request.status() == StatusAgendamento.CANCELADO) {
            pagamentoService.cancelarPagamentoPendenteDoAgendamento(id, empresa.getId());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public AgendamentoEntity buscarEntidade(Long id) {
        AgendamentoEntity agendamento = agendamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento nao encontrado."));
        validarEmpresaAtual(agendamento.getEmpresa().getId());
        return agendamento;
    }

    @Transactional(readOnly = true)
    public AgendamentoEntity buscarEntidadeOperacional(Long id) {
        AgendamentoEntity agendamento = buscarEntidade(id);
        if (agendamento.getCliente() != null
                && agendamento.getCliente().getStatus()
                        == com.minhaempresa.gendaz.shared.enums.StatusCadastro.EXCLUIDO) {
            throw new BusinessException("Agendamento de cliente excluido não pode ser acessado operacionalmente.");
        }
        return agendamento;
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

    private ProfissionalEntity profissionalParaSemPreferencia(EmpresaEntity empresa, LocalDate data) {
        var profissional = profissionalRepositoryDisponivel(empresa.getId(), data).stream()
                .filter(item -> !item.isSistema())
                .findFirst()
                .orElse(null);
        return profissional != null ? profissional : profissionalService.buscarOuCriarAtendimentoPrincipal(empresa);
    }

    private List<ProfissionalEntity> profissionalRepositoryDisponivel(Long empresaId, LocalDate data) {
        return profissionalService.listarPorEmpresa(empresaId).stream()
                .filter(item -> item.status() == com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO)
                .map(item -> profissionalService.buscarEntidade(item.id()))
                .filter(item -> profissionalService.trabalhaNoDia(item, data))
                .toList();
    }

    private void validarProfissionalAgendamento(EmpresaEntity empresa, ProfissionalEntity profissional, LocalDate data) {
        if (profissional == null || empresa == null || profissional.getEmpresa() == null || !profissional.getEmpresa().getId().equals(empresa.getId())) {
            throw new BusinessException("Profissional nao pertence a empresa informada.");
        }
        if (profissional.getStatus() != com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO) {
            throw new BusinessException("Profissional indisponivel.");
        }
        profissionalService.validarTrabalhoNoDia(profissional, data);
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

    private void criarPagamentoPendente(AgendamentoEntity agendamento, ClienteEntity cliente, EmpresaEntity empresa) {
        BigDecimal valor = agendamento.getValorFinal() != null ? agendamento.getValorFinal() : agendamento.getServico().getValor();
        pagamentoRepository.save(PagamentoEntity.builder()
                .agendamento(agendamento)
                .cliente(cliente)
                .empresa(empresa)
                .valor(valor)
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
        Long companyId = CompanyContext.requireCompanyId();
        if (empresaId == null || !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Agendamento nao encontrado.");
        }
    }
}

