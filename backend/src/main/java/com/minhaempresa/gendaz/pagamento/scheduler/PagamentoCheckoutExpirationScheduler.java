package com.minhaempresa.gendaz.pagamento.scheduler;

import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PagamentoCheckoutExpirationScheduler {

    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final PagamentoService pagamentoService;

    @Scheduled(fixedDelay = 60000) // Executa a cada 1 minuto
    public void expirarCheckoutsVencidos() {
        log.debug("Iniciando limpeza de checkouts expirados...");
        
        LocalDateTime agora = LocalDateTime.now();
        List<PagamentoPlanoEntity> vencidos = pagamentoPlanoRepository.findByStatusAndDataExpiracaoBefore(
                StatusPagamento.PAYMENT_PENDING, agora);

        if (vencidos.isEmpty()) {
            return;
        }

        log.info("Encontrados {} checkouts pendentes com prazo vencido. Processando expiração...", vencidos.size());

        for (PagamentoPlanoEntity pagamento : vencidos) {
            try {
                pagamentoService.expirarCheckoutPorTimeout(pagamento);
            } catch (Exception ex) {
                log.error("Erro ao expirar checkout id={}. erroTipo={}", pagamento.getId(), ex.getClass().getSimpleName());
            }
        }
        
        log.info("Processamento de expiração de checkouts concluído.");
    }
}
