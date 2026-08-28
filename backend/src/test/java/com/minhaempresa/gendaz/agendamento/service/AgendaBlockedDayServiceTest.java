package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendaBlockedDayDtos.BloquearDiaRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendaBlockedDayEntity;
import com.minhaempresa.gendaz.agendamento.repository.AgendaBlockedDayRepository;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgendaBlockedDayServiceTest {
    @Mock AgendaBlockedDayRepository repository;
    @Mock EmpresaService empresaService;
    @Mock ProfissionalService profissionalService;
    @Mock LogAtividadeService logAtividadeService;
    @Captor ArgumentCaptor<AgendaBlockedDayEntity> bloqueioCaptor;
    @InjectMocks AgendaBlockedDayService service;

    @AfterEach
    void limparContexto() {
        CompanyContext.clear();
    }

    @Test
    void empresaAListaA() {
        CompanyContext.setCompanyId(1L);
        EmpresaEntity empresaA = empresa(1L);
        when(repository.findByEmpresaIdOrderByDataAsc(1L)).thenReturn(List.of(bloqueio(10L, empresaA, null)));

        var resposta = service.listar(1L);

        assertEquals(1, resposta.size());
        assertEquals(1L, resposta.get(0).empresaId());
        verify(repository).findByEmpresaIdOrderByDataAsc(1L);
    }

    @Test
    void empresaAListaBFalha() {
        CompanyContext.setCompanyId(1L);

        assertThrows(BusinessException.class, () -> service.listar(2L));

        verify(repository, never()).findByEmpresaIdOrderByDataAsc(any());
    }

    @Test
    void empresaACriaA() {
        CompanyContext.setCompanyId(1L);
        EmpresaEntity empresaA = empresa(1L);
        when(empresaService.buscarEntidade(1L)).thenReturn(empresaA);
        when(repository.existsByEmpresaIdAndDataAndProfissionalIsNull(1L, LocalDate.now().plusDays(10))).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.bloquear(new BloquearDiaRequest(1L, null, LocalDate.now().plusDays(10), " Motivo  teste "));

        verify(empresaService).buscarEntidade(1L);
        verify(repository).save(bloqueioCaptor.capture());
        assertEquals(empresaA, bloqueioCaptor.getValue().getEmpresa());
        assertEquals("Motivo teste", bloqueioCaptor.getValue().getMotivo());
    }

    @Test
    void empresaATentaCriarBFalha() {
        CompanyContext.setCompanyId(1L);

        assertThrows(BusinessException.class, () -> service.bloquear(new BloquearDiaRequest(2L, null, LocalDate.now().plusDays(10), null)));

        verify(empresaService, never()).buscarEntidade(any());
        verify(repository, never()).save(any());
    }

    @Test
    void empresaAUsaProfissionalA() {
        CompanyContext.setCompanyId(1L);
        EmpresaEntity empresaA = empresa(1L);
        ProfissionalEntity profissionalA = profissional(10L, empresaA);
        when(empresaService.buscarEntidade(1L)).thenReturn(empresaA);
        when(profissionalService.buscarEntidade(10L)).thenReturn(profissionalA);
        when(repository.existsByEmpresaIdAndDataAndProfissionalIsNull(1L, LocalDate.now().plusDays(10))).thenReturn(false);
        when(repository.existsByEmpresaIdAndProfissionalIdAndData(1L, 10L, LocalDate.now().plusDays(10))).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.bloquear(new BloquearDiaRequest(1L, 10L, LocalDate.now().plusDays(10), null));

        verify(repository).save(bloqueioCaptor.capture());
        assertEquals(profissionalA, bloqueioCaptor.getValue().getProfissional());
        assertEquals(empresaA, bloqueioCaptor.getValue().getEmpresa());
    }

    @Test
    void empresaATentaProfissionalBFalha() {
        CompanyContext.setCompanyId(1L);
        EmpresaEntity empresaA = empresa(1L);
        ProfissionalEntity profissionalB = profissional(20L, empresa(2L));
        when(empresaService.buscarEntidade(1L)).thenReturn(empresaA);
        when(profissionalService.buscarEntidade(20L)).thenReturn(profissionalB);

        assertThrows(BusinessException.class, () -> service.bloquear(new BloquearDiaRequest(1L, 20L, LocalDate.now().plusDays(10), null)));

        verify(repository, never()).save(any());
    }

    @Test
    void empresaAExcluiBloqueioA() {
        CompanyContext.setCompanyId(1L);
        AgendaBlockedDayEntity bloqueioA = bloqueio(100L, empresa(1L), null);
        when(repository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(bloqueioA));

        service.desbloquear(100L, 1L);

        verify(repository).findByIdAndEmpresaId(100L, 1L);
        verify(repository).delete(bloqueioA);
    }

    @Test
    void empresaATentaExcluirBComEmpresaIdAFalha() {
        CompanyContext.setCompanyId(1L);
        when(repository.findByIdAndEmpresaId(200L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.desbloquear(200L, 1L));

        verify(repository).findByIdAndEmpresaId(200L, 1L);
        verify(repository, never()).delete(any());
    }

    @Test
    void empresaATentaExcluirBComEmpresaIdBFalhaAntesDaConsulta() {
        CompanyContext.setCompanyId(1L);

        assertThrows(BusinessException.class, () -> service.desbloquear(200L, 2L));

        verify(repository, never()).findByIdAndEmpresaId(any(), any());
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteSemEmpresaIdNaoViraBypass() {
        CompanyContext.setCompanyId(1L);

        assertThrows(BusinessException.class, () -> service.desbloquear(100L, null));

        verify(repository, never()).findByIdAndEmpresaId(any(), any());
        verify(repository, never()).delete(any());
    }

    @Test
    void operacaoProtegidaSemCompanyContextFalha() {
        CompanyContext.clear();

        assertThrows(BusinessException.class, () -> service.listar(1L));
        assertThrows(BusinessException.class, () -> service.bloquear(new BloquearDiaRequest(1L, null, LocalDate.now().plusDays(10), null)));
        assertThrows(BusinessException.class, () -> service.desbloquear(100L, 1L));

        verify(repository, never()).findByEmpresaIdOrderByDataAsc(any());
        verify(repository, never()).save(any());
        verify(repository, never()).findByIdAndEmpresaId(any(), any());
        verify(repository, never()).delete(any());
    }

    @Test
    void diaBloqueadoPermaneceUtilitarioSemCompanyContext() {
        CompanyContext.clear();
        LocalDate data = LocalDate.now().plusDays(10);
        when(repository.existsByEmpresaIdAndDataAndProfissionalIsNull(1L, data)).thenReturn(false);
        when(repository.existsByEmpresaIdAndProfissionalIdAndData(1L, 10L, data)).thenReturn(true);

        assertDoesNotThrow(() -> service.diaBloqueado(1L, 10L, data));
        assertTrue(service.diaBloqueado(1L, 10L, data));
    }

    @Test
    void diaBloqueadoGeralRetornaVerdadeiroSemConsultarProfissional() {
        CompanyContext.clear();
        LocalDate data = LocalDate.now().plusDays(10);
        when(repository.existsByEmpresaIdAndDataAndProfissionalIsNull(1L, data)).thenReturn(true);

        assertTrue(service.diaBloqueado(1L, 10L, data));

        verify(repository, never()).existsByEmpresaIdAndProfissionalIdAndData(any(), any(), any());
    }

    @Test
    void diaBloqueadoSemProfissionalRetornaFalsoQuandoNaoHaBloqueioGeral() {
        CompanyContext.clear();
        LocalDate data = LocalDate.now().plusDays(10);
        when(repository.existsByEmpresaIdAndDataAndProfissionalIsNull(1L, data)).thenReturn(false);

        assertFalse(service.diaBloqueado(1L, null, data));
    }

    private EmpresaEntity empresa(Long id) {
        return EmpresaEntity.builder().id(id).nomeFantasia("Empresa " + id).email("empresa" + id + "@gendaz.test").build();
    }

    private ProfissionalEntity profissional(Long id, EmpresaEntity empresa) {
        return ProfissionalEntity.builder().id(id).nome("Profissional " + id).empresa(empresa).build();
    }

    private AgendaBlockedDayEntity bloqueio(Long id, EmpresaEntity empresa, ProfissionalEntity profissional) {
        return AgendaBlockedDayEntity.builder()
                .id(id)
                .empresa(empresa)
                .profissional(profissional)
                .data(LocalDate.now().plusDays(10))
                .motivo("Motivo")
                .build();
    }
}
