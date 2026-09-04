package com.minhaempresa.gendaz.dashboard.service;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.conversa.repository.ConversaRepository;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.DashboardAgendamentoItem;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.DashboardItemResumo;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.DashboardPagamentoItem;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.DashboardReceitaDiaItem;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.DashboardResumoResponse;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.PrimeiroPassoItem;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.PrimeirosPassosResponse;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.pagamento.service.ReceitaCompetenciaHelper;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.shared.enums.TimezoneEnum;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
    private static final DateTimeFormatter DATA_LABEL = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"));
    // Receita operacional confirmada: SOMENTE PAGO (dinheiro efetivamente recebido).
    // PAYMENT_APPROVED pertence ao fluxo de plano/assinatura/Stripe/Admin e
    // nao pode inflar o Dashboard operacional (ver PagamentoService).
    private static final List<StatusPagamento> STATUS_RECEITA_CONFIRMADA = List.of(
            StatusPagamento.PAGO
    );
    private static final List<StatusPagamento> STATUS_PENDENTE = List.of(
            StatusPagamento.PENDENTE,
            StatusPagamento.PAYMENT_PENDING
    );

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AssinaturaService assinaturaService;
    private final ConversaRepository conversaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;

    @Transactional(readOnly = true)
    public PrimeirosPassosResponse primeirosPassos(Long usuarioId) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuario nao encontrado."));
        if (usuario.getEmpresa() == null) {
            throw new BusinessException("Usuario sem empresa nao possui primeiros passos.");
        }
        return construirPrimeirosPassos(usuario.getEmpresa());
    }

    @Transactional(readOnly = true)
    public DashboardResumoResponse resumo(Long usuarioId, Long empresaIdParam, Integer mesParam, Integer anoParam) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuario nao encontrado."));
        EmpresaEntity empresa = usuario.getEmpresa();
        if (empresa == null) {
            throw new BusinessException("Usuario sem empresa nao possui resumo do dashboard.");
        }
        if (empresaIdParam != null && !empresa.getId().equals(empresaIdParam)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }

        Long empresaId = empresa.getId();
        ZoneId zoneId = resolverZoneId(empresa.getTimezone());
        LocalDate hoje = LocalDate.now(zoneId);
        int mes = mesParam != null ? mesParam : hoje.getMonthValue();
        int ano = anoParam != null ? anoParam : hoje.getYear();
        validarPeriodo(mes, ano);

        LocalDate inicioPeriodoDia = LocalDate.of(ano, mes, 1);
        LocalDate fimPeriodoDia = inicioPeriodoDia.withDayOfMonth(inicioPeriodoDia.lengthOfMonth());
        boolean mesAtual = inicioPeriodoDia.equals(hoje.withDayOfMonth(1));
        LocalDate fimReferencia = mesAtual ? hoje : fimPeriodoDia;

        log.info("[dashboard-debug] empresaId={}", empresaId);
        log.info("[dashboard-debug] periodo mes={} ano={} inicio={} fim={} mesAtual={}", mes, ano, inicioPeriodoDia, fimReferencia, mesAtual);

        long agendamentosHoje = agendamentoRepository.countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(empresaId, hoje, StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO);
        long conversasAbertas = conversaRepository.countAbertasByEmpresaId(empresaId);
        long clientesCadastrados = clienteRepository.countByEmpresaIdAndStatusNot(empresaId, StatusCadastro.EXCLUIDO);
        long servicosAtivos = servicoRepository.countAtivosByEmpresaId(empresaId);

        List<PagamentoDtos.PagamentoResponse> receitasDoMes = pagamentosConfirmadosNoPeriodo(empresaId, inicioPeriodoDia, fimReferencia);
        BigDecimal receitaConfirmada = receitasDoMes.stream()
                .map(PagamentoDtos.PagamentoResponse::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long pendentesPagamento = pagamentoRepository
                .countByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCancelado(
                        empresaId, STATUS_PENDENTE, StatusCadastro.EXCLUIDO, StatusAgendamento.CANCELADO);
        // Valor monetario agregado: historico. Nao pode depender do status ATUAL do cliente.
        BigDecimal pendenteCobranca = valorOuZero(pagamentoRepository
                .somarValorByEmpresaIdAndStatusInAgendamentoNaoCancelado(
                        empresaId, STATUS_PENDENTE, StatusAgendamento.CANCELADO));

        List<DashboardAgendamentoItem> proximosAgendamentos = agendamentoRepository
                .findTop5ByEmpresaIdAndStatusInAndDataGreaterThanEqualAndClienteStatusNotOrderByDataAscHoraInicioAsc(
                        empresaId,
                        List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO),
                        hoje,
                        StatusCadastro.EXCLUIDO
                ).stream()
                .map(this::toAgendamentoItem)
                .toList();

        List<DashboardAgendamentoItem> ultimosAgendamentos = agendamentoRepository
                .findTop10ByEmpresaIdAndClienteStatusNotOrderByDataDescHoraInicioDesc(empresaId, StatusCadastro.EXCLUIDO)
                .stream()
                .map(this::toAgendamentoItem)
                .toList();

        var rowsServicos = agendamentoRepository.resumoServicosMaisAgendados(empresaId, StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO, PageRequest.of(0, 5));
        List<DashboardItemResumo> servicosMaisAgendados = (rowsServicos == null ? List.<Object[]>of() : rowsServicos)
                .stream()
                .map(this::toItemResumo)
                .toList();

        var rowsProfissionais = agendamentoRepository.resumoProfissionaisMaisAgendados(empresaId, StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO, PageRequest.of(0, 5));
        List<DashboardItemResumo> profissionaisMaisAgendados = (rowsProfissionais == null ? List.<Object[]>of() : rowsProfissionais)
                .stream()
                .map(this::toItemResumo)
                .toList();

        List<DashboardReceitaDiaItem> receitaPorDia = montarReceitaPorDia(inicioPeriodoDia, fimReferencia, receitasDoMes);

        log.info("[dashboard-debug] receitaConfirmada={}", receitaConfirmada);
        log.info("[dashboard-debug] totalPagamentosConfirmados={}", receitaPorDia.stream().map(DashboardReceitaDiaItem::valor).reduce(BigDecimal.ZERO, BigDecimal::add));
        log.info("[dashboard-debug] receitaPorDia={}", receitaPorDia);

        List<DashboardPagamentoItem> pagamentosPendentes = pagamentoRepository
                .findByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCanceladoOrderByIdDesc(
                        empresaId, STATUS_PENDENTE, StatusCadastro.EXCLUIDO, StatusAgendamento.CANCELADO)
                .stream()
                .limit(5)
                .map(this::toPagamentoItem)
                .toList();

        return new DashboardResumoResponse(
                agendamentosHoje,
                pendentesPagamento,
                conversasAbertas,
                clientesCadastrados,
                servicosAtivos,
                receitaConfirmada,
                pendenteCobranca,
                proximosAgendamentos,
                ultimosAgendamentos,
                servicosMaisAgendados,
                profissionaisMaisAgendados,
                receitaPorDia,
                pagamentosPendentes,
                empresa.getNomeFantasia(),
                construirPrimeirosPassos(empresa)
        );
    }

    private void validarPeriodo(int mes, int ano) {
        if (mes < 1 || mes > 12) {
            throw new BusinessException("Mes invalido. Informe um valor entre 1 e 12.");
        }
        if (ano < 2000 || ano > 2100) {
            throw new BusinessException("Ano invalido.");
        }
    }

    private ZoneId resolverZoneId(String timezone) {
        String valor = timezone == null || timezone.isBlank()
                ? TimezoneEnum.AMERICA_SAO_PAULO.getValue()
                : timezone;
        return ZoneId.of(valor);
    }

    private List<PagamentoDtos.PagamentoResponse> pagamentosConfirmadosNoPeriodo(Long empresaId, LocalDate inicio, LocalDate fim) {
        return pagamentoRepository.findByEmpresaIdForFinanceiro(empresaId).stream()
                .flatMap(p -> ReceitaCompetenciaHelper.expandirParcelasVirtuais(p).stream())
                .filter(p -> p.status() != null && STATUS_RECEITA_CONFIRMADA.contains(p.status()))
                // Receita historica NAO depende do status ATUAL do cliente (ATIVO/INATIVO/EXCLUIDO).
                // Dinheiro recebido no passado continua existindo mesmo apos a exclusao do cliente.
                .filter(p -> p.dataPagamento() != null)
                .filter(p -> {
                    LocalDate dataCompetencia = p.dataPagamento().toLocalDate();
                    return !dataCompetencia.isBefore(inicio) && !dataCompetencia.isAfter(fim);
                })
                .toList();
    }

    private List<DashboardReceitaDiaItem> montarReceitaPorDia(LocalDate inicio, LocalDate fim, List<PagamentoDtos.PagamentoResponse> receitas) {
        Map<LocalDate, BigDecimal> receitaPorDia = receitas.stream()
                .filter(p -> p.dataPagamento() != null)
                .collect(Collectors.toMap(
                        p -> p.dataPagamento().toLocalDate(),
                        p -> valorOuZero(p.valor()),
                        BigDecimal::add
                ));

        List<DashboardReceitaDiaItem> resultado = new ArrayList<>();
        LocalDate data = inicio;
        while (!data.isAfter(fim)) {
            resultado.add(new DashboardReceitaDiaItem(
                    data.toString(),
                    data.format(DATA_LABEL),
                    receitaPorDia.getOrDefault(data, BigDecimal.ZERO)
            ));
            data = data.plusDays(1);
        }
        return resultado;
    }

    private PrimeirosPassosResponse construirPrimeirosPassos(EmpresaEntity empresa) {
        Long empresaId = empresa.getId();
        boolean temServico = servicoRepository.countAtivosByEmpresaId(empresaId) > 0;
        boolean temProfissional = profissionalRepository.countAtivosByEmpresaId(empresaId) > 0;
        boolean temLinkAgendamento = empresa.getAgendamentoSlug() != null && !empresa.getAgendamentoSlug().isBlank();
        boolean planoComProfissionais = assinaturaService.buscarAtualPorEmpresa(empresaId)
                .map(AssinaturaEntity::getPlano)
                .map(plano -> "PRO".equalsIgnoreCase(plano.getNome()) || "BASICO".equalsIgnoreCase(plano.getNome())
                        || "PLUS".equalsIgnoreCase(plano.getNome()) || "ENTERPRISE".equalsIgnoreCase(plano.getNome()))
                .orElse(false);

        List<PrimeiroPassoItem> etapas = new ArrayList<>();
        etapas.add(new PrimeiroPassoItem("servico", "Cadastrar um servico", "Crie seu primeiro servico", "/sistema/servicos", temServico));
        etapas.add(new PrimeiroPassoItem("horario-servico", "Definir horarios do servico", "Configure a disponibilidade", "/sistema/configuracoes", temServico));
        if (planoComProfissionais) {
            etapas.add(new PrimeiroPassoItem("profissional", "Cadastrar um profissional", "Adicione quem realiza os servicos", "/sistema/profissionais", temProfissional));
            etapas.add(new PrimeiroPassoItem("horario-profissional", "Definir horarios do profissional", "Configure a agenda de trabalho", "/sistema/configuracoes", temProfissional));
        }
        etapas.add(new PrimeiroPassoItem("link-agendamento", "Compartilhar link de agendamento", "Copie o link publico da empresa", "/sistema/configuracoes", temLinkAgendamento));
        int concluidos = (int) etapas.stream().filter(PrimeiroPassoItem::concluido).count();
        return new PrimeirosPassosResponse(concluidos, etapas.size(), etapas);
    }

    private DashboardAgendamentoItem toAgendamentoItem(AgendamentoEntity agendamento) {
        return new DashboardAgendamentoItem(
                agendamento.getId(),
                agendamento.getData() != null ? agendamento.getData().toString() : null,
                horaCurta(agendamento.getHoraInicio()),
                horaCurta(agendamento.getHoraFim()),
                agendamento.getCliente() != null ? agendamento.getCliente().getNome() : "",
                agendamento.getServico() != null ? agendamento.getServico().getNome() : "",
                agendamento.getProfissional() != null ? agendamento.getProfissional().getNome() : "",
                agendamento.getStatus() != null ? agendamento.getStatus().name() : ""
        );
    }

    private DashboardItemResumo toItemResumo(Object[] row) {
        String nome = row.length > 1 && row[1] != null ? String.valueOf(row[1]) : "";
        long quantidade = row.length > 2 && row[2] != null ? ((Number) row[2]).longValue() : 0L;
        BigDecimal valor = row.length > 3 && row[3] != null ? valorNumerico(row[3]) : BigDecimal.ZERO;
        return new DashboardItemResumo(nome, quantidade, valor);
    }

    private DashboardPagamentoItem toPagamentoItem(com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoResponse pagamento) {
        return new DashboardPagamentoItem(
                pagamento.id(),
                pagamento.clienteNome() != null ? pagamento.clienteNome() : "",
                pagamento.metodoPagamento() != null ? pagamento.metodoPagamento().name() : "",
                valorOuZero(pagamento.valor()),
                pagamento.status() != null ? pagamento.status().name() : ""
        );
    }


    private BigDecimal valorOuZero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private BigDecimal valorNumerico(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        if (valor instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (valor instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(valor));
    }

    private String horaCurta(LocalTime horario) {
        return horario == null ? null : horario.toString().substring(0, 5);
    }
}

