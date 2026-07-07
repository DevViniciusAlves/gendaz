package com.minhaempresa.agendapro.notafiscal.service;

import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.service.ClienteService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.service.EmpresaService;
import com.minhaempresa.agendapro.notafiscal.dto.NotaFiscalDtos.EmitirNotaFiscalRequest;
import com.minhaempresa.agendapro.notafiscal.dto.NotaFiscalDtos.NotaFiscalResponse;
import com.minhaempresa.agendapro.notafiscal.entity.NotaFiscalEntity;
import com.minhaempresa.agendapro.notafiscal.enums.StatusNotaFiscal;
import com.minhaempresa.agendapro.notafiscal.mapper.NotaFiscalMapper;
import com.minhaempresa.agendapro.notafiscal.repository.NotaFiscalRepository;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotaFiscalService {
    private final NotaFiscalRepository notaFiscalRepository;
    private final ClienteService clienteService;
    private final EmpresaService empresaService;
    private final NotaFiscalMapper mapper = new NotaFiscalMapper();

    @Transactional
    public NotaFiscalResponse emitir(EmitirNotaFiscalRequest request) {
        ClienteEntity cliente = clienteService.buscarEntidade(request.clienteId());
        EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
        NotaFiscalEntity nota = NotaFiscalEntity.builder()
                .cliente(cliente)
                .empresa(empresa)
                .valor(request.valor())
                .status(StatusNotaFiscal.EMITIDA)
                .numeroFake("NF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .dataEmissao(LocalDateTime.now())
                .build();
        return mapper.toResponse(notaFiscalRepository.save(nota));
    }

    @Transactional(readOnly = true)
    public List<NotaFiscalResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return notaFiscalRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NotaFiscalEntity buscarEntidade(Long id) {
        NotaFiscalEntity notaFiscal = notaFiscalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nota fiscal nao encontrada."));
        validarEmpresaAtual(notaFiscal.getEmpresa().getId());
        return notaFiscal;
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId != null && empresaId != null && !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Nota fiscal nao encontrada.");
        }
    }
}
