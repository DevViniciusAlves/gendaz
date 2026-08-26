package com.minhaempresa.gendaz.admin.controller;

import com.minhaempresa.gendaz.security.entity.IpTrackingEntity;
import com.minhaempresa.gendaz.security.repository.IpTrackingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
public class AdminSecurityController {

    private final IpTrackingRepository ipTrackingRepository;

    @GetMapping("/ips-bloqueados")
    public ResponseEntity<List<IpTrackingEntity>> listarIpsBloqueados() {
        List<IpTrackingEntity> ips = ipTrackingRepository.findIpsBloqueados(LocalDateTime.now());
        return ResponseEntity.ok(ips);
    }

    @GetMapping("/ips-suspeitos")
    public ResponseEntity<List<IpTrackingEntity>> listarIpsSuspeitos() {
        List<IpTrackingEntity> ips = ipTrackingRepository.findIpsSuspeitos(LocalDateTime.now().minusHours(1));
        return ResponseEntity.ok(ips);
    }

    @PostMapping("/ip/{ipAddress}/desbloquear")
    public ResponseEntity<?> desbloquearIp(@PathVariable String ipAddress) {
        return ipTrackingRepository.findByIpAddress(ipAddress)
                .map(ip -> {
                    ip.setBloqueado(false);
                    ip.setBloqueadoAte(null);
                    ipTrackingRepository.save(ip);
                    return ResponseEntity.ok(Map.of("mensagem", "IP desbloqueado com sucesso"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/ip/{ipAddress}")
    public ResponseEntity<?> deletarRegistroIp(@PathVariable String ipAddress) {
        return ipTrackingRepository.findByIpAddress(ipAddress)
                .map(ip -> {
                    ipTrackingRepository.delete(ip);
                    return ResponseEntity.ok(Map.of("mensagem", "Registro de IP deletado"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

