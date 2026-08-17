package com.minhaempresa.gendaz.assinatura.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssinaturaServiceTest {

    @Mock
    private AssinaturaRepository assinaturaRepository;

    @InjectMocks
    private AssinaturaService assinaturaService;

    @Test
    void devePreservarTesteAoAtivarPlanoPago() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).build();
        PlanoEntity planoBasico = PlanoEntity.builder().id(1L).nome("BASICO").build();
        PlanoEntity planoPro = PlanoEntity.builder().id(2L).nome("PRO").build();
        LocalDate hoje = LocalDate.now();

        AssinaturaEntity testeBasico = AssinaturaEntity.builder()
                .id(10L)
                .empresa(empresa)
                .plano(planoBasico)
                .status(StatusAssinatura.TESTE)
                .dataInicio(hoje)
                .dataFim(hoje.plusDays(7))
                .build();

        when(assinaturaRepository.findByEmpresaId(1L)).thenReturn(List.of(testeBasico));
        when(assinaturaRepository.save(any(AssinaturaEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Ativar PRO pago
        AssinaturaEntity novaAssinatura = assinaturaService.ativarPlanoPago(empresa, planoPro);

        // Verify: TESTE preserved
        assertEquals(StatusAssinatura.TESTE, testeBasico.getStatus());
        assertEquals(hoje.plusDays(7), testeBasico.getDataFim());

        // Verify: PRO created and queued after TESTE
        assertNotNull(novaAssinatura);
        assertEquals(StatusAssinatura.ATIVA, novaAssinatura.getStatus());
        assertEquals(testeBasico.getDataFim(), novaAssinatura.getDataInicio());
        
        verify(assinaturaRepository).save(novaAssinatura);
    }

    @Test
    void naoDeveAlterarStatusParaInativoQuandoStatusSoberano() {
        when(assinaturaRepository.save(any(AssinaturaEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        EmpresaEntity empresaPendente = EmpresaEntity.builder().id(1L).status(StatusEmpresa.PENDENTE_PAGAMENTO).build();
        EmpresaEntity empresaBloqueada = EmpresaEntity.builder().id(2L).status(StatusEmpresa.BLOQUEADA).build();
        EmpresaEntity empresaEncerrada = EmpresaEntity.builder().id(3L).status(StatusEmpresa.ENCERRADA).build();
        EmpresaEntity empresaAtiva = EmpresaEntity.builder().id(4L).status(StatusEmpresa.ATIVA).build();

        // Para empresaPendente (1L)
        AssinaturaEntity assinaturaPendente = AssinaturaEntity.builder()
                .id(100L)
                .empresa(empresaPendente)
                .status(StatusAssinatura.PENDENTE_PAGAMENTO)
                .build();
        when(assinaturaRepository.findByEmpresaId(1L)).thenReturn(List.of(assinaturaPendente));
        assinaturaService.buscarAtualPorEmpresa(1L);
        assertEquals(StatusEmpresa.PENDENTE_PAGAMENTO, empresaPendente.getStatus());

        // Para empresaBloqueada (2L)
        AssinaturaEntity assinaturaBloqueada = AssinaturaEntity.builder()
                .id(101L)
                .empresa(empresaBloqueada)
                .status(StatusAssinatura.EXPIRADA)
                .build();
        when(assinaturaRepository.findByEmpresaId(2L)).thenReturn(List.of(assinaturaBloqueada));
        assinaturaService.buscarAtualPorEmpresa(2L);
        assertEquals(StatusEmpresa.BLOQUEADA, empresaBloqueada.getStatus());

        // Para empresaEncerrada (3L)
        AssinaturaEntity assinaturaEncerrada = AssinaturaEntity.builder()
                .id(102L)
                .empresa(empresaEncerrada)
                .status(StatusAssinatura.EXPIRADA)
                .build();
        when(assinaturaRepository.findByEmpresaId(3L)).thenReturn(List.of(assinaturaEncerrada));
        assinaturaService.buscarAtualPorEmpresa(3L);
        assertEquals(StatusEmpresa.ENCERRADA, empresaEncerrada.getStatus());

        // Para empresaAtiva (4L) -> Deve virar INATIVA
        AssinaturaEntity assinaturaAtivaExpirada = AssinaturaEntity.builder()
                .id(103L)
                .empresa(empresaAtiva)
                .status(StatusAssinatura.ATIVA)
                .dataFim(LocalDate.now().minusDays(1)) // já expirou
                .build();
        when(assinaturaRepository.findByEmpresaId(4L)).thenReturn(List.of(assinaturaAtivaExpirada));
        assinaturaService.buscarAtualPorEmpresa(4L);
        assertEquals(StatusEmpresa.INATIVA, empresaAtiva.getStatus());
    }
}
