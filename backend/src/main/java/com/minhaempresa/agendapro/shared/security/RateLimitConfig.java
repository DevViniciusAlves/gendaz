package com.minhaempresa.agendapro.shared.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RateLimitConfig {

    public static class Limits {
        public static final int LOGIN_PER_HOUR = 5;
        public static final int REGISTRAR_PER_DAY = 3;
        public static final int HORARIOS_PER_MINUTE = 10;
        public static final int API_GERAL_PER_MINUTE = 100;
        public static final int WHATSAPP_WEBHOOK_PER_MINUTE = 50;
    }

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registrarBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> horariosBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> apiBuckets = new ConcurrentHashMap<>();

    public Bucket getLoginBucket(String ip) {
        return loginBuckets.computeIfAbsent(ip, k ->
            Bucket4j.builder()
                .addLimit(Bandwidth.classic(Limits.LOGIN_PER_HOUR, Refill.intervally(Limits.LOGIN_PER_HOUR, Duration.ofHours(1))))
                .build()
        );
    }

    public Bucket getRegistrarBucket(String ip) {
        return registrarBuckets.computeIfAbsent(ip, k ->
            Bucket4j.builder()
                .addLimit(Bandwidth.classic(Limits.REGISTRAR_PER_DAY, Refill.intervally(Limits.REGISTRAR_PER_DAY, Duration.ofDays(1))))
                .build()
        );
    }

    public Bucket getHorariosBucket(Long usuarioId) {
        String key = "horarios:" + usuarioId;
        return horariosBuckets.computeIfAbsent(key, k ->
            Bucket4j.builder()
                .addLimit(Bandwidth.classic(Limits.HORARIOS_PER_MINUTE, Refill.intervally(Limits.HORARIOS_PER_MINUTE, Duration.ofMinutes(1))))
                .build()
        );
    }

    public Bucket getApiBucket(Long usuarioId) {
        String key = "api:" + usuarioId;
        return apiBuckets.computeIfAbsent(key, k ->
            Bucket4j.builder()
                .addLimit(Bandwidth.classic(Limits.API_GERAL_PER_MINUTE, Refill.intervally(Limits.API_GERAL_PER_MINUTE, Duration.ofMinutes(1))))
                .build()
        );
    }
}
