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
import com.minhaempresa.gendaz.usuario.service.UsuarioService;
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
                clienteRepository.findByEmpresaId(empresaId).stream().map(c -> new com.minhaempresa.gendaz.cliente.mapper.ClienteMapper().toResponse(c)).toList(),
                servicoRepository.findByEmpresaId(empresaId).stream().map(s -> new com.minhaempresa.gendaz.servico.mapper.ServicoMapper().toResponse(s)).toList(),
                profissionalRepository.findByEmpresaId(empresaId).stream().map(p -> new com.minhaempresa.gendaz.profissional.mapper.ProfissionalMapper().toResponse(p)).toList(),
                agendamentoRepository.findByEmpresaId(empresaId).stream().map(a -> new com.minhaempresa.gendaz.agendamento.mapper.AgendamentoMapper().toResponse(a)).toList(),
                conversaRepository.findByEmpresaIdOrderByDataUltimaMensagemDesc(empresaId).stream().map(c -> new com.minhaempresa.gendaz.conversa.mapper.ConversaMapper().toResponse(c)).toList(),
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
            u.setSessaoAtivaMeuGendaz(null);
        });
        usuarioRepository.saveAll(usuarios);

        meuGendazAcessoRepository.findByEmpresaId(empresaId).forEach(acesso -> {
            acesso.setSessaoAtiva(null);
            meuGendazAcessoRepository.save(acesso);
        });

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

