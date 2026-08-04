package com.minhaempresa.agendapro.promocao.service;

import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.crm.dto.CrmDtos.CrmClienteResponse;
import com.minhaempresa.agendapro.crm.service.CrmService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.email.ResendEmailService;
import com.minhaempresa.agendapro.promocao.dto.PromocaoDtos.*;
import com.minhaempresa.agendapro.promocao.entity.*;
import com.minhaempresa.agendapro.promocao.enums.TipoPromocao;
import com.minhaempresa.agendapro.promocao.repository.*;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import com.minhaempresa.agendapro.servico.repository.ServicoRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromocaoService {
    private final PromocaoRepository promocaoRepository;
    private final PromocaoUsoRepository promocaoUsoRepository;
    private final PromocaoNotificacaoRepository promocaoNotificacaoRepository;
    private final ClienteRepository clienteRepository;
    private final CrmService crmService;
    private final ServicoRepository servicoRepository;
    private final EmpresaRepository empresaRepository;
    private final ResendEmailService resendEmailService;

    @Transactional(readOnly = true)
    public List<PromocaoResponse> listar(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return promocaoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PromocaoResponse criar(Long empresaId, PromocaoRequest request) {
        validarEmpresaAtual(empresaId);
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        validarDatas(request.dataInicio(), request.dataFim());
        validarCodigoUnico(empresaId, request.codigo(), null);

        PromocaoEntity promocao = PromocaoEntity.builder()
                .empresa(empresa)
                .codigo(request.codigo().trim().toUpperCase())
                .descricao(request.descricao().trim())
                .tipo(request.tipo())
                .valor(request.valor())
                .dataInicio(request.dataInicio())
                .dataFim(request.dataFim())
                .quantidadeLimite(request.quantidadeLimite())
                .quantidadeUsada(0)
                .status(StatusCadastro.ATIVO)
                .aplicarTodosServicos(Boolean.TRUE.equals(request.aplicarTodosServicos()))
                .build();

        if (!promocao.getAplicarTodosServicos()) {
            promocao.setServicos(carregarServicos(empresaId, request.servicoIds()));
        }

        return toResponse(promocaoRepository.save(promocao));
    }

    @Transactional
    public PromocaoResponse atualizar(Long empresaId, Long id, PromocaoRequest request) {
        validarEmpresaAtual(empresaId);
        PromocaoEntity promocao = buscarDaEmpresa(empresaId, id);
        validarDatas(request.dataInicio(), request.dataFim());
        validarCodigoUnico(empresaId, request.codigo(), id);

        promocao.setCodigo(request.codigo().trim().toUpperCase());
        promocao.setDescricao(request.descricao().trim());
        promocao.setTipo(request.tipo());
        promocao.setValor(request.valor());
        promocao.setDataInicio(request.dataInicio());
        promocao.setDataFim(request.dataFim());
        promocao.setQuantidadeLimite(request.quantidadeLimite());
        promocao.setAplicarTodosServicos(Boolean.TRUE.equals(request.aplicarTodosServicos()));
        promocao.setServicos(promocao.getAplicarTodosServicos() ? new HashSet<>() : carregarServicos(empresaId, request.servicoIds()));
        return toResponse(promocaoRepository.save(promocao));
    }

    @Transactional
    public void desativar(Long empresaId, Long id) {
        PromocaoEntity promocao = buscarDaEmpresa(empresaId, id);
        promocao.setStatus(StatusCadastro.INATIVO);
        promocaoRepository.save(promocao);
    }

    @Transactional
    public void excluir(Long empresaId, Long id) {
        PromocaoEntity promocao = buscarDaEmpresa(empresaId, id);
        promocaoUsoRepository.deleteAll(promocaoUsoRepository.findByPromocaoIdOrderByDataUsoDesc(id));
        promocaoNotificacaoRepository.deleteAll(promocaoNotificacaoRepository.findByPromocaoIdOrderByIdDesc(id));
        promocaoRepository.delete(promocao);
        log.info("[promocao] promocao {} excluida da empresa {}", id, empresaId);
    }

    @Transactional(readOnly = true)
    public PromocaoResumoResponse resumo(Long empresaId, Long id) {
        PromocaoEntity promocao = buscarDaEmpresa(empresaId, id);
        List<PromocaoUsoResponse> usos = promocaoUsoRepository.findByPromocaoIdOrderByDataUsoDesc(id).stream()
                .map(u -> new PromocaoUsoResponse(
                        u.getId(),
                        u.getPromocao().getId(),
                        u.getCliente().getId(),
                        u.getCliente().getNome(),
                        u.getDataUso(),
                        u.getValorDesconto()))
                .toList();

        List<PromocaoNotificacaoEntity> notificacoes = promocaoNotificacaoRepository.findByPromocaoIdOrderByIdDesc(id);
        long enviadas = notificacoes.stream().filter(n -> "ENVIADA".equalsIgnoreCase(n.getStatus())).count();
        long erros = notificacoes.stream().filter(n -> "ERRO".equalsIgnoreCase(n.getStatus())).count();

        return new PromocaoResumoResponse(
                promocao.getId(),
                promocao.getCodigo(),
                promocao.getDescricao(),
                promocaoUsoRepository.countDistinctClienteIdByPromocaoId(id),
                promocaoUsoRepository.countByPromocaoId(id),
                (long) notificacoes.size(),
                enviadas,
                erros,
                usos
        );
    }

    @Transactional
    public void notificarClientes(Long empresaId, Long id, PromocaoNotificarRequest request) {
        PromocaoEntity promocao = buscarDaEmpresa(empresaId, id);
        List<ClienteEntity> clientes = selecionarClientes(empresaId, request.tipo(), request.clienteIds());

        for (ClienteEntity cliente : clientes) {
            PromocaoNotificacaoEntity notificacao = PromocaoNotificacaoEntity.builder()
                    .promocao(promocao)
                    .cliente(cliente)
                    .status("PENDENTE")
                    .build();
            promocaoNotificacaoRepository.save(notificacao);
            tentarEnviarNotificacao(notificacao);
        }

        promocao.setDataNotificacao(LocalDateTime.now());
        promocaoRepository.save(promocao);
    }

    @Transactional(readOnly = true)
    public List<PromocaoUsoResponse> listarUsos(Long empresaId, Long id) {
        buscarDaEmpresa(empresaId, id);
        return promocaoUsoRepository.findByPromocaoIdOrderByDataUsoDesc(id).stream()
                .map(u -> new PromocaoUsoResponse(
                        u.getId(),
                        u.getPromocao().getId(),
                        u.getCliente().getId(),
                        u.getCliente().getNome(),
                        u.getDataUso(),
                        u.getValorDesconto()))
                .toList();
    }

    private void tentarEnviarNotificacao(PromocaoNotificacaoEntity notificacao) {
        try {
            PromocaoEntity promocao = notificacao.getPromocao();
            ClienteEntity cliente = notificacao.getCliente();
            String desconto = promocao.getTipo() == TipoPromocao.PERCENTUAL
                    ? promocao.getValor() + "%"
                    : "R$ " + promocao.getValor();

            String assunto = "Promoção disponível: " + promocao.getCodigo();
            String html = """
                    <html>
                      <body style="font-family:Arial,Helvetica,sans-serif; color:#111111; background:#f4f4f5; padding:24px;">
                        <div style="max-width:680px; margin:0 auto; background:#ffffff; border-radius:16px; padding:28px;">
                          <h1 style="margin:0 0 12px;">%s</h1>
                          <p style="margin:0 0 16px;">Olá %s, temos uma promoção para você.</p>
                          <p style="margin:0 0 8px;"><strong>Cupom:</strong> %s</p>
                          <p style="margin:0 0 8px;"><strong>Desconto:</strong> %s</p>
                          <p style="margin:0 0 8px;"><strong>Válido até:</strong> %s</p>
                        </div>
                      </body>
                    </html>
                    """.formatted(
                    promocao.getDescricao(),
                    cliente.getNome(),
                    promocao.getCodigo(),
                    desconto,
                    promocao.getDataFim().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            );

            boolean enviado = resendEmailService.enviarEmailCrm(cliente.getEmail(), assunto, html);
            notificacao.setStatus(enviado ? "ENVIADA" : "ERRO");
            notificacao.setDataEnvio(LocalDateTime.now());
            if (!enviado) {
                notificacao.setMensagemErro("Falha ao enviar email");
            }
            promocaoNotificacaoRepository.save(notificacao);
        } catch (Exception e) {
            notificacao.setStatus("ERRO");
            notificacao.setMensagemErro(e.getMessage());
            promocaoNotificacaoRepository.save(notificacao);
            log.warn("[promocao] erro ao notificar cliente {}: {}", notificacao.getCliente().getId(), e.getMessage());
        }
    }

    private List<ClienteEntity> selecionarClientes(Long empresaId, String tipo, Set<Long> clienteIds) {
        String modo = tipo == null ? "" : tipo.trim().toUpperCase();
        return switch (modo) {
            case "TODOS" -> clienteRepository.findByEmpresaId(empresaId);
            case "EM_RISCO" -> crmService.listarClientes(empresaId, "at_risk", null, null, 30).stream()
                    .map(CrmClienteResponse::id)
                    .map(id -> clienteRepository.findById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
            case "MANUAL" -> clienteRepository.findAllById(clienteIds == null ? Set.of() : clienteIds).stream()
                    .filter(cliente -> cliente.getEmpresa() != null && empresaId.equals(cliente.getEmpresa().getId()))
                    .toList();
            default -> throw new BusinessException("Tipo de notificacao invalido.");
        };
    }

    private Set<ServicoEntity> carregarServicos(Long empresaId, Set<Long> servicoIds) {
        if (servicoIds == null || servicoIds.isEmpty()) {
            return new HashSet<>();
        }
        return servicoRepository.findAllById(servicoIds).stream()
                .filter(servico -> servico.getEmpresa() != null && empresaId.equals(servico.getEmpresa().getId()))
                .collect(Collectors.toSet());
    }

    private PromocaoEntity buscarDaEmpresa(Long empresaId, Long id) {
        return promocaoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Promocao nao encontrada."));
    }

    private EmpresaEntity buscarEmpresa(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
    }

    private void validarDatas(LocalDateTime dataInicio, LocalDateTime dataFim) {
        if (dataInicio.isAfter(dataFim)) {
            throw new BusinessException("Data inicial nao pode ser depois da data final.");
        }
    }

    private void validarCodigoUnico(Long empresaId, String codigo, Long idAtual) {
        boolean existe = promocaoRepository.existsByEmpresaIdAndCodigoIgnoreCase(empresaId, codigo);
        if (!existe) return;
        if (idAtual != null) {
            PromocaoEntity atual = promocaoRepository.findById(idAtual).orElse(null);
            if (atual != null && atual.getCodigo().equalsIgnoreCase(codigo)) {
                return;
            }
        }
        throw new BusinessException("Codigo de promocao ja existe nesta empresa.");
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId != null && empresaId != null && !companyId.equals(empresaId)) {
            throw new BusinessException("Empresa da sessao nao corresponde a Promocoes solicitadas.");
        }
    }

    private PromocaoResponse toResponse(PromocaoEntity promocao) {
        long totalUsos = promocaoUsoRepository.countByPromocaoId(promocao.getId());
        long totalClientesUsaram = promocaoUsoRepository.countDistinctClienteIdByPromocaoId(promocao.getId());
        List<PromocaoNotificacaoEntity> notificacoes = promocaoNotificacaoRepository.findByPromocaoIdOrderByIdDesc(promocao.getId());
        long enviados = notificacoes.stream().filter(n -> "ENVIADA".equalsIgnoreCase(n.getStatus())).count();
        long erros = notificacoes.stream().filter(n -> "ERRO".equalsIgnoreCase(n.getStatus())).count();

        return new PromocaoResponse(
                promocao.getId(),
                promocao.getCodigo(),
                promocao.getDescricao(),
                promocao.getTipo(),
                promocao.getValor(),
                promocao.getDataInicio(),
                promocao.getDataFim(),
                promocao.getQuantidadeLimite(),
                promocao.getQuantidadeUsada(),
                promocao.getStatus(),
                promocao.getAplicarTodosServicos(),
                promocao.getServicos().stream()
                        .map(servico -> new ServicoResumo(servico.getId(), servico.getNome()))
                        .toList(),
                promocao.getDataCriacao(),
                promocao.getDataNotificacao(),
                totalClientesUsaram,
                totalUsos,
                (long) notificacoes.size(),
                enviados,
                erros
        );
    }
}
