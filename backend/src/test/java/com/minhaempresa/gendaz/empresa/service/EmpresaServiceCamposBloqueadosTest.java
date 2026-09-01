package com.minhaempresa.gendaz.empresa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.AtualizarEmpresaRequest;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
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
class EmpresaServiceCamposBloqueadosTest {

    @Mock EmpresaRepository empresaRepository;
    @Mock SanitizacaoService sanitizacaoService;
    @Mock RamoDeteccaoService ramoDeteccaoService;

    private EmpresaService empresaService;

    private EmpresaEntity empresa() {
        return EmpresaEntity.builder()
                .id(7L)
                .nomeFantasia("Minha Empresa")
                .telefone("5565993360300")
                .email("contato@empresa.com")
                .status(StatusEmpresa.ATIVA)
                .build();
    }

    private AtualizarEmpresaRequest request(String nomeFantasia, String email) {
        return new AtualizarEmpresaRequest(nomeFantasia, "(65) 99336-0300", email, "America/Cuiaba", StatusEmpresa.ATIVA);
    }

    @BeforeEach
    void setUp() {
        empresaService = new EmpresaService(
                empresaRepository,
                sanitizacaoService,
                ramoDeteccaoService,
                new PhoneNumberService(),
                mock(LogAtividadeService.class)
        );
        CompanyContext.setCompanyId(7L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void usuarioNaoAlteraNomeFantasiaDiretamente() {
        when(empresaRepository.findById(7L)).thenReturn(Optional.of(empresa()));
        when(sanitizacaoService.textoObrigatorio("Outro Nome")).thenReturn("Outro Nome");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> empresaService.atualizar(7L, request("Outro Nome", "contato@empresa.com")));

        assertEquals("Nome fantasia deve ser alterado por solicitacao ao suporte.", ex.getMessage());
        verify(empresaRepository, never()).save(any(EmpresaEntity.class));
    }

    @Test
    void usuarioNaoAlteraEmailDiretamente() {
        when(empresaRepository.findById(7L)).thenReturn(Optional.of(empresa()));
        when(sanitizacaoService.textoObrigatorio("Minha Empresa")).thenReturn("Minha Empresa");
        when(sanitizacaoService.email("novo@empresa.com")).thenReturn("novo@empresa.com");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> empresaService.atualizar(7L, request("Minha Empresa", "novo@empresa.com")));

        assertEquals("E-mail da empresa deve ser alterado por solicitacao ao suporte.", ex.getMessage());
        verify(empresaRepository, never()).save(any(EmpresaEntity.class));
    }
}