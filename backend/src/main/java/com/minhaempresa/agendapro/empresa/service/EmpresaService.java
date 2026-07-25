package com.minhaempresa.agendapro.empresa.service;

import com.minhaempresa.agendapro.empresa.dto.EmpresaDtos.AtualizarEmpresaRequest;
import com.minhaempresa.agendapro.empresa.dto.EmpresaDtos.CriarEmpresaRequest;
import com.minhaempresa.agendapro.empresa.dto.EmpresaDtos.EmpresaResponse;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.enums.StatusEmpresa;
import com.minhaempresa.agendapro.empresa.mapper.EmpresaMapper;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ConflictException;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
import com.minhaempresa.agendapro.shared.enums.TimezoneEnum;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmpresaService {
    private final EmpresaRepository empresaRepository;
    private final SanitizacaoService sanitizacaoService;
    private final RamoDeteccaoService ramoDeteccaoService;
    private final EmpresaMapper mapper = new EmpresaMapper();

    @Transactional
    public EmpresaResponse criar(CriarEmpresaRequest request) {
        validarDadosObrigatorios(request.nomeFantasia(), request.email());
        if (request.documento() != null && !request.documento().isBlank() && empresaRepository.existsByDocumento(request.documento())) {
            throw new ConflictException("Ja existe empresa com este documento.");
        }
        EmpresaEntity empresa = EmpresaEntity.builder()
                .nomeFantasia(sanitizacaoService.textoObrigatorio(request.nomeFantasia()))
                .documento(sanitizacaoService.texto(request.documento()))
                .telefone(sanitizacaoService.telefone(request.telefone()))
                .email(sanitizacaoService.email(request.email()))
                .status(StatusEmpresa.ATIVA)
                .timezone(TimezoneEnum.AMERICA_CUIABA.getValue())
                .build();
        return mapper.toResponse(empresaRepository.save(empresa));
    }

    @Transactional
    public EmpresaResponse buscarPorId(Long id) {
        EmpresaEntity empresa = buscarEntidade(id);
        empresa = ramoDeteccaoService.sincronizarRamoSeNecessario(empresa);
        return mapper.toResponse(empresa);
    }

    @Transactional
    public EmpresaResponse atualizar(Long id, AtualizarEmpresaRequest request) {
        validarDadosObrigatorios(request.nomeFantasia(), request.email());
        EmpresaEntity empresa = buscarEntidade(id);
        validarCamposBloqueados(empresa, request);
        String telefone = sanitizacaoService.telefone(request.telefone());
        if (telefone != null && !telefone.isBlank() && (telefone.length() < 10 || telefone.length() > 15)) {
            throw new BusinessException("Telefone deve ter de 10 a 15 digitos.");
        }
        empresa.setTelefone(telefone);
        empresa.setTimezone(resolverTimezone(empresa.getTimezone(), request.timezone()));
        return mapper.toResponse(empresaRepository.save(empresa));
    }

    @Transactional(readOnly = true)
    public EmpresaEntity buscarEntidade(Long id) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId != null && !companyId.equals(id)) {
            throw new ResourceNotFoundException("Empresa nao encontrada.");
        }
        return empresaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
    }

    private void validarDadosObrigatorios(String nomeFantasia, String email) {
        if (nomeFantasia == null || nomeFantasia.isBlank()) {
            throw new BusinessException("Nome fantasia e obrigatorio.");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException("E-mail da empresa e obrigatorio.");
        }
    }

    private void validarCamposBloqueados(EmpresaEntity empresa, AtualizarEmpresaRequest request) {
        if (!empresa.getNomeFantasia().equals(sanitizacaoService.textoObrigatorio(request.nomeFantasia()))) {
            throw new BusinessException("Nome fantasia deve ser alterado por solicitacao ao suporte.");
        }
        if (!Objects.equals(empresa.getDocumento(), sanitizacaoService.texto(request.documento()))) {
            throw new BusinessException("Documento deve ser alterado por solicitacao ao suporte.");
        }
        if (!empresa.getEmail().equals(sanitizacaoService.email(request.email()))) {
            throw new BusinessException("E-mail da empresa deve ser alterado por solicitacao ao suporte.");
        }
    }

    private String resolverTimezone(String timezoneAtual, String timezoneNovo) {
        if (timezoneNovo == null || timezoneNovo.isBlank()) {
            return timezoneAtual;
        }
        if (TimezoneEnum.fromValue(timezoneNovo).isEmpty()) {
            throw new BusinessException("Fuso horario invalido.");
        }
        return timezoneNovo;
    }
}
