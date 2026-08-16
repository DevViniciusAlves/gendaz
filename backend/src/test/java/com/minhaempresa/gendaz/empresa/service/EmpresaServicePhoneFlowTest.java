package com.minhaempresa.gendaz.empresa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.AtualizarEmpresaRequest;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.mapper.EmpresaMapper;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmpresaServicePhoneFlowTest {

    @Mock EmpresaRepository empresaRepository;
    @Mock SanitizacaoService sanitizacaoService;
    @Mock RamoDeteccaoService ramoDeteccaoService;

    private PhoneNumberService phoneNumberService;
    private EmpresaService empresaService;

    private EmpresaEntity empresa() {
        return EmpresaEntity.builder()
                .id(7L)
                .nomeFantasia("Minha Empresa")
                .documento(null)
                .telefone("5565993360300")
                .email("contato@empresa.com")
                .status(StatusEmpresa.ATIVA)
                .build();
    }

    @BeforeEach
    void setUp() {
        phoneNumberService = new PhoneNumberService();
        empresaService = new EmpresaService(empresaRepository, sanitizacaoService, ramoDeteccaoService, phoneNumberService);
        CompanyContext.setCompanyId(7L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private AtualizarEmpresaRequest request(String telefone) {
        return new AtualizarEmpresaRequest("Minha Empresa", null, telefone, "contato@empresa.com", "America/Cuiaba", StatusEmpresa.ATIVA);
    }

    @Test
    void editarMantendoProprioTelefonePermitido() {
        EmpresaEntity empresa = empresa();
        when(empresaRepository.findById(7L)).thenReturn(Optional.of(empresa));
        when(sanitizacaoService.textoObrigatorio("Minha Empresa")).thenReturn("Minha Empresa");
        when(sanitizacaoService.texto(null)).thenReturn(null);
        when(sanitizacaoService.email("contato@empresa.com")).thenReturn("contato@empresa.com");
        when(empresaRepository.save(any(EmpresaEntity.class))).thenReturn(empresa);

        // mesmo telefone atual -> permitido
        var response = empresaService.atualizar(7L, request("(65) 99336-0300"));

        assertEquals("5565993360300", response.telefone());
        verify(empresaRepository, never()).existsByTelefoneAndIdNot("5565993360300", 7L);
    }

    @Test
    void editarTelefoneInternacionalNormalizaEConflitaComOutraEmpresa() {
        EmpresaEntity empresa = empresa();
        when(empresaRepository.findById(7L)).thenReturn(Optional.of(empresa));
        when(sanitizacaoService.textoObrigatorio("Minha Empresa")).thenReturn("Minha Empresa");
        when(sanitizacaoService.texto(null)).thenReturn(null);
        when(sanitizacaoService.email("contato@empresa.com")).thenReturn("contato@empresa.com");

        // telefone de outra empresa
        when(empresaRepository.existsByTelefoneAndIdNot("14155552671", 7L)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> empresaService.atualizar(7L, request("+1 415 555 2671")));
        assertEquals("Este numero ja esta cadastrado em outra conta.", ex.getMessage());
        verify(empresaRepository, never()).save(any(EmpresaEntity.class));
    }
}
