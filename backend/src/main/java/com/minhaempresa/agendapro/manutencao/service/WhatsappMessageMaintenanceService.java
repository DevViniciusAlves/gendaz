package com.minhaempresa.agendapro.manutencao.service;

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
        try {
            long removidas = limparMensagensAntigas(30);
            log.info("[whatsapp-maintenance] limpeza agendada concluida totalRemovido={}", removidas);
        } catch (Exception ex) {
            log.warn("[whatsapp-maintenance] limpeza agendada falhou: {}", ex.getMessage());
        }
    }

    public long limparMensagensAntigas(int dias) {
        int diasValidos = Math.max(dias, 1);
        LocalDateTime corte = LocalDateTime.now().minusDays(diasValidos);
        long totalRemovido = 0L;

        while (true) {
            List<WhatsappMessageEntity> lote = messageRepository
                    .findByCreatedAtBeforeOrderByCreatedAtAsc(corte, PageRequest.of(0, LOTE_MAXIMO, Sort.by(Sort.Direction.ASC, "createdAt")))
                    .getContent();

            if (lote.isEmpty()) {
                break;
            }

            messageRepository.deleteAllInBatch(lote);
            totalRemovido += lote.size();

            log.info("[whatsapp-maintenance] lote removido totalParcial={} corte={} dias={} loteMaximo={}",
                    totalRemovido, corte, diasValidos, LOTE_MAXIMO);

            if (lote.size() < LOTE_MAXIMO) {
                break;
            }
        }

        log.info("[whatsapp-maintenance] limpeza concluida totalRemovido={} dias={} corte={}",
                totalRemovido, diasValidos, corte);
        return totalRemovido;
    }
}
