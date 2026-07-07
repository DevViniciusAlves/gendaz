package com.minhaempresa.agendapro.lgpd.service;

import com.minhaempresa.agendapro.admin.entity.AuditLogEntity;
import com.minhaempresa.agendapro.admin.repository.AuditLogRepository;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.agendapro.assinatura.service.AssinaturaService;
import com.minhaempresa.agendapro.chamado.service.ChamadoService;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.conversa.repository.ConversaRepository;
import com.minhaempresa.agendapro.entrega.repository.EntregaRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.enums.StatusEmpresa;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.agendapro.financeiro.service.FinanceiroService;
import com.minhaempresa.agendapro.lgpd.dto.LgpdDtos.AuditoriaExportada;
import com.minhaempresa.agendapro.lgpd.dto.LgpdDtos.EmpresaExportada;
import com.minhaempresa.agendapro.lgpd.dto.LgpdDtos.ExcluirContaResponse;
import com.minhaempresa.agendapro.lgpd.dto.LgpdDtos.ExportacaoDadosResponse;
import com.minhaempresa.agendapro.mensagem.repository.MensagemRepository;
import com.minhaempresa.agendapro.notafiscal.repository.NotaFiscalRepository;
import com.minhaempresa.agendapro.notificacao.repository.NotificacaoRepository;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.profissional.repository.ProfissionalRepository;
import com.minhaempresa.agendapro.servico.repository.ServicoRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.StatusUsuario;
import com.minhaempresa.agendapro.usuario.mapper.UsuarioMapper;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import com.minhaempresa.agendapro.usuario.service.UsuarioService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
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
    private final PagamentoRepository pagamentoRepository;
    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final NotaFiscalRepository notaFiscalRepository;
    private final EntregaRepository entregaRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final ChamadoService chamadoService;
    private final AssinaturaService assinaturaService;
    private final FinanceiroService financeiroService;
    private final AuditLogRepository auditLogRepository;
    private final UsuarioMapper usuarioMapper = new UsuarioMapper();

    @Transactional(readOnly = true)
    public ExportacaoDadosResponse exportar(Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarEntidade(usuarioId);
        Long empresaId = obterEmpresaId(usuario);
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        AssinaturaResponse assinatura = assinaturaService.buscarAtualResponsePorEmpresa(empresaId);
        ResumoFinanceiroResponse financeiro = financeiroService.resumo(empresaId, java.time.LocalDate.now().getMonthValue(), java.time.LocalDate.now().getYear());
        List<AuditoriaExportada> auditoria = auditLogRepository.findTop200ByOrderByDataCriacaoDesc().stream()
                .filter(log -> log.getEmpresa() != null && log.getEmpresa().getId().equals(empresaId))
                .map(this::toAuditoria)
                .toList();

        return new ExportacaoDadosResponse(
                new EmpresaExportada(empresa.getId(), empresa.getNomeFantasia(), empresa.getDocumento(), empresa.getTelefone(), empresa.getEmail(), empresa.getStatus().name(), empresa.getDataCriacao()),
                usuarioMapper.toResponse(usuario),
                assinatura,
                financeiro,
                usuarioRepository.findByEmpresaId(empresaId).stream().map(usuarioMapper::toResponse).toList(),
                clienteRepository.findByEmpresaId(empresaId).stream().map(c -> new com.minhaempresa.agendapro.cliente.mapper.ClienteMapper().toResponse(c)).toList(),
                servicoRepository.findByEmpresaId(empresaId).stream().map(s -> new com.minhaempresa.agendapro.servico.mapper.ServicoMapper().toResponse(s)).toList(),
                profissionalRepository.findByEmpresaId(empresaId).stream().map(p -> new com.minhaempresa.agendapro.profissional.mapper.ProfissionalMapper().toResponse(p)).toList(),
                agendamentoRepository.findByEmpresaId(empresaId).stream().map(a -> new com.minhaempresa.agendapro.agendamento.mapper.AgendamentoMapper().toResponse(a)).toList(),
                conversaRepository.findByEmpresaIdOrderByDataUltimaMensagemDesc(empresaId).stream().map(c -> new com.minhaempresa.agendapro.conversa.mapper.ConversaMapper().toResponse(c)).toList(),
                pagamentoRepository.findByEmpresaId(empresaId).stream().map(p -> new com.minhaempresa.agendapro.pagamento.mapper.PagamentoMapper().toResponse(p)).toList(),
                pagamentoPlanoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream().map(p -> new com.minhaempresa.agendapro.pagamento.mapper.PagamentoMapper().toPlanoResponse(p)).toList(),
                notaFiscalRepository.findByEmpresaId(empresaId).stream().map(n -> new com.minhaempresa.agendapro.notafiscal.mapper.NotaFiscalMapper().toResponse(n)).toList(),
                entregaRepository.findByEmpresaId(empresaId).stream().map(e -> new com.minhaempresa.agendapro.entrega.mapper.EntregaMapper().toResponse(e)).toList(),
                notificacaoRepository.findByEmpresaId(empresaId).stream().map(n -> new com.minhaempresa.agendapro.notificacao.mapper.NotificacaoMapper().toResponse(n)).toList(),
                chamadoService.listarPorEmpresa(empresaId, usuarioId),
                auditoria
        );
    }

    @Transactional
    public ExcluirContaResponse excluirConta(Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarEntidade(usuarioId);
        if (usuario.getEmpresa() == null) {
            throw new BusinessException("Usuario sem empresa nao pode solicitar exclusao da conta.");
        }
        Long empresaId = usuario.getEmpresa().getId();
        EmpresaEntity empresa = buscarEmpresa(empresaId);

        List<UsuarioEntity> usuarios = usuarioRepository.findByEmpresaId(empresaId);
        usuarios.forEach(u -> {
            u.setStatus(StatusUsuario.INATIVO);
            u.setSessaoAtiva(null);
        });
        usuarioRepository.saveAll(usuarios);

        empresa.setStatus(StatusEmpresa.INATIVA);
        empresaRepository.save(empresa);

        return new ExcluirContaResponse(
                "Conta desativada com sucesso. Os acessos foram revogados e os dados permanecem sujeitos as regras de retencao legal.",
                empresa.getId(),
                empresa.getStatus().name()
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
