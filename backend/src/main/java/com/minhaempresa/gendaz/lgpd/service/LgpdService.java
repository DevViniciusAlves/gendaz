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
        if (usuario.getPerfil() != PerfilUsuario.DONO) {
            throw new BusinessException("Acesso negado: apenas o dono pode realizar esta ação.");
        }
        Long empresaId = obterEmpresaId(usuario);
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        AssinaturaResponse assinatura = assinaturaService.buscarAtualResponsePorEmpresa(empresaId);
        ResumoFinanceiroResponse financeiro = financeiroService.resumo(empresaId, java.time.LocalDate.now().getMonthValue(), java.time.LocalDate.now().getYear());
        List<AuditoriaExportada> auditoria = auditLogRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream()
                .limit(200)
                .map(this::toAuditoria)
                .toList();

        List<com.minhaempresa.gendaz.mensagem.dto.MensagemDtos.MensagemResponse> mensagens = conversaRepository.findByEmpresaIdOrderByDataUltimaMensagemDesc(empresaId).stream()
                .flatMap(c -> mensagemRepository.findByConversaIdOrderByDataEnvioAsc(c.getId()).stream())
                .map(m -> new com.minhaempresa.gendaz.mensagem.mapper.MensagemMapper().toResponse(m))
                .toList();

        return new ExportacaoDadosResponse(
                new EmpresaExportada(empresa.getId(), empresa.getNomeFantasia(), empresa.getTelefone(), empresa.getEmail(), empresa.getStatus().name(), empresa.getDataCriacao()),
                usuarioMapper.toResponse(usuario),
                assinatura,
                financeiro,
                usuarioRepository.findByEmpresaId(empresaId).stream().map(usuarioMapper::toResponse).toList(),
                clienteRepository.findByEmpresaId(empresaId).stream().map(c -> new com.minhaempresa.gendaz.cliente.mapper.ClienteMapper().toResponse(c)).toList(),
                servicoRepository.findByEmpresaId(empresaId).stream().map(s -> new com.minhaempresa.gendaz.servico.mapper.ServicoMapper().toResponse(s)).toList(),
                profissionalRepository.findByEmpresaId(empresaId).stream().map(p -> new com.minhaempresa.gendaz.profissional.mapper.ProfissionalMapper().toResponse(p)).toList(),
                agendamentoRepository.findByEmpresaId(empresaId).stream().map(a -> new com.minhaempresa.gendaz.agendamento.mapper.AgendamentoMapper().toResponse(a)).toList(),
                conversaRepository.findByEmpresaIdOrderByDataUltimaMensagemDesc(empresaId).stream().map(c -> new com.minhaempresa.gendaz.conversa.mapper.ConversaMapper().toResponse(c)).toList(),
                mensagens,
                pagamentoRepository.findByEmpresaId(empresaId).stream().map(p -> new com.minhaempresa.gendaz.pagamento.mapper.PagamentoMapper().toResponse(p)).toList(),
                pagamentoPlanoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream().map(p -> new com.minhaempresa.gendaz.pagamento.mapper.PagamentoMapper().toPlanoResponse(p)).toList(),
                notaFiscalRepository.findByEmpresaId(empresaId).stream().map(n -> new com.minhaempresa.gendaz.notafiscal.mapper.NotaFiscalMapper().toResponse(n)).toList(),
                entregaRepository.findByEmpresaId(empresaId).stream().map(e -> new com.minhaempresa.gendaz.entrega.mapper.EntregaMapper().toResponse(e)).toList(),
                notificacaoRepository.findByEmpresaId(empresaId).stream().map(n -> new com.minhaempresa.gendaz.notificacao.mapper.NotificacaoMapper().toResponse(n)).toList(),
                chamadoService.listarPorEmpresa(empresaId, usuarioId),
                auditoria
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
            u.setStatus(StatusUsuario.INATIVO);
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

