package com.minhaempresa.gendaz.cliente.service;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.ClienteResponse;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.SalvarClienteRequest;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.mapper.ClienteMapper;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.conversa.entity.ConversaEntity;
import com.minhaempresa.gendaz.conversa.repository.ConversaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.crm.repository.CrmContatoRepository;
import com.minhaempresa.gendaz.cliente.service.ClienteEmailBloqueadoService;
import com.minhaempresa.gendaz.entrega.repository.EntregaRepository;
import com.minhaempresa.gendaz.mensagem.repository.MensagemRepository;
import com.minhaempresa.gendaz.meugendazpromocao.repository.MeuGendazPromocaoNotificacaoRepository;
import com.minhaempresa.gendaz.notafiscal.repository.NotaFiscalRepository;
import com.minhaempresa.gendaz.notificacao.repository.NotificacaoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.promocao.repository.PromocaoNotificacaoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
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
    private final PhoneNumberService phoneNumberService;
    private final AdminAuditService auditService;

    private final ClienteMapper mapper = new ClienteMapper();

    @Transactional
    public ClienteResponse salvar(SalvarClienteRequest request) {
        Map<String, Object> contextoInicio = new LinkedHashMap<>();
        contextoInicio.put("empresaId", request.empresaId());
        log.debug("[cliente-debug] inicio criacao cliente {}", contextoInicio);
        try {
            EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
            String nome = sanitizacaoService.textoObrigatorio(request.nome());
            String telefone = phoneNumberService.normalizarObrigatorio(request.telefone());
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
            log.info("[cliente-debug] cliente criado com sucesso {}", contextoSucesso);
            auditService.registrar("CLIENTE_CRIADO", "INFO", null, null, empresa, "Cliente criado", salvo.getNome(), null, null);
            return mapper.toResponse(salvo);
        } catch (Exception e) {
            Map<String, Object> contexto = new LinkedHashMap<>();
            contexto.put("empresaId", request.empresaId());
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
        String telefoneNormalizado = phoneNumberService.normalizarObrigatorio(telefone);
        Long companyId = CompanyContext.requireCompanyId();
        return clienteRepository.findFirstByEmpresaIdAndTelefone(companyId, telefoneNormalizado)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
    }

    @Transactional
    public ClienteResponse atualizar(Long id, SalvarClienteRequest request) {
        ClienteEntity cliente = buscarEntidade(id);
        validarEmpresa(cliente, request.empresaId());
        String nome = sanitizacaoService.textoObrigatorio(request.nome());
        String telefone = phoneNumberService.normalizarObrigatorio(request.telefone());
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

        // Apenas limpa vínculos operacionais, preservando financeiros/agendamentos/fiscais
        for (ConversaEntity conversa : conversaRepository.findByClienteId(id)) {
            mensagemRepository.deleteByConversaId(conversa.getId());
            conversaRepository.delete(conversa);
        }
        crmContatoRepository.deleteByClienteId(id);
        notificacaoRepository.deleteByClienteId(id);
        promocaoNotificacaoRepository.deleteByClienteId(id);
        meuGendazPromocaoNotificacaoRepository.deleteByClienteId(id);
        
        // Anonimização
        cliente.setNome("Cliente excluído");
        cliente.setTelefone("00000000000");
        cliente.setEmail("excluido-" + cliente.getId() + "@gendaz.site");
        cliente.setObservacoes("");
        cliente.setStatus(StatusCadastro.EXCLUIDO);
        
        clienteRepository.save(cliente);

        clienteEmailBloqueadoService.bloquear(cliente.getEmpresa(), cliente.getEmail(), "Cliente excluido pelo painel Gendaz");
        
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
        Long companyId = CompanyContext.requireCompanyId();
        if (empresaId == null || !companyId.equals(empresaId)) {
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
        Optional<ClienteEntity> clienteComMesmoTelefone = clienteRepository.findFirstByEmpresaIdAndTelefone(empresaId, telefone);
        if (clienteComMesmoTelefone.isPresent() && (ignorarClienteId == null || !clienteComMesmoTelefone.get().getId().equals(ignorarClienteId))) {
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

