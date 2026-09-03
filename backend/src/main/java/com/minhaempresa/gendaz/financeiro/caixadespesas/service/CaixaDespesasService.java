package com.minhaempresa.gendaz.financeiro.caixadespesas.service;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.financeiro.caixadespesas.dto.CaixaDespesasDtos.AdicionarCaixaDespesasRequest;
import com.minhaempresa.gendaz.financeiro.caixadespesas.dto.CaixaDespesasDtos.CaixaDespesasTotaisResponse;
import com.minhaempresa.gendaz.financeiro.caixadespesas.dto.CaixaDespesasDtos.HistoricoItemResponse;
import com.minhaempresa.gendaz.financeiro.caixadespesas.dto.CaixaDespesasDtos.HistoricoResponse;
import com.minhaempresa.gendaz.financeiro.caixadespesas.entity.CaixaDespesasLogEntity;
import com.minhaempresa.gendaz.financeiro.caixadespesas.enums.TipoCaixaDespesasLog;
import com.minhaempresa.gendaz.financeiro.caixadespesas.repository.CaixaDespesasLogRepository;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CaixaDespesasService {

    private static final int TAMANHO_PAGINA = 10;

    private final CaixaDespesasLogRepository logRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AssinaturaService assinaturaService;
    private final LogAtividadeService logAtividadeService;

    private void exigirPlanoPro(Long empresaId) {
        if (!assinaturaService.isPlanoComRecursosAvancados(empresaId)) {
            throw new BusinessException("Recurso disponivel apenas no plano PRO.");
        }
    }

    private void validarValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("O valor deve ser maior que zero.");
        }
    }

    private EmpresaEntity carregarEmpresa(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
    }

    /**
     * Carga com lock pessimista para movimentacoes financeiras. Serializa
     * atualizacoes concorrentes de caixaTotal/despesasTotal da mesma empresa,
     * evitando perda de update quando duas confirmacoes disputam o saldo.
     */
    private EmpresaEntity carregarEmpresaComLock(Long empresaId) {
        return empresaRepository.findByIdWithLock(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
    }

    private UsuarioEntity carregarUsuario(Long usuarioId) {
        return usuarioId == null ? null : usuarioRepository.findById(usuarioId).orElse(null);
    }

    @Transactional
    public CaixaDespesasTotaisResponse adicionarCaixaManual(Long empresaId, BigDecimal valor, String obs, Long usuarioId) {
        exigirPlanoPro(empresaId);
        validarValor(valor);
        EmpresaEntity empresa = carregarEmpresa(empresaId);
        empresa.setCaixaTotal(empresa.getCaixaTotal().add(valor));
        empresaRepository.save(empresa);
        UsuarioEntity usuario = carregarUsuario(usuarioId);
        String nome = usuario != null ? usuario.getNome() : "Usuario";
        registrarLog(empresa, TipoCaixaDespesasLog.ADICAO_MANUAL_CAIXA, valor, nome + " adicionou", obs, usuario, null);
        logAtividadeService.registrar("CAIXA_DESPESA", empresaId, "Registrou entrada de caixa de R$ " + valor.toPlainString());
        return new CaixaDespesasTotaisResponse(empresa.getCaixaTotal(), empresa.getDespesasTotal());
    }

    @Transactional
    public CaixaDespesasTotaisResponse adicionarDespesasManual(Long empresaId, BigDecimal valor, String obs, Long usuarioId) {
        exigirPlanoPro(empresaId);
        validarValor(valor);
        EmpresaEntity empresa = carregarEmpresa(empresaId);
        empresa.setDespesasTotal(empresa.getDespesasTotal().add(valor));
        empresaRepository.save(empresa);
        UsuarioEntity usuario = carregarUsuario(usuarioId);
        String nome = usuario != null ? usuario.getNome() : "Usuario";
        registrarLog(empresa, TipoCaixaDespesasLog.ADICAO_MANUAL_DESPESAS, valor, nome + " adicionou", obs, usuario, null);
        logAtividadeService.registrar("CAIXA_DESPESA", empresaId, "Registrou despesa de R$ " + valor.toPlainString());
        return new CaixaDespesasTotaisResponse(empresa.getCaixaTotal(), empresa.getDespesasTotal());
    }

    @Transactional
    public CaixaDespesasTotaisResponse removerCaixaManual(Long empresaId, Long logId, Long usuarioId) {
        exigirPlanoPro(empresaId);
        CaixaDespesasLogEntity log = logRepository.findByIdAndBusinessId(logId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro nao encontrado."));
        if (log.getTipo() != TipoCaixaDespesasLog.ADICAO_MANUAL_CAIXA) {
            throw new BusinessException("Apenas adicoes manuais de caixa podem ser removidas.");
        }
        EmpresaEntity empresa = log.getBusiness();
        BigDecimal subtrair = log.getValor();
        empresa.setCaixaTotal(empresa.getCaixaTotal().subtract(subtrair));
        empresaRepository.save(empresa);
        UsuarioEntity usuario = carregarUsuario(usuarioId);
        String nome = usuario != null ? usuario.getNome() : "Usuario";
        registrarLog(empresa, TipoCaixaDespesasLog.REMOCAO_MANUAL_CAIXA, subtrair, nome + " removeu", null, usuario, null);
        logAtividadeService.registrar("CAIXA_DESPESA", empresaId, "Removeu entrada de caixa de R$ " + subtrair.toPlainString());
        return new CaixaDespesasTotaisResponse(empresa.getCaixaTotal(), empresa.getDespesasTotal());
    }

    @Transactional
    public CaixaDespesasTotaisResponse removerDespesasManual(Long empresaId, Long logId, Long usuarioId) {
        exigirPlanoPro(empresaId);
        CaixaDespesasLogEntity log = logRepository.findByIdAndBusinessId(logId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro nao encontrado."));
        if (log.getTipo() != TipoCaixaDespesasLog.ADICAO_MANUAL_DESPESAS) {
            throw new BusinessException("Apenas adicoes manuais de despesas podem ser removidas.");
        }
        EmpresaEntity empresa = log.getBusiness();
        BigDecimal subtrair = log.getValor();
        empresa.setDespesasTotal(empresa.getDespesasTotal().subtract(subtrair));
        empresaRepository.save(empresa);
        UsuarioEntity usuario = carregarUsuario(usuarioId);
        String nome = usuario != null ? usuario.getNome() : "Usuario";
        registrarLog(empresa, TipoCaixaDespesasLog.REMOCAO_MANUAL_DESPESAS, subtrair, nome + " removeu", null, usuario, null);
        logAtividadeService.registrar("CAIXA_DESPESA", empresaId, "Removeu despesa de R$ " + subtrair.toPlainString());
        return new CaixaDespesasTotaisResponse(empresa.getCaixaTotal(), empresa.getDespesasTotal());
    }

    @Transactional
    public CaixaDespesasTotaisResponse removerValorCaixaManual(Long empresaId, BigDecimal valor, String obs, Long usuarioId) {
        exigirPlanoPro(empresaId);
        validarValor(valor);
        EmpresaEntity empresa = carregarEmpresa(empresaId);
        if (valor.compareTo(empresa.getCaixaTotal()) > 0) {
            throw new BusinessException("O valor não pode ser maior que o total do caixa.");
        }
        empresa.setCaixaTotal(empresa.getCaixaTotal().subtract(valor));
        empresaRepository.save(empresa);
        UsuarioEntity usuario = carregarUsuario(usuarioId);
        String nome = usuario != null ? usuario.getNome() : "Usuario";
        registrarLog(empresa, TipoCaixaDespesasLog.REMOCAO_MANUAL_CAIXA, valor, nome + " removeu", obs, usuario, null);
        logAtividadeService.registrar("CAIXA_DESPESA", empresaId, "Removeu valor do caixa de R$ " + valor.toPlainString());
        return new CaixaDespesasTotaisResponse(empresa.getCaixaTotal(), empresa.getDespesasTotal());
    }

    @Transactional
    public CaixaDespesasTotaisResponse removerValorDespesasManual(Long empresaId, BigDecimal valor, String obs, Long usuarioId) {
        exigirPlanoPro(empresaId);
        validarValor(valor);
        EmpresaEntity empresa = carregarEmpresa(empresaId);
        if (valor.compareTo(empresa.getDespesasTotal()) > 0) {
            throw new BusinessException("O valor não pode ser maior que o total de despesas.");
        }
        empresa.setDespesasTotal(empresa.getDespesasTotal().subtract(valor));
        empresaRepository.save(empresa);
        UsuarioEntity usuario = carregarUsuario(usuarioId);
        String nome = usuario != null ? usuario.getNome() : "Usuario";
        registrarLog(empresa, TipoCaixaDespesasLog.REMOCAO_MANUAL_DESPESAS, valor, nome + " removeu", obs, usuario, null);
        logAtividadeService.registrar("CAIXA_DESPESA", empresaId, "Removeu valor de despesas de R$ " + valor.toPlainString());
        return new CaixaDespesasTotaisResponse(empresa.getCaixaTotal(), empresa.getDespesasTotal());
    }

    @Transactional(readOnly = true)
    public CaixaDespesasTotaisResponse buscarTotais(Long empresaId) {
        EmpresaEntity empresa = carregarEmpresa(empresaId);
        return new CaixaDespesasTotaisResponse(empresa.getCaixaTotal(), empresa.getDespesasTotal());
    }

    @Transactional(readOnly = true)
    public HistoricoResponse listarHistorico(Long empresaId, int pagina, int tamanho) {
        int size = tamanho > 0 ? tamanho : TAMANHO_PAGINA;
        Pageable pageable = PageRequest.of(Math.max(pagina - 1, 0), size, Sort.by(Sort.Direction.DESC, "criadoEm"));
        Page<CaixaDespesasLogEntity> page = logRepository.findByBusinessIdOrderByCriadoEmDesc(empresaId, pageable);
        List<HistoricoItemResponse> itens = page.getContent().stream().map(this::toItem).toList();
        return new HistoricoResponse(itens, page.getTotalElements(), pagina, page.getTotalPages(), size);
    }

    @Transactional
    public void registrarPagamentoAprovado(PagamentoEntity pagamento) {
        EmpresaEntity empresa = carregarEmpresaComLock(pagamento.getEmpresa().getId());
        if (!assinaturaService.isPlanoComRecursosAvancados(empresa.getId())) {
            return;
        }
        BigDecimal valor = pagamento.getValor();
        empresa.setCaixaTotal(empresa.getCaixaTotal().add(valor));
        empresaRepository.save(empresa);
        String descricao = buildDescricaoPagamento(pagamento);
        registrarLog(empresa, TipoCaixaDespesasLog.PAGAMENTO_APROVADO, valor, descricao, null, null, pagamento.getAgendamento());
        logAtividadeService.registrar("CAIXA_DESPESA", empresa.getId(), "Registrou entrada de caixa por pagamento aprovado de R$ " + (valor != null ? valor.toPlainString() : "0"));
    }

    @Transactional
    public void registrarPagamentoRemovido(PagamentoEntity pagamento, Long usuarioId) {
        EmpresaEntity empresa = carregarEmpresaComLock(pagamento.getEmpresa().getId());
        if (!assinaturaService.isPlanoComRecursosAvancados(empresa.getId())) {
            return;
        }
        BigDecimal valor = pagamento.getValor();
        empresa.setCaixaTotal(empresa.getCaixaTotal().subtract(valor));
        empresaRepository.save(empresa);
        UsuarioEntity usuario = carregarUsuario(usuarioId);
        String nome = usuario != null ? usuario.getNome() : "Usuario";
        registrarLog(empresa, TipoCaixaDespesasLog.PAGAMENTO_REMOVIDO, valor, null, null, usuario, pagamento.getAgendamento());
        logAtividadeService.registrar("CAIXA_DESPESA", empresa.getId(), "Removeu entrada de caixa por pagamento removido de R$ " + (valor != null ? valor.toPlainString() : "0"));
    }

    @Transactional
    public void registrarPagamentoCancelado(PagamentoEntity pagamento, Long usuarioId, StatusPagamento statusAnterior) {
        EmpresaEntity empresa = carregarEmpresaComLock(pagamento.getEmpresa().getId());
        if (!assinaturaService.isPlanoComRecursosAvancados(empresa.getId())) {
            return;
        }
        BigDecimal valor = pagamento.getValor();
        if (statusAnterior == StatusPagamento.PAGO) {
            empresa.setCaixaTotal(empresa.getCaixaTotal().subtract(valor));
            empresaRepository.save(empresa);
        }
        UsuarioEntity usuario = carregarUsuario(usuarioId);
        String nome = usuario != null ? usuario.getNome() : "Usuario";
        registrarLog(empresa, TipoCaixaDespesasLog.PAGAMENTO_CANCELADO, valor, null, null, usuario, pagamento.getAgendamento());
        logAtividadeService.registrar("CAIXA_DESPESA", empresa.getId(), "Registrou cancelamento de pagamento no caixa de R$ " + (valor != null ? valor.toPlainString() : "0"));
    }

    private String buildDescricaoPagamento(PagamentoEntity pagamento) {
        AgendamentoEntity ag = pagamento.getAgendamento();
        String cliente = pagamento.getCliente() != null ? pagamento.getCliente().getNome() : "Cliente";
        String servico = (ag != null && ag.getServico() != null) ? ag.getServico().getNome() : "Servico";
        String data = (ag != null && ag.getData() != null)
                ? ag.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "";
        return "Agendamento - " + cliente + " - " + servico + " - " + data;
    }

    private HistoricoItemResponse toItem(CaixaDespesasLogEntity log) {
        boolean positivo = switch (log.getTipo()) {
            case PAGAMENTO_APROVADO, ADICAO_MANUAL_CAIXA, REMOCAO_MANUAL_DESPESAS -> true;
            case REMOCAO_MANUAL_CAIXA, ADICAO_MANUAL_DESPESAS, PAGAMENTO_CANCELADO, PAGAMENTO_REMOVIDO -> false;
            default -> false;
        };
        String categoria = switch (log.getTipo()) {
            case PAGAMENTO_APROVADO, PAGAMENTO_REMOVIDO, PAGAMENTO_CANCELADO, ADICAO_MANUAL_CAIXA, REMOCAO_MANUAL_CAIXA -> "CAIXA";
            case ADICAO_MANUAL_DESPESAS, REMOCAO_MANUAL_DESPESAS -> "DESPESAS";
        };

        String descricao = switch (log.getTipo()) {
            case PAGAMENTO_APROVADO -> buildPagamentoHistorico(log, "Aprovado");
            case PAGAMENTO_CANCELADO -> buildPagamentoHistorico(log, "Pendente");
            case PAGAMENTO_REMOVIDO -> buildPagamentoHistorico(log, "Removido");
            default -> buildManualHistorico(log, categoria, positivo);
        };

        String usuarioNome = log.getUsuario() != null ? log.getUsuario().getNome() : null;
        return new HistoricoItemResponse(
                log.getId(),
                log.getTipo(),
                categoria,
                descricao,
                log.getValor(),
                positivo,
                log.getObs(),
                log.getCriadoEm(),
                usuarioNome
        );
    }

    private String buildPagamentoHistorico(CaixaDespesasLogEntity log, String status) {
        String cliente = "Cliente";
        String servico = "Servico";
        if (log.getAgendamento() != null) {
            if (log.getAgendamento().getCliente() != null) {
                cliente = log.getAgendamento().getCliente().getNome();
            }
            if (log.getAgendamento().getServico() != null) {
                servico = log.getAgendamento().getServico().getNome();
            }
        }
        return "CAIXA - " + cliente + " - " + servico + " - " + status;
    }

    private String buildManualHistorico(CaixaDespesasLogEntity log, String categoria, boolean positivo) {
        String nome = log.getUsuario() != null ? log.getUsuario().getNome() : "Usuario";
        String acao = positivo ? "Adicionou" : "Removeu";
        return categoria + " - " + nome + " - " + acao;
    }

    private void registrarLog(EmpresaEntity empresa, TipoCaixaDespesasLog tipo, BigDecimal valor, String descricao,
                              String obs, UsuarioEntity usuario, AgendamentoEntity agendamento) {
        CaixaDespesasLogEntity log = CaixaDespesasLogEntity.builder()
                .business(empresa)
                .tipo(tipo)
                .valor(valor)
                .descricao(descricao)
                .obs(obs)
                .usuario(usuario)
                .agendamento(agendamento)
                .build();
        logRepository.save(log);
    }
}
