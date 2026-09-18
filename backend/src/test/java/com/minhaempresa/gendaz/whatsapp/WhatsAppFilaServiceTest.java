package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppFilaService;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WhatsAppFilaServiceTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    @Autowired
    private WhatsAppFilaService filaService;
    @Autowired
    private EmpresaRepository empresaRepository;

    private EmpresaEntity novaEmpresa(String prefixo) {
        long seq = SEQUENCIA.incrementAndGet();
        return empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia(prefixo + " " + seq)
                .email(prefixo + "-" + seq + "-" + System.nanoTime() + "@teste.com")
                .status(StatusEmpresa.ATIVA)
                .build());
    }

    private String chaveUnica(String prefixo) {
        return prefixo + "-" + SEQUENCIA.incrementAndGet() + "-" + System.nanoTime();
    }

    @Test
    void enfileirarCriaUmaVezComConteudoOperacional() {
        EmpresaEntity empresa = novaEmpresa("wpp-fila");
        LocalDateTime agendado = LocalDateTime.now().plusHours(2);

        WhatsAppNotificacaoEntity criada = filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("fila"), agendado, null, null, "5511999999999", "Lembrete de teste");

        assertNotNull(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.PENDENTE, criada.getStatus());
        assertEquals("5511999999999", criada.getRecipient());
        assertEquals("Lembrete de teste", criada.getMessageBody());
        assertEquals(agendado, criada.getNextAttemptAt());
        assertEquals(0, criada.getAttempts());
    }

    @Test
    void mesmaChaveNaoDuplicaENaoSobrescrevePayload() {
        EmpresaEntity empresa = novaEmpresa("wpp-fila-dup");
        String chave = chaveUnica("filadup");

        WhatsAppNotificacaoEntity primeira = filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.CRM_RESGATE,
                chave, LocalDateTime.now().plusHours(1), null, null, "5511888888888", "Original");
        WhatsAppNotificacaoEntity segunda = filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.CRM_RESGATE,
                chave, LocalDateTime.now().plusHours(1), null, null, "5511777777777", "Diferente");

        assertEquals(primeira.getId(), segunda.getId());
        assertEquals("5511888888888", segunda.getRecipient());
        assertEquals("Original", segunda.getMessageBody());
    }

    @Test
    void empresaDiferentePodeUsarMesmaChave() {
        EmpresaEntity empresaA = novaEmpresa("wpp-fila-a");
        EmpresaEntity empresaB = novaEmpresa("wpp-fila-b");
        String chave = chaveUnica("filashared");

        WhatsAppNotificacaoEntity na = filaService.enfileirar(
                empresaA.getId(), WhatsAppTipoNotificacao.CRM_RECONEXAO,
                chave, LocalDateTime.now().plusHours(1), null, null, "5511888888888", "A");
        WhatsAppNotificacaoEntity nb = filaService.enfileirar(
                empresaB.getId(), WhatsAppTipoNotificacao.CRM_RECONEXAO,
                chave, LocalDateTime.now().plusHours(1), null, null, "5511888888888", "B");

        assertTrue(!na.getId().equals(nb.getId()));
    }

    @Test
    void payloadInvalidoERejeitado() {
        EmpresaEntity empresa = novaEmpresa("wpp-fila-inv");
        LocalDateTime agendado = LocalDateTime.now().plusHours(1);

        assertThrows(IllegalArgumentException.class, () -> filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("i1"), agendado, null, null, "abc", "Texto"));
        assertThrows(IllegalArgumentException.class, () -> filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("i2"), agendado, null, null, "1234567", "Texto"));
        assertThrows(IllegalArgumentException.class, () -> filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("i3"), agendado, null, null, "5511999999999", "   "));
        assertThrows(IllegalArgumentException.class, () -> filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("i4"), agendado, null, null, "5511999999999", "x".repeat(4097)));
        assertThrows(IllegalArgumentException.class, () -> filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("i5"), null, null, null, "5511999999999", "Texto"));
    }
}
