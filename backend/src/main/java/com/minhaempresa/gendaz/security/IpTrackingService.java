package com.minhaempresa.gendaz.security;

import com.minhaempresa.gendaz.security.entity.IpTrackingEntity;
import com.minhaempresa.gendaz.security.repository.IpTrackingRepository;
import com.minhaempresa.gendaz.shared.security.SecurityMonitoringService;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IpTrackingService {

    private static final int MAX_TENTATIVAS_POR_HORA = 15;
    private static final int DURACAO_BLOQUEIO_HORAS = 24;

    private final IpTrackingRepository ipTrackingRepository;
    private final SecurityMonitoringService securityMonitoringService;

    public void registrarTentativaFalhada(String ip) {
        if (ip == null || ip.isBlank()) return;

        Optional<IpTrackingEntity> existente = ipTrackingRepository.findByIpAddress(ip);
        IpTrackingEntity tracking;

        if (existente.isPresent()) {
            tracking = existente.get();

            if (tracking.getUltimoAcesso() != null
                    && tracking.getUltimoAcesso().plusHours(1).isBefore(LocalDateTime.now())) {
                tracking.setTentativasFalhadas(0);
            }

            tracking.setTentativasFalhadas(tracking.getTentativasFalhadas() + 1);
        } else {
            tracking = new IpTrackingEntity();
            tracking.setIpAddress(ip);
            tracking.setTentativasFalhadas(1);
        }

        tracking.setUltimoAcesso(LocalDateTime.now());

        if (tracking.getTentativasFalhadas() >= MAX_TENTATIVAS_POR_HORA) {
            tracking.setBloqueado(true);
            tracking.setBloqueadoAte(LocalDateTime.now().plusHours(DURACAO_BLOQUEIO_HORAS));
            tracking.setMotivoBloqueio("Muitas solicitacoes suspeitas de login");
            log.warn("[ip-tracking] IP {} bloqueado por {}h", ip, DURACAO_BLOQUEIO_HORAS);
            securityMonitoringService.registrarEvento(
                    "IP_BLOQUEADO_LOGIN_FALHADO",
                    "HIGH",
                    ip,
                    null,
                    "/api/auth/login",
                    "-",
                    "tentativas=" + tracking.getTentativasFalhadas()
            );
        } else if (tracking.getTentativasFalhadas() >= 3) {
            securityMonitoringService.registrarEvento(
                    "LOGIN_FALHADO_REPETIDO_IP",
                    "MEDIUM",
                    ip,
                    null,
                    "/api/auth/login",
                    "-",
                    "tentativas=" + tracking.getTentativasFalhadas()
            );
        }

        ipTrackingRepository.save(tracking);
    }

    public void registrarTentativaBemsucedida(String ip) {
        if (ip == null || ip.isBlank()) return;

        Optional<IpTrackingEntity> existente = ipTrackingRepository.findByIpAddress(ip);

        if (existente.isPresent()) {
            IpTrackingEntity tracking = existente.get();
            tracking.setTentativasFalhadas(0);
            tracking.setUltimoAcesso(LocalDateTime.now());
            ipTrackingRepository.save(tracking);
            log.info("[ip-tracking] IP {} reset de tentativas apos login bem-sucedido", ip);
        }
    }

    public boolean estaIpBloqueado(String ip) {
        if (ip == null || ip.isBlank()) return false;

        Optional<IpTrackingEntity> existente = ipTrackingRepository.findByIpAddress(ip);

        if (existente.isPresent()) {
            IpTrackingEntity tracking = existente.get();
            if (tracking.estaBloqueado()) {
                log.warn("[ip-tracking] acesso negado para IP bloqueado: {}", ip);
                return true;
            }
        }

        return false;
    }

    public long getMinutosRestantesDesbloqueio(String ip) {
        if (ip == null || ip.isBlank()) return 0;

        Optional<IpTrackingEntity> existente = ipTrackingRepository.findByIpAddress(ip);

        if (existente.isPresent()) {
            IpTrackingEntity tracking = existente.get();
            if (tracking.getBloqueadoAte() != null) {
                return java.time.temporal.ChronoUnit.MINUTES
                        .between(LocalDateTime.now(), tracking.getBloqueadoAte());
            }
        }

        return 0;
    }
}

