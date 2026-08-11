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
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private static final List<StatusPagamento> STATUS_RECEITA_CONFIRMADA = List.of(
            StatusPagamento.PAGO,
            StatusPagamento.PAYMENT_APPROVED
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
    public DashboardResumoResponse resumo(Long usuarioId) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuario nao encontrado."));
        EmpresaEntity empresa = usuario.getEmpresa();
        if (empresa == null) {
            throw new BusinessException("Usuario sem empresa nao possui resumo do dashboard.");
        }

        Long empresaId = empresa.getId();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioPeriodoDia = hoje.withDayOfMonth(1);
        LocalDateTime inicioPeriodo = inicioPeriodoDia.atStartOfDay();
        LocalDateTime fimPeriodo = hoje.withDayOfMonth(hoje.lengthOfMonth()).atTime(LocalTime.MAX);
        log.info("[dashboard-debug] empresaId={}", empresaId);
        log.info("[dashboard-debug] periodo mensal inicio={} fim={}", inicioPeriodo, fimPeriodo);

        long agendamentosHoje = agendamentoRepository.countByEmpresaIdAndData(empresaId, hoje);
        long conversasAbertas = conversaRepository.countAbertasByEmpresaId(empresaId);
        long clientesCadastrados = clienteRepository.countByEmpresaId(empresaId);
        long servicosAtivos = servicoRepository.countAtivosByEmpresaId(empresaId);

        BigDecimal receitaConfirmada = valorOuZero(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(empresaId, STATUS_RECEITA_CONFIRMADA));
        BigDecimal pendenteCobranca = valorOuZero(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(empresaId, STATUS_PENDENTE));

        List<DashboardAgendamentoItem> proximosAgendamentos = agendamentoRepository
                .findTop5ByEmpresaIdAndStatusInAndDataGreaterThanEqualOrderByDataAscHoraInicioAsc(
                        empresaId,
                        List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO),
                        hoje
                ).stream()
                .map(this::toAgendamentoItem)
                .toList();

        List<DashboardAgendamentoItem> ultimosAgendamentos = agendamentoRepository
                .findTop10ByEmpresaIdOrderByDataDescHoraInicioDesc(empresaId)
                .stream()
                .map(this::toAgendamentoItem)
                .toList();

        List<DashboardItemResumo> servicosMaisAgendados = agendamentoRepository
                .resumoServicosMaisAgendados(empresaId, StatusAgendamento.CANCELADO, PageRequest.of(0, 5))
                .stream()
                .map(this::toItemResumo)
                .toList();

        List<DashboardReceitaDiaItem> receitaPorDia = pagamentosPorDia(empresaId, inicioPeriodo, fimPeriodo);
        log.info("[dashboard-debug] receitaConfirmada={}", receitaConfirmada);
        log.info("[dashboard-debug] totalPagamentosConfirmados={}", receitaPorDia.stream().map(DashboardReceitaDiaItem::valor).reduce(BigDecimal.ZERO, BigDecimal::add));
        log.info("[dashboard-debug] receitaPorDia={}", receitaPorDia);

        List<DashboardPagamentoItem> pagamentosPendentes = pagamentoRepository
                .findTop5ByEmpresaIdAndStatusOrderByIdDesc(empresaId, StatusPagamento.PENDENTE)
                .stream()
                .map(this::toPagamentoItem)
                .toList();

        return new DashboardResumoResponse(
                agendamentosHoje,
                conversasAbertas,
                clientesCadastrados,
                servicosAtivos,
                receitaConfirmada,
                pendenteCobranca,
                proximosAgendamentos,
                ultimosAgendamentos,
                servicosMaisAgendados,
                receitaPorDia,
                pagamentosPendentes,
                empresa.getNomeFantasia(),
                construirPrimeirosPassos(empresa)
        );
    }

    private PrimeirosPassosResponse construirPrimeirosPassos(EmpresaEntity empresa) {
        Long empresaId = empresa.getId();
        boolean temServico = servicoRepository.countAtivosByEmpresaId(empresaId) > 0;
        boolean temProfissional = profissionalRepository.countAtivosByEmpresaId(empresaId) > 0;
        boolean temLinkAgendamento = empresa.getAgendamentoSlug() != null && !empresa.getAgendamentoSlug().isBlank();
        boolean planoPro = assinaturaService.buscarAtualPorEmpresa(empresaId)
                .map(AssinaturaEntity::getPlano)
                .map(plano -> "PRO".equalsIgnoreCase(plano.getNome()))
                .orElse(false);

        List<PrimeiroPassoItem> etapas = new ArrayList<>();
        etapas.add(new PrimeiroPassoItem("servico", "Cadastrar um servico", "Crie seu primeiro servico", "/sistema/servicos", temServico));
        etapas.add(new PrimeiroPassoItem("horario-servico", "Definir horarios do servico", "Configure a disponibilidade", "/sistema/configuracoes", temServico));
        if (planoPro) {
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

    private DashboardPagamentoItem toPagamentoItem(PagamentoEntity pagamento) {
        return new DashboardPagamentoItem(
                pagamento.getId(),
                pagamento.getCliente() != null ? pagamento.getCliente().getNome() : "",
                pagamento.getMetodoPagamento() != null ? pagamento.getMetodoPagamento().name() : "",
                valorOuZero(pagamento.getValor()),
                pagamento.getStatus() != null ? pagamento.getStatus().name() : ""
        );
    }

    private List<DashboardReceitaDiaItem> pagamentosPorDia(Long empresaId, LocalDateTime inicio, LocalDateTime fim) {
        Map<LocalDate, BigDecimal> receitaPorDia = pagamentoRepository.resumoReceitaPorDia(empresaId, STATUS_RECEITA_CONFIRMADA, inicio, fim)
                .stream()
                .filter(row -> row.length >= 2 && row[0] != null)
                .collect(Collectors.toMap(
                        row -> toLocalDate(row[0]),
                        row -> valorNumerico(row[1]),
                        BigDecimal::add
                ));

        List<DashboardReceitaDiaItem> resultado = new ArrayList<>();
        LocalDate dataInicial = inicio.toLocalDate();
        int diasDoMes = inicio.toLocalDate().lengthOfMonth();
        for (int i = 0; i < diasDoMes; i++) {
            LocalDate data = dataInicial.plusDays(i);
            resultado.add(new DashboardReceitaDiaItem(
                    data.toString(),
                    data.format(DATA_LABEL),
                    receitaPorDia.getOrDefault(data, BigDecimal.ZERO)
            ));
        }
        return resultado;
    }

    private LocalDate toLocalDate(Object valor) {
        if (valor instanceof LocalDate localDate) {
            return localDate;
        }
        if (valor instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(valor));
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

