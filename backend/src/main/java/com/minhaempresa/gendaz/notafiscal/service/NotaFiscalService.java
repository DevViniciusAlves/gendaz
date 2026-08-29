package com.minhaempresa.gendaz.notafiscal.service;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.notafiscal.dto.NotaFiscalDtos.EmitirNotaFiscalRequest;
import com.minhaempresa.gendaz.notafiscal.dto.NotaFiscalDtos.NotaFiscalResponse;
import com.minhaempresa.gendaz.notafiscal.entity.NotaFiscalEntity;
import com.minhaempresa.gendaz.notafiscal.enums.StatusNotaFiscal;
import com.minhaempresa.gendaz.notafiscal.mapper.NotaFiscalMapper;
import com.minhaempresa.gendaz.notafiscal.repository.NotaFiscalRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
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
    private final LogAtividadeService logAtividadeService;

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
        NotaFiscalEntity salva = notaFiscalRepository.save(nota);
        logAtividadeService.registrar("NOTA_FISCAL", salva.getId(), "Emitiu nota fiscal " + salva.getNumeroFake());
        return mapper.toResponse(salva);
    }

    @Transactional(readOnly = true)
    public List<NotaFiscalResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return notaFiscalRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NotaFiscalEntity buscarEntidade(Long id) {
        NotaFiscalEntity notaFiscal = notaFiscalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nota fiscal não encontrada."));
        validarEmpresaAtual(notaFiscal.getEmpresa().getId());
        return notaFiscal;
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.requireCompanyId();
        if (empresaId == null || !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Nota fiscal não encontrada.");
        }
    }
}

