package com.minhaempresa.gendaz.entrega.service;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.entrega.dto.EntregaDtos.AtualizarStatusEntregaRequest;
import com.minhaempresa.gendaz.entrega.dto.EntregaDtos.CriarEntregaRequest;
import com.minhaempresa.gendaz.entrega.dto.EntregaDtos.EntregaResponse;
import com.minhaempresa.gendaz.entrega.entity.EntregaEntity;
import com.minhaempresa.gendaz.entrega.enums.StatusEntrega;
import com.minhaempresa.gendaz.entrega.mapper.EntregaMapper;
import com.minhaempresa.gendaz.entrega.repository.EntregaRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
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
    private final LogAtividadeService logAtividadeService;
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
        EntregaEntity salva = entregaRepository.save(entrega);
        logAtividadeService.registrar("ENTREGA", salva.getId(), "Criou entrega " + salva.getEndereco());
        return mapper.toResponse(salva);
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
        EntregaEntity salva = entregaRepository.save(entrega);
        logAtividadeService.registrar("ENTREGA", salva.getId(), "Alterou status da entrega " + salva.getEndereco());
        return mapper.toResponse(salva);
    }

    @Transactional(readOnly = true)
    public EntregaEntity buscarEntidade(Long id) {
        EntregaEntity entrega = entregaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega nao encontrada."));
        validarEmpresaAtual(entrega.getEmpresa().getId());
        return entrega;
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.requireCompanyId();
        if (empresaId == null || !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Entrega nao encontrada.");
        }
    }
}

