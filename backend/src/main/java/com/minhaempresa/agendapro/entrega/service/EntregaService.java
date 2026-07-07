package com.minhaempresa.agendapro.entrega.service;

import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.service.ClienteService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.service.EmpresaService;
import com.minhaempresa.agendapro.entrega.dto.EntregaDtos.AtualizarStatusEntregaRequest;
import com.minhaempresa.agendapro.entrega.dto.EntregaDtos.CriarEntregaRequest;
import com.minhaempresa.agendapro.entrega.dto.EntregaDtos.EntregaResponse;
import com.minhaempresa.agendapro.entrega.entity.EntregaEntity;
import com.minhaempresa.agendapro.entrega.enums.StatusEntrega;
import com.minhaempresa.agendapro.entrega.mapper.EntregaMapper;
import com.minhaempresa.agendapro.entrega.repository.EntregaRepository;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntregaService {
    private final EntregaRepository entregaRepository;
    private final ClienteService clienteService;
    private final EmpresaService empresaService;
    private final SanitizacaoService sanitizacaoService;
    private final EntregaMapper mapper = new EntregaMapper();

    @Transactional
    public EntregaResponse criar(CriarEntregaRequest request) {
        ClienteEntity cliente = clienteService.buscarEntidade(request.clienteId());
        EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
        EntregaEntity entrega = EntregaEntity.builder()
                .cliente(cliente)
                .empresa(empresa)
                .endereco(sanitizacaoService.textoObrigatorio(request.endereco()))
                .status(StatusEntrega.PENDENTE)
                .observacoes(sanitizacaoService.texto(request.observacoes()))
                .dataPrevisao(request.dataPrevisao())
                .build();
        return mapper.toResponse(entregaRepository.save(entrega));
    }

    @Transactional(readOnly = true)
    public List<EntregaResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return entregaRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public EntregaResponse atualizarStatus(Long id, AtualizarStatusEntregaRequest request) {
        EntregaEntity entrega = buscarEntidade(id);
        entrega.setStatus(request.status());
        return mapper.toResponse(entregaRepository.save(entrega));
    }

    @Transactional(readOnly = true)
    public EntregaEntity buscarEntidade(Long id) {
        EntregaEntity entrega = entregaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega nao encontrada."));
        validarEmpresaAtual(entrega.getEmpresa().getId());
        return entrega;
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId != null && empresaId != null && !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Entrega nao encontrada.");
        }
    }
}
