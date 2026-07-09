package com.minhaempresa.agendapro.manutencao.service;

// ⚠️ DESATIVADO — Esta classe é exclusiva para manutenção de mensagens WhatsApp.
// ⚠️ DESATIVADO — Todos os métodos estão desativados. Não utilizar em produção.

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappMessageEntity;
import com.minhaempresa.agendapro.whatsapp.repository.WhatsappMessageRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappMessageMaintenanceService {
    private static final int LOTE_MAXIMO = 1000;

    private final WhatsappMessageRepository messageRepository;

    @Scheduled(cron = "${whatsapp.messages-cleanup-cron:0 0 3 * * *}")
    public void limparMensagensAntigasAgendada() {
        // ⚠️ DESATIVADO — try {
        // ⚠️ DESATIVADO —     long removidas = limparMensagensAntigas(30);
        // ⚠️ DESATIVADO —     log.info("[whatsapp-maintenance] limpeza agendada concluida totalRemovido={}", removidas);
        // ⚠️ DESATIVADO — } catch (Exception ex) {
        // ⚠️ DESATIVADO —     log.warn("[whatsapp-maintenance] limpeza agendada falhou: {}", ex.getMessage());
        // ⚠️ DESATIVADO — }
    }

    public long limparMensagensAntigas(int dias) {
        // ⚠️ DESATIVADO — int diasValidos = Math.max(dias, 1);
        // ⚠️ DESATIVADO — LocalDateTime corte = LocalDateTime.now().minusDays(diasValidos);
        // ⚠️ DESATIVADO — long totalRemovido = 0L;
        //
        // ⚠️ DESATIVADO — while (true) {
        // ⚠️ DESATIVADO —     List<WhatsappMessageEntity> lote = messageRepository
        // ⚠️ DESATIVADO —             .findByCreatedAtBeforeOrderByCreatedAtAsc(corte, PageRequest.of(0, LOTE_MAXIMO, Sort.by(Sort.Direction.ASC, "createdAt")))
        // ⚠️ DESATIVADO —             .getContent();
        //
        // ⚠️ DESATIVADO —     if (lote.isEmpty()) {
        // ⚠️ DESATIVADO —         break;
        // ⚠️ DESATIVADO —     }
        //
        // ⚠️ DESATIVADO —     messageRepository.deleteAllInBatch(lote);
        // ⚠️ DESATIVADO —     totalRemovido += lote.size();
        //
        // ⚠️ DESATIVADO —     log.info("[whatsapp-maintenance] lote removido totalParcial={} corte={} dias={} loteMaximo={}",
        // ⚠️ DESATIVADO —             totalRemovido, corte, diasValidos, LOTE_MAXIMO);
        //
        // ⚠️ DESATIVADO —     if (lote.size() < LOTE_MAXIMO) {
        // ⚠️ DESATIVADO —         break;
        // ⚠️ DESATIVADO —     }
        // ⚠️ DESATIVADO — }
        //
        // ⚠️ DESATIVADO — log.info("[whatsapp-maintenance] limpeza concluida totalRemovido={} dias={} corte={}",
        // ⚠️ DESATIVADO —         totalRemovido, diasValidos, corte);
        return 0L;
    }
}
