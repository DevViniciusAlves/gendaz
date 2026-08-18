package com.minhaempresa.gendaz.empresa.service;

import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.AtualizarEmpresaRequest;
import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.CriarEmpresaRequest;
import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.EmpresaResponse;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.mapper.EmpresaMapper;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.enums.TimezoneEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmpresaService {
    private final EmpresaRepository empresaRepository;
    private final SanitizacaoService sanitizacaoService;
    private final RamoDeteccaoService ramoDeteccaoService;
    private final PhoneNumberService phoneNumberService;
    private final EmpresaMapper mapper = new EmpresaMapper();

    @Transactional
    public EmpresaResponse criar(CriarEmpresaRequest request) {
        validarDadosObrigatorios(request.nomeFantasia(), request.email());
        EmpresaEntity empresa = EmpresaEntity.builder()
                .nomeFantasia(sanitizacaoService.textoObrigatorio(request.nomeFantasia()))
                .telefone(phoneNumberService.normalizarOpcional(request.telefone()))
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
        String telefone = phoneNumberService.normalizarOpcional(request.telefone());
        if (telefone != null && !telefone.isBlank()
                && !telefone.equals(empresa.getTelefone())
                && empresaRepository.existsByTelefoneAndIdNot(telefone, id)) {
            throw new ConflictException("Este numero ja esta cadastrado em outra conta.");
        }
        empresa.setTelefone(telefone);
        empresa.setTimezone(resolverTimezone(empresa.getTimezone(), request.timezone()));
        return mapper.toResponse(empresaRepository.save(empresa));
    }

    @Transactional(readOnly = true)
    public EmpresaEntity buscarEntidade(Long id) {
        Long companyId = CompanyContext.requireCompanyId();
        if (id == null || !companyId.equals(id)) {
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

