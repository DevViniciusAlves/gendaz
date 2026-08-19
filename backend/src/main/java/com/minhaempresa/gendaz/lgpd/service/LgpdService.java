package com.minhaempresa.gendaz.lgpd.service;

import com.minhaempresa.gendaz.admin.entity.AuditLogEntity;
import com.minhaempresa.gendaz.admin.repository.AuditLogRepository;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.chamado.service.ChamadoService;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.conversa.repository.ConversaRepository;
import com.minhaempresa.gendaz.entrega.repository.EntregaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.gendaz.financeiro.service.FinanceiroService;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.AceitesLgpd;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.AuditoriaExportada;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.EmpresaExportada;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.ExcluirContaResponse;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.ExportacaoDadosResponse;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.MeuGendazExportado;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.PlanoExportado;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.ReativarContaResponse;
import com.minhaempresa.gendaz.mensagem.repository.MensagemRepository;
import com.minhaempresa.gendaz.meugendazacesso.repository.MeuGendazAcessoRepository;
import com.minhaempresa.gendaz.notafiscal.repository.NotaFiscalRepository;
import com.minhaempresa.gendaz.notificacao.repository.NotificacaoRepository;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGateway;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.mapper.UsuarioMapper;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.service.UsuarioService;
import com.minhaempresa.gendaz.auth.service.UsuarioSessionService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LgpdService {
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final MeuGendazAcessoRepository meuGendazAcessoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final NotaFiscalRepository notaFiscalRepository;
    private final EntregaRepository entregaRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final ChamadoService chamadoService;
    private final AssinaturaService assinaturaService;
    private final FinanceiroService financeiroService;
    private final AuditLogRepository auditLogRepository;
    private final PaymentGateway paymentGateway;
    private final UsuarioSessionService usuarioSessionService;
    private final UsuarioMapper usuarioMapper = new UsuarioMapper();

    @Transactional(readOnly = true)
    public ExportacaoDadosResponse exportar(Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarEntidade(usuarioId);
        Long empresaId = obterEmpresaId(usuario);
        EmpresaEntity empresa = buscarEmpresa(empresaId);

        // 1. Dados da Exportação
        Map<String, Object> exportacao = new java.util.HashMap<>();
        exportacao.put("geradoEm", java.time.LocalDateTime.now());
        exportacao.put("formato", "JSON");
        exportacao.put("origem", "gendaz");

        // 2. Dados da Empresa (Campos permitidos)
        var empresaExport = new EmpresaExportada(
                empresa.getId(),
                empresa.getNomeFantasia(),
                empresa.getTelefone(),
                empresa.getEmail(),
                empresa.getAgendamentoSlug(),
                empresa.getStatus().name(),
                empresa.getTimezone(),
                empresa.getRamo() != null ? empresa.getRamo().name() : null,
                empresa.getDataCriacao(),
                empresa.getDataAtualizacao()
        );

        // 3. Meus Dados
        var meusDados = usuarioMapper.toResponse(usuario);

        // 4. Aceites LGPD
        var aceites = new AceitesLgpd(
                usuario.getAceitouTermos() != null && usuario.getAceitouTermos(),
                usuario.getDataAceiteTermos(),
                usuario.getVersaoTermos(),
                usuario.getDataAceitePolitica(),
                usuario.getVersaoPolitica()
        );

        // 5. Plano (Leitura segura)
        PlanoExportado plano = null;
        try {
            AssinaturaResponse assinatura = assinaturaService.buscarAtualResponsePorEmpresa(empresaId);
            if (assinatura != null) {
                plano = new PlanoExportado(
                        assinatura.planoNome(),
                        assinatura.status().name(),
                        assinatura.dataInicio() != null ? assinatura.dataInicio().atStartOfDay() : null,
                        assinatura.dataFim() != null ? assinatura.dataFim().atStartOfDay() : null
                );
            }
        } catch (Exception e) {
            log.warn("Nao foi possivel carregar dados do plano para exportacao LGPD: {}", e.getMessage());
        }

        // 6. Dados Tecnicos (Audit logs do proprio usuario)
        List<AuditoriaExportada> auditoria = List.of();

        // 7. Meu Gendaz (Acesso do titular)
        MeuGendazExportado meuGendaz = meuGendazAcessoRepository.findByEmpresaIdAndEmailIgnoreCase(empresaId, usuario.getEmail())
                .map(a -> new MeuGendazExportado(a.getNome(), a.getEmail(), a.getStatus().name()))
                .orElse(null);

        return new ExportacaoDadosResponse(
                exportacao,
                empresaExport,
                meusDados,
                aceites,
                plano,
                auditoria,
                meuGendaz
        );
    }

    @Transactional
    public ExcluirContaResponse encerrarConta(Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarEntidade(usuarioId);
        if (usuario.getPerfil() != PerfilUsuario.DONO) {
            throw new BusinessException("Acesso negado: apenas o dono pode realizar esta ação.");
        }
        if (usuario.getEmpresa() == null) {
            throw new BusinessException("Usuario sem empresa nao pode solicitar exclusao da conta.");
        }
        Long empresaId = usuario.getEmpresa().getId();
        EmpresaEntity empresa = buscarEmpresa(empresaId);

        List<UsuarioEntity> usuarios = usuarioRepository.findByEmpresaId(empresaId);
        usuarios.forEach(u -> {
            u.setSessaoAtiva(null);
            u.setSessaoAtivaMeuGendaz(null);
        });
        usuarioRepository.saveAll(usuarios);

        meuGendazAcessoRepository.findByEmpresaId(empresaId).forEach(acesso -> {
            acesso.setSessaoAtiva(null);
            meuGendazAcessoRepository.save(acesso);
        });

        // Estado terminal para LGPD: impede reativação por webhook ou pagamento
        empresa.setStatus(StatusEmpresa.ENCERRADA);
        empresaRepository.save(empresa);
        
        // Cancelar subscription Stripe para impedir renovação futura
        String stripeStatus = cancelarSubscriptionStripe(empresa);

        return new ExcluirContaResponse(
                "Conta encerrada com sucesso. Os acessos foram revogados e as cobranças futuras canceladas.",
                empresa.getId(),
                empresa.getStatus().name(),
                stripeStatus
        );
    }

    @Transactional
    public ReativarContaResponse reativarConta(Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarEntidade(usuarioId);
        if (usuario.getPerfil() != PerfilUsuario.DONO) {
            throw new BusinessException("Acesso negado: apenas o dono pode reativar a conta.");
        }
        if (usuario.getEmpresa() == null) {
            throw new BusinessException("Usuario sem empresa nao pode reativar a conta.");
        }
        EmpresaEntity empresa = buscarEmpresa(usuario.getEmpresa().getId());
        if (empresa.getStatus() != StatusEmpresa.ENCERRADA) {
            throw new BusinessException("Esta conta nao esta encerrada.");
        }

        // Decisao sempre pela vigencia real do plano/trial existente (somente leitura das regras atuais).
        boolean planoVigente = assinaturaService.buscarAtualPorEmpresa(empresa.getId()).isPresent();
        StatusEmpresa novoStatus = planoVigente ? StatusEmpresa.ATIVA : StatusEmpresa.INATIVA;
        empresa.setStatus(novoStatus);
        empresaRepository.save(empresa);

        // Encerra a sessao restrita usada para a reativacao: o dono fara novo login normal.
        if (usuario.getSessaoAtiva() != null && !usuario.getSessaoAtiva().isBlank()) {
            usuarioSessionService.encerrarSessao(usuario.getSessaoAtiva());
        }

        String mensagem = novoStatus == StatusEmpresa.ATIVA
                ? "Conta reativada com sucesso. Faca login novamente para utilizar o gendaz."
                : "Conta reativada. Seu plano expirou: faca login para regularizar o pagamento.";
        log.info("[LGPD] Conta encerrada reativada pelo DONO: empresa={}, novoStatus={}", empresa.getId(), novoStatus);
        return new ReativarContaResponse(mensagem, empresa.getId(), novoStatus.name());
    }

    private EmpresaEntity buscarEmpresa(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
    }

    private Long obterEmpresaId(UsuarioEntity usuario) {
        if (usuario.getEmpresa() == null) {
            throw new BusinessException("Usuario nao possui empresa vinculada.");
        }
        return usuario.getEmpresa().getId();
    }

    private String cancelarSubscriptionStripe(EmpresaEntity empresa) {
        if (empresa.getStripeCustomerId() == null || empresa.getStripeCustomerId().isBlank()) {
            log.info("Nenhuma subscription Stripe para cancelar: empresa={}", empresa.getId());
            return "NENHUMA_ASSINATURA";
        }
        
        try {
            List<PagamentoPlanoEntity> pagamentos = pagamentoPlanoRepository.findByEmpresaIdAndSubscriptionIdNotNull(empresa.getId());
            boolean algumCancelado = false;
            for (PagamentoPlanoEntity pagamento : pagamentos) {
                if (pagamento.getSubscriptionId() != null && !pagamento.getSubscriptionId().isBlank()) {
                    paymentGateway.cancelarSubscription(pagamento.getSubscriptionId());
                    algumCancelado = true;
                    log.info("Subscription Stripe cancelada: empresa={}, subscriptionId={}", empresa.getId(), pagamento.getSubscriptionId());
                }
            }
            return algumCancelado ? "CANCELADO" : "NENHUMA_ASSINATURA_ATIVA";
        } catch (Exception ex) {
            log.error("Falha ao cancelar subscription Stripe para empresa {}: {}", empresa.getId(), ex.getMessage(), ex);
            return "FALHA_AO_CANCELAR";
        }
    }


    private AuditoriaExportada toAuditoria(AuditLogEntity log) {
        return new AuditoriaExportada(
                log.getId(),
                log.getTipo(),
                log.getSeveridade(),
                log.getDescricao(),
                log.getMotivo(),
                log.getIp(),
                log.getUserAgent(),
                log.getDataCriacao()
        );
    }
}

