package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.repository.PlanoRepository;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppUsoCicloEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppCategoriaCota;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppUsoCicloRepository;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppDisponibilidade;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppQuotaService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppReserva;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppUsoResponse;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WhatsAppQuotaServiceTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    @Autowired
    private WhatsAppQuotaService quotaService;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private PlanoRepository planoRepository;
    @Autowired
    private AssinaturaRepository assinaturaRepository;
    @Autowired
    private WhatsAppUsoCicloRepository usoRepository;

    private EmpresaEntity novaEmpresa() {
        long seq = SEQUENCIA.incrementAndGet();
        return empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Wpp Cota " + seq)
                .email("wpp-cota-" + seq + "-" + System.nanoTime() + "@teste.com")
                .status(StatusEmpresa.ATIVA)
                .build());
    }

    private PlanoEntity plano(String nome) {
        return planoRepository.findByNome(nome).orElseThrow();
    }

    private AssinaturaEntity assinaturaVigente(EmpresaEntity empresa, PlanoEntity plano,
            LocalDate inicio, LocalDate fim) {
        return assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresa)
                .plano(plano)
                .status(StatusAssinatura.ATIVA)
                .dataInicio(inicio)
                .dataFim(fim)
                .build());
    }

    @Test
    void consultarUsoCriaRegistroDoCicloSobDemandaEReutilizaNoMesmoCiclo() {
        EmpresaEntity empresa = novaEmpresa();
        LocalDate hoje = LocalDate.now();
        assinaturaVigente(empresa, plano("PRO"), hoje.minusDays(5), hoje.plusDays(25));

        WhatsAppUsoResponse primeira = quotaService.consultarUso(empresa.getId());
        assertEquals(hoje.minusDays(5), primeira.cicloInicio());
        assertEquals("PRO", primeira.planoNome());
        assertEquals(150, primeira.limiteLembretes());
        assertEquals(10, primeira.limiteCrm());
        assertNotNull(primeira.cicloInicio());

        WhatsAppUsoResponse segunda = quotaService.consultarUso(empresa.getId());
        assertEquals(primeira.cicloInicio(), segunda.cicloInicio());
        assertEquals(
                1,
                usoRepository.findByEmpresaIdAndCicloInicio(
                        empresa.getId(), hoje.minusDays(5)).stream().count());
    }

    @Test
    void cicloSeguinteCriaOutroRegistroSemMisturarUso() {
        EmpresaEntity empresa = novaEmpresa();
        LocalDate hoje = LocalDate.now();
        AssinaturaEntity cicloAntigo = assinaturaVigente(
                empresa, plano("PRO"), hoje.minusDays(40), hoje.plusDays(20));

        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);
        quotaService.confirmarEnvio(empresa.getId(), WhatsAppCategoriaCota.CRM);

        // Renovacao: o ciclo anterior vence e um novo ciclo comeca hoje.
        cicloAntigo.setDataFim(hoje.minusDays(1));
        assinaturaRepository.save(cicloAntigo);
        assinaturaVigente(empresa, plano("PRO"), hoje, hoje.plusDays(30));

        WhatsAppUsoResponse novoCiclo = quotaService.consultarUso(empresa.getId());
        assertEquals(hoje, novoCiclo.cicloInicio());
        assertEquals(0, novoCiclo.crmEnviados());
        assertEquals(0, novoCiclo.crmReservados());
        assertEquals(10, novoCiclo.crmDisponiveis());

        WhatsAppUsoCicloEntity usoAntigo = usoRepository
                .findByEmpresaIdAndCicloInicio(empresa.getId(), hoje.minusDays(40)).orElseThrow();
        assertEquals(1, usoAntigo.getCrmEnviados());
    }

    @Test
    void constraintImpedeDuplicidadeDeEmpresaCiclo() {
        EmpresaEntity empresa = novaEmpresa();
        LocalDate hoje = LocalDate.now();
        assinaturaVigente(empresa, plano("PRO"), hoje.minusDays(5), hoje.plusDays(25));
        quotaService.consultarUso(empresa.getId());

        WhatsAppUsoCicloEntity duplicado = WhatsAppUsoCicloEntity.builder()
                .empresa(empresa)
                .cicloInicio(hoje.minusDays(5))
                .build();
        assertThrows(DataIntegrityViolationException.class,
                () -> usoRepository.saveAndFlush(duplicado));
    }

    @Test
    void enviadosNaoUltrapassamOLimiteEReservadosEntramNaDisponibilidade() {
        EmpresaEntity empresa = novaEmpresa();
        LocalDate hoje = LocalDate.now();
        assinaturaVigente(empresa, plano("PRO"), hoje.minusDays(5), hoje.plusDays(25));

        for (int i = 0; i < 10; i++) {
            assertEquals(WhatsAppReserva.RESERVADA, quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM));
        }
        assertEquals(WhatsAppReserva.LIMITE_ATINGIDO, quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM));
        assertEquals(
                WhatsAppDisponibilidade.LIMITE_ATINGIDO,
                quotaService.podeReservar(empresa.getId(), WhatsAppCategoriaCota.CRM));

        WhatsAppUsoResponse uso = quotaService.consultarUso(empresa.getId());
        assertEquals(10, uso.crmReservados());
        assertEquals(0, uso.crmEnviados());
        assertEquals(0, uso.crmDisponiveis());
    }

    @Test
    void liberarReservaDevolveCapacidadeSemAumentarEnviados() {
        EmpresaEntity empresa = novaEmpresa();
        LocalDate hoje = LocalDate.now();
        assinaturaVigente(empresa, plano("PRO"), hoje.minusDays(5), hoje.plusDays(25));

        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);
        quotaService.liberarReserva(empresa.getId(), WhatsAppCategoriaCota.CRM);

        WhatsAppUsoResponse uso = quotaService.consultarUso(empresa.getId());
        assertEquals(0, uso.crmReservados());
        assertEquals(0, uso.crmEnviados());
        assertEquals(10, uso.crmDisponiveis());
        assertEquals(WhatsAppReserva.RESERVADA, quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM));
    }

    @Test
    void confirmarEnvioConverteExatamenteUmReservadoEmUmEnviado() {
        EmpresaEntity empresa = novaEmpresa();
        LocalDate hoje = LocalDate.now();
        assinaturaVigente(empresa, plano("PRO"), hoje.minusDays(5), hoje.plusDays(25));

        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);
        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);
        quotaService.confirmarEnvio(empresa.getId(), WhatsAppCategoriaCota.CRM);

        WhatsAppUsoResponse uso = quotaService.consultarUso(empresa.getId());
        assertEquals(1, uso.crmReservados());
        assertEquals(1, uso.crmEnviados());
        assertEquals(8, uso.crmDisponiveis());
    }

    @Test
    void confirmarOuLiberarSemReservaNaoAlteraNada() {
        EmpresaEntity empresa = novaEmpresa();
        LocalDate hoje = LocalDate.now();
        assinaturaVigente(empresa, plano("PRO"), hoje.minusDays(5), hoje.plusDays(25));
        quotaService.consultarUso(empresa.getId());

        quotaService.confirmarEnvio(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);
        quotaService.liberarReserva(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);

        WhatsAppUsoResponse uso = quotaService.consultarUso(empresa.getId());
        assertEquals(0, uso.lembretesReservados());
        assertEquals(0, uso.lembretesEnviados());
        assertEquals(150, uso.lembretesDisponiveis());
    }

    @Test
    void lembreteNaoAlteraCrmECrmNaoAlteraLembrete() {
        EmpresaEntity empresa = novaEmpresa();
        LocalDate hoje = LocalDate.now();
        assinaturaVigente(empresa, plano("PRO"), hoje.minusDays(5), hoje.plusDays(25));

        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);
        quotaService.confirmarEnvio(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);

        WhatsAppUsoResponse uso = quotaService.consultarUso(empresa.getId());
        assertEquals(1, uso.lembretesEnviados());
        assertEquals(149, uso.lembretesDisponiveis());
        assertEquals(0, uso.crmEnviados());
        assertEquals(0, uso.crmReservados());
        assertEquals(10, uso.crmDisponiveis());

        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);
        WhatsAppUsoResponse aposCrm = quotaService.consultarUso(empresa.getId());
        assertEquals(1, aposCrm.lembretesEnviados());
        assertEquals(149, aposCrm.lembretesDisponiveis());
        assertEquals(1, aposCrm.crmReservados());
    }

    @Test
    void planoSemWhatsAppNaoPermiteReserva() {
        EmpresaEntity empresa = novaEmpresa();
        LocalDate hoje = LocalDate.now();
        assinaturaVigente(empresa, plano("BASICO"), hoje.minusDays(5), hoje.plusDays(25));

        assertEquals(
                WhatsAppReserva.PLANO_SEM_WHATSAPP,
                quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE));
        assertEquals(
                WhatsAppReserva.PLANO_SEM_WHATSAPP,
                quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM));
        assertEquals(
                WhatsAppDisponibilidade.PLANO_SEM_WHATSAPP,
                quotaService.podeReservar(empresa.getId(), WhatsAppCategoriaCota.CRM));

        WhatsAppUsoResponse uso = quotaService.consultarUso(empresa.getId());
        assertEquals(0, uso.limiteLembretes());
        assertEquals(0, uso.limiteCrm());
        assertTrue(usoRepository.findByEmpresaIdAndCicloInicio(
                empresa.getId(), hoje.minusDays(5)).isEmpty());
    }

    @Test
    void empresaSemAssinaturaVigenteEquivaleASemWhatsAppSemInventarCiclo() {
        EmpresaEntity empresa = novaEmpresa();

        assertEquals(
                WhatsAppReserva.PLANO_SEM_WHATSAPP,
                quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM));

        WhatsAppUsoResponse uso = quotaService.consultarUso(empresa.getId());
        assertEquals(0, uso.limiteLembretes());
        assertEquals(0, uso.limiteCrm());
        assertNull(uso.cicloInicio());
    }
}
