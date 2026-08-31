package com.minhaempresa.gendaz.assinatura.scheduler;

import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssinaturaExpirationScheduler {

    private final AssinaturaRepository assinaturaRepository;
    private final AssinaturaService assinaturaService;

    @Scheduled(
            cron = "0 5 * * * *",
            zone = "America/Sao_Paulo"
    )
    @Transactional
    public void processarExpiracaoAutomatica() {
        LocalDate hoje = LocalDate.now();

        List<Long> empresasComExpiracao = assinaturaRepository.findEmpresasComAssinaturaVencida(
                List.of(StatusAssinatura.ATIVA, StatusAssinatura.TESTE), hoje);

        log.info("Processando expiracao automática para {} empresas com assinaturas vencidas", empresasComExpiracao.size());

        for (Long empresaId : empresasComExpiracao) {
            try {
                assinaturaService.processarExpiracaoDaEmpresa(empresaId, hoje);
                log.info("Expiracao processada para empresa id={}", empresaId);
            } catch (Exception ex) {
                log.error("Erro ao processar expiracao da empresa id={}. erroTipo={}", empresaId, ex.getClass().getSimpleName());
            }
        }

        log.info("Processamento automatico de expiracao concluido.");
    }
}