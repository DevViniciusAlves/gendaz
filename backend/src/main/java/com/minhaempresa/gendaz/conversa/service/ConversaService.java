package com.minhaempresa.gendaz.conversa.service;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.conversa.dto.ConversaDtos.ConversaResponse;
import com.minhaempresa.gendaz.conversa.dto.ConversaDtos.CriarConversaRequest;
import com.minhaempresa.gendaz.conversa.entity.ConversaEntity;
import com.minhaempresa.gendaz.conversa.enums.StatusConversa;
import com.minhaempresa.gendaz.conversa.mapper.ConversaMapper;
import com.minhaempresa.gendaz.conversa.repository.ConversaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversaService {
    private final ConversaRepository conversaRepository;
    private final ClienteService clienteService;
    private final EmpresaService empresaService;
    private final LogAtividadeService logAtividadeService;
    private final ConversaMapper mapper = new ConversaMapper();

    @Transactional
    public ConversaResponse criar(CriarConversaRequest request) {
        ClienteEntity cliente = clienteService.buscarEntidadeOperacional(request.clienteId());
        EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
        ConversaEntity conversa = ConversaEntity.builder()
                .cliente(cliente)
                .empresa(empresa)
                .status(StatusConversa.ABERTA)
                .ultimaMensagem("Conversa iniciada.")
                .dataUltimaMensagem(LocalDateTime.now())
                .build();
        ConversaResponse response = mapper.toResponse(conversaRepository.save(conversa));
        logAtividadeService.registrar("CONVERSA", conversa.getId(), "Iniciou conversa com " + cliente.getNome());
        return response;
    }

    @Transactional(readOnly = true)
    public List<ConversaResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return conversaRepository.findByEmpresaIdOrderByDataUltimaMensagemDesc(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ConversaResponse buscarPorId(Long id) {
        return mapper.toResponse(buscarEntidade(id));
    }

    @Transactional
    public void atualizarUltimaMensagem(ConversaEntity conversa, String conteudo) {
        conversa.setUltimaMensagem(conteudo);
        conversa.setDataUltimaMensagem(LocalDateTime.now());
        conversaRepository.save(conversa);
    }

    @Transactional
    public ConversaResponse finalizar(Long id) {
        ConversaEntity conversa = buscarEntidade(id);
        conversa.setStatus(StatusConversa.FINALIZADA);
        conversa.setDataUltimaMensagem(LocalDateTime.now());
        ConversaResponse response = mapper.toResponse(conversaRepository.save(conversa));
        logAtividadeService.registrar("CONVERSA", conversa.getId(), "Finalizou conversa com " + (conversa.getCliente() != null ? conversa.getCliente().getNome() : "cliente"));
        return response;
    }

    @Transactional(readOnly = true)
    public ConversaEntity buscarEntidade(Long id) {
        ConversaEntity conversa = conversaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada."));
        Long companyId = CompanyContext.requireCompanyId();
        if (conversa.getEmpresa() == null || !companyId.equals(conversa.getEmpresa().getId())) {
            throw new ResourceNotFoundException("Conversa não encontrada.");
        }
        return conversa;
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.requireCompanyId();
        if (empresaId == null || !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Conversa não encontrada.");
        }
    }
}

