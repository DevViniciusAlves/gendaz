package com.minhaempresa.agendapro.cliente.service;

import com.minhaempresa.agendapro.admin.service.AdminAuditService;
import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.ClienteResponse;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.SalvarClienteRequest;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.mapper.ClienteMapper;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.conversa.entity.ConversaEntity;
import com.minhaempresa.agendapro.conversa.repository.ConversaRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.service.EmpresaService;
import com.minhaempresa.agendapro.entrega.repository.EntregaRepository;
import com.minhaempresa.agendapro.mensagem.repository.MensagemRepository;
import com.minhaempresa.agendapro.notafiscal.repository.NotaFiscalRepository;
import com.minhaempresa.agendapro.notificacao.repository.NotificacaoRepository;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
// ⚠️ DESATIVADO — import com.minhaempresa.agendapro.whatsapp.repository.WhatsappLembretePagamentoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClienteService {
    private final ClienteRepository clienteRepository;
    private final EmpresaService empresaService;
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final EntregaRepository entregaRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final NotaFiscalRepository notaFiscalRepository;
    // ⚠️ DESATIVADO — private final WhatsappLembretePagamentoRepository lembretePagamentoRepository;
    private final SanitizacaoService sanitizacaoService;
    private final AdminAuditService auditService;
    private final ClienteMapper mapper = new ClienteMapper();

    @Transactional
    public ClienteResponse salvar(SalvarClienteRequest request) {
        EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
        ClienteEntity cliente = ClienteEntity.builder()
                .nome(sanitizacaoService.textoObrigatorio(request.nome()))
                .telefone(sanitizacaoService.telefone(request.telefone()))
                .email(sanitizacaoService.email(request.email()))
                .observacoes(sanitizacaoService.texto(request.observacoes()))
                .empresa(empresa)
                .build();
        ClienteEntity salvo = clienteRepository.save(cliente);
        auditService.registrar("CLIENTE_CRIADO", "INFO", null, null, empresa, "Cliente criado", salvo.getNome(), null, null);
        return mapper.toResponse(salvo);
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return clienteRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscarPorId(Long id) {
        ClienteEntity cliente = buscarEntidade(id);
        validarEmpresaAtual(cliente.getEmpresa().getId());
        return mapper.toResponse(cliente);
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscarPorTelefone(String telefone) {
        String telefoneSanitizado = sanitizacaoService.telefone(telefone);
        return clienteRepository.findFirstByTelefone(telefoneSanitizado)
                .filter(cliente -> com.minhaempresa.agendapro.shared.CompanyContext.getCompanyId() == null
                        || com.minhaempresa.agendapro.shared.CompanyContext.getCompanyId().equals(cliente.getEmpresa().getId()))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
    }

    @Transactional
    public ClienteResponse atualizar(Long id, SalvarClienteRequest request) {
        ClienteEntity cliente = buscarEntidade(id);
        validarEmpresa(cliente, request.empresaId());
        cliente.setNome(sanitizacaoService.textoObrigatorio(request.nome()));
        cliente.setTelefone(sanitizacaoService.telefone(request.telefone()));
        cliente.setEmail(sanitizacaoService.email(request.email()));
        cliente.setObservacoes(sanitizacaoService.texto(request.observacoes()));
        ClienteEntity salvo = clienteRepository.save(cliente);
        auditService.registrar("CLIENTE_ATUALIZADO", "INFO", null, null, cliente.getEmpresa(), "Cliente atualizado", salvo.getNome(), null, null);
        return mapper.toResponse(salvo);
    }

    @Transactional
    public void excluir(Long id, Long empresaId) {
        ClienteEntity cliente = buscarEntidade(id);
        validarEmpresa(cliente, empresaId);

        for (AgendamentoEntity agendamento : agendamentoRepository.findByClienteId(id)) {
            // ⚠️ DESATIVADO — lembretePagamentoRepository.deleteByAgendamento_Id(agendamento.getId());
            pagamentoRepository.deleteByAgendamentoId(agendamento.getId());
            agendamentoRepository.delete(agendamento);
        }

        for (ConversaEntity conversa : conversaRepository.findByClienteId(id)) {
            mensagemRepository.deleteByConversaId(conversa.getId());
            conversaRepository.delete(conversa);
        }

        entregaRepository.deleteByClienteId(id);
        notificacaoRepository.deleteByClienteId(id);
        notaFiscalRepository.deleteByClienteId(id);
        pagamentoRepository.deleteByClienteId(id);
        clienteRepository.delete(cliente);
        auditService.registrar("CLIENTE_EXCLUIDO", "WARN", null, null, cliente.getEmpresa(), "Cliente excluido", cliente.getNome(), null, null);
    }

    @Transactional(readOnly = true)
    public ClienteEntity buscarEntidade(Long id) {
        ClienteEntity cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
        validarEmpresaAtual(cliente.getEmpresa().getId());
        return cliente;
    }

    private void validarEmpresa(ClienteEntity cliente, Long empresaId) {
        if (empresaId == null || !cliente.getEmpresa().getId().equals(empresaId)) {
            throw new ResourceNotFoundException("Cliente não encontrado.");
        }
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId != null && empresaId != null && !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Cliente não encontrado.");
        }
    }
}
