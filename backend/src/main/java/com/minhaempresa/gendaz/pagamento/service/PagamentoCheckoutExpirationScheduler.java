package com.minhaempresa.gendaz.pagamento.service;

import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoCheckoutExpirationScheduler {

    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final PagamentoService pagamentoService;

    @Scheduled(fixedDelay = 60000) // Executa a cada 1 minuto
    public void expirarCheckoutsVencidos() {
        var agora = LocalDateTime.now();
        var vencidos = pagamentoPlanoRepository.findByStatusAndDataExpiracaoBefore(StatusPagamento.PAYMENT_PENDING, agora);
        
        if (vencidos.isEmpty()) {
            return;
        }

        log.info("Scheduler processando {} checkouts vencidos", vencidos.size());
        for (var pagamento : vencidos) {
            try {
                pagamentoService.expirarCheckoutPorTimeout(pagamento.getId());
            } catch (Exception e) {
                log.error("Erro ao expirar checkout {}", pagamento.getId(), e);
            }
        }
    }
}
