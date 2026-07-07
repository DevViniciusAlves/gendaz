package com.minhaempresa.agendapro.conversa.service;

import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.service.ClienteService;
import com.minhaempresa.agendapro.conversa.dto.ConversaDtos.ConversaResponse;
import com.minhaempresa.agendapro.conversa.dto.ConversaDtos.CriarConversaRequest;
import com.minhaempresa.agendapro.conversa.entity.ConversaEntity;
import com.minhaempresa.agendapro.conversa.enums.StatusConversa;
import com.minhaempresa.agendapro.conversa.mapper.ConversaMapper;
import com.minhaempresa.agendapro.conversa.repository.ConversaRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.service.EmpresaService;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
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
    private final ConversaMapper mapper = new ConversaMapper();

    @Transactional
    public ConversaResponse criar(CriarConversaRequest request) {
        ClienteEntity cliente = clienteService.buscarEntidade(request.clienteId());
        EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
        ConversaEntity conversa = ConversaEntity.builder()
                .cliente(cliente)
                .empresa(empresa)
                .status(StatusConversa.ABERTA)
                .ultimaMensagem("Conversa iniciada.")
                .dataUltimaMensagem(LocalDateTime.now())
                .build();
        return mapper.toResponse(conversaRepository.save(conversa));
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
        return mapper.toResponse(conversaRepository.save(conversa));
    }

    @Transactional(readOnly = true)
    public ConversaEntity buscarEntidade(Long id) {
        ConversaEntity conversa = conversaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada."));
        Long companyId = CompanyContext.getCompanyId();
        if (companyId != null && conversa.getEmpresa() != null && !companyId.equals(conversa.getEmpresa().getId())) {
            throw new ResourceNotFoundException("Conversa não encontrada.");
        }
        return conversa;
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId != null && empresaId != null && !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Conversa não encontrada.");
        }
    }
}
