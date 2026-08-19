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
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.AuditoriaExportada;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.EmpresaExportada;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.ExcluirContaResponse;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.ExportacaoDadosResponse;
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
import java.util.List;
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
    private final UsuarioMapper usuarioMapper = new UsuarioMapper();

    @Transactional(readOnly = true)
    public ExportacaoDadosResponse exportar(Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarEntidade(usuarioId);
        Long empresaId = obterEmpresaId(usuario);
        EmpresaEntity empresa = buscarEmpresa(empresaId);

        // 1. Dados da Exportação
        var exportacao = new LgpdDtos.ExportacaoInfo(
                java.time.LocalDateTime.now(),
                "JSON",
                "gendaz"
        );

        // 2. Dados da Empresa (Campos permitidos)
        var empresaExport = new LgpdDtos.EmpresaExportada(
                empresa.getNomeFantasia(),
                empresa.getTelefone(),
                empresa.getEmail(),
                empresa.getAgendamentoSlug(),
                empresa.getStatus().name(),
                empresa.getTimezone(),
                empresa.getRamo(),
                empresa.getDataCriacao(),
                empresa.getDataAtualizacao()
        );

        // 3. Meus Dados
        var meusDados = new LgpdDtos.MeusDadosExport(
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil().name(),
                usuario.getStatus().name()
        );

        // 4. Aceites LGPD
        var aceites = new LgpdDtos.AceitesLgpdExport(
                usuario.isAceitouTermos(),
                usuario.getDataAceiteTermos(),
                usuario.getVersaoTermos(),
                usuario.getDataAceitePolitica(),
                usuario.getVersaoPolitica()
        );

        // 5. Plano (Leitura segura)
        LgpdDtos.PlanoExport plano = null;
        try {
            AssinaturaResponse assinatura = assinaturaService.buscarAtualResponsePorEmpresa(empresaId);
            if (assinatura != null) {
                plano = new LgpdDtos.PlanoExport(
                        assinatura.getPlanoNome(),
                        assinatura.getStatus().name(),
                        assinatura.getDataCriacao(),
                        assinatura.getDataFim()
                );
            }
        } catch (Exception e) {
            log.warn("Nao foi possivel carregar dados do plano para exportacao LGPD: {}", e.getMessage());
        }

        // 6. Dados Tecnicos (Audit logs do proprio usuario)
        // Buscamos logs onde o IP ou UserAgent podem estar relacionados, mas o ideal eh filtro por usuario se existir.
        // Como o AuditLogEntity parece nao ter usuarioId direto (pelo exportar original), filtramos os ultimos 50 da empresa
        // que coincidam com o IP/UA atual se disponivel, ou apenas retornamos vazios se nao houver vinculo forte.
        // No momento, seguindo a regra de nao inferir vinculo apenas por empresa, retornaremos apenas os que o repository
        // suportar de forma segura. O sistema atual usa findByEmpresaId.
        // Para cumprir o requisito 6: "Nao exportar esse registro automaticamente se nao houver seguranca".
        List<LgpdDtos.AuditoriaExportada> auditoria = List.of();

        // 7. Meu Gendaz (Acesso do titular)
        LgpdDtos.MeuGendazExport meuGendaz = meuGendazAcessoRepository.findByEmpresaIdAndEmail(empresaId, usuario.getEmail())
                .map(a -> new LgpdDtos.MeuGendazExport(a.getNome(), a.getEmail(), a.getStatus().name()))
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

