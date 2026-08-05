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
import com.minhaempresa.agendapro.crm.repository.CrmContatoRepository;
import com.minhaempresa.agendapro.cliente.service.ClienteEmailBloqueadoService;
import com.minhaempresa.agendapro.entrega.repository.EntregaRepository;
import com.minhaempresa.agendapro.mensagem.repository.MensagemRepository;
import com.minhaempresa.agendapro.meugendazpromocao.repository.MeuGendazPromocaoNotificacaoRepository;
import com.minhaempresa.agendapro.notafiscal.repository.NotaFiscalRepository;
import com.minhaempresa.agendapro.notificacao.repository.NotificacaoRepository;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.promocao.repository.PromocaoNotificacaoRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClienteService {
    private final ClienteRepository clienteRepository;
    private final EmpresaService empresaService;
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ConversaRepository conversaRepository;
    private final CrmContatoRepository crmContatoRepository;
    private final MensagemRepository mensagemRepository;
    private final EntregaRepository entregaRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final NotaFiscalRepository notaFiscalRepository;
    private final PromocaoNotificacaoRepository promocaoNotificacaoRepository;
    private final MeuGendazPromocaoNotificacaoRepository meuGendazPromocaoNotificacaoRepository;
    private final ClienteEmailBloqueadoService clienteEmailBloqueadoService;
    private final SanitizacaoService sanitizacaoService;
    private final AdminAuditService auditService;
    private final ClienteMapper mapper = new ClienteMapper();

    @Transactional
    public ClienteResponse salvar(SalvarClienteRequest request) {
        Map<String, Object> contextoInicio = new LinkedHashMap<>();
        contextoInicio.put("empresaId", request.empresaId());
        contextoInicio.put("nome", request.nome());
        contextoInicio.put("telefone", request.telefone());
        contextoInicio.put("email", request.email());
        log.debug("[cliente-debug] inicio criacao cliente {}", contextoInicio);
        try {
            EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
            String nome = sanitizacaoService.textoObrigatorio(request.nome());
            String telefone = sanitizacaoService.telefone(request.telefone());
            String email = sanitizacaoService.email(request.email());
            validarCamposObrigatorios(nome, telefone, email);
            validarDuplicidade(empresa.getId(), telefone, email, null);
            ClienteEntity cliente = ClienteEntity.builder()
                    .nome(nome)
                    .telefone(telefone)
                    .email(email)
                    .observacoes(sanitizacaoService.texto(request.observacoes()))
                    .status(StatusCadastro.ATIVO)
                    .empresa(empresa)
                    .build();
            ClienteEntity salvo = clienteRepository.save(cliente);
            clienteEmailBloqueadoService.desbloquear(empresa.getId(), salvo.getEmail());
            Map<String, Object> contextoSucesso = new LinkedHashMap<>();
            contextoSucesso.put("clienteId", salvo.getId());
            contextoSucesso.put("empresaId", empresa.getId());
            contextoSucesso.put("nome", salvo.getNome());
            contextoSucesso.put("telefone", salvo.getTelefone());
            contextoSucesso.put("email", salvo.getEmail());
            log.info("[cliente-debug] cliente criado com sucesso {}", contextoSucesso);
            auditService.registrar("CLIENTE_CRIADO", "INFO", null, null, empresa, "Cliente criado", salvo.getNome(), null, null);
            return mapper.toResponse(salvo);
        } catch (Exception e) {
            Map<String, Object> contexto = new LinkedHashMap<>();
            contexto.put("empresaId", request.empresaId());
            contexto.put("nome", request.nome());
            contexto.put("telefone", request.telefone());
            contexto.put("email", request.email());
            log.error("[cliente-debug] erro ao criar cliente. mensagem='{}' contexto={}", e.getMessage(), contexto, e);
            throw e;
        }
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
        String nome = sanitizacaoService.textoObrigatorio(request.nome());
        String telefone = sanitizacaoService.telefone(request.telefone());
        String email = sanitizacaoService.email(request.email());
        validarCamposObrigatorios(nome, telefone, email);
        validarDuplicidade(cliente.getEmpresa().getId(), telefone, email, cliente.getId());
        cliente.setNome(nome);
        cliente.setTelefone(telefone);
        cliente.setEmail(email);
        cliente.setObservacoes(sanitizacaoService.texto(request.observacoes()));
        ClienteEntity salvo = clienteRepository.save(cliente);
        clienteEmailBloqueadoService.desbloquear(cliente.getEmpresa().getId(), salvo.getEmail());
        auditService.registrar("CLIENTE_ATUALIZADO", "INFO", null, null, cliente.getEmpresa(), "Cliente atualizado", salvo.getNome(), null, null);
        return mapper.toResponse(salvo);
    }

    @Transactional
    public void excluir(Long id, Long empresaId) {
        ClienteEntity cliente = buscarEntidade(id);
        validarEmpresa(cliente, empresaId);

        for (AgendamentoEntity agendamento : agendamentoRepository.findByClienteId(id)) {
            pagamentoRepository.deleteByAgendamentoId(agendamento.getId());
            agendamentoRepository.delete(agendamento);
        }

        for (ConversaEntity conversa : conversaRepository.findByClienteId(id)) {
            mensagemRepository.deleteByConversaId(conversa.getId());
            conversaRepository.delete(conversa);
        }

        crmContatoRepository.deleteByClienteId(id);
        entregaRepository.deleteByClienteId(id);
        notificacaoRepository.deleteByClienteId(id);
        notaFiscalRepository.deleteByClienteId(id);
        pagamentoRepository.deleteByClienteId(id);
        promocaoNotificacaoRepository.deleteByClienteId(id);
        meuGendazPromocaoNotificacaoRepository.deleteByClienteId(id);
        clienteEmailBloqueadoService.bloquear(cliente.getEmpresa(), cliente.getEmail(), "Cliente excluido pelo painel Gendaz");
        clienteRepository.delete(cliente);
        auditService.registrar("CLIENTE_EXCLUIDO", "WARN", null, null, cliente.getEmpresa(), "Cliente excluido", cliente.getNome(), null, null);
    }

    @Transactional
    public ClienteResponse alterarStatus(Long id, Long empresaId, StatusCadastro status) {
        ClienteEntity cliente = buscarEntidade(id);
        validarEmpresa(cliente, empresaId);
        cliente.setStatus(status == null ? StatusCadastro.ATIVO : status);
        ClienteEntity salvo = clienteRepository.save(cliente);
        auditService.registrar(
                "CLIENTE_STATUS_ALTERADO",
                "INFO",
                null,
                null,
                cliente.getEmpresa(),
                "Status do cliente alterado",
                cliente.getNome() + " -> " + salvo.getStatus().name(),
                null,
                null
        );
        return mapper.toResponse(salvo);
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

    private void validarCamposObrigatorios(String nome, String telefone, String email) {
        if (nome == null || nome.isBlank()) {
            throw new BusinessException("Nome e obrigatorio.");
        }
        if (telefone == null || telefone.isBlank()) {
            throw new BusinessException("Telefone e obrigatorio.");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException("E-mail e obrigatorio.");
        }
    }

    private void validarDuplicidade(Long empresaId, String telefone, String email, Long ignorarClienteId) {
        boolean telefoneExiste = ignorarClienteId == null
                ? clienteRepository.existsByEmpresaIdAndTelefone(empresaId, telefone)
                : clienteRepository.existsByEmpresaIdAndTelefoneAndIdNot(empresaId, telefone, ignorarClienteId);
        if (telefoneExiste) {
            throw new BusinessException("Ja existe um cliente com este telefone.");
        }

        boolean emailExiste = ignorarClienteId == null
                ? clienteRepository.existsByEmpresaIdAndEmail(empresaId, email)
                : clienteRepository.existsByEmpresaIdAndEmailAndIdNot(empresaId, email, ignorarClienteId);
        if (emailExiste) {
            throw new BusinessException("Ja existe um cliente com este e-mail.");
        }
    }
}
