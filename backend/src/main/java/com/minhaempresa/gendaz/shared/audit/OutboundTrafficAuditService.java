package com.minhaempresa.gendaz.shared.audit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OutboundTrafficAuditService {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final boolean enabled;
    private final long summaryIntervalMs;

    private final Map<String, Counter> integrationCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> methodCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> postgresCounters = new ConcurrentHashMap<>();

    public OutboundTrafficAuditService(
            @Value("${app.outbound-audit.enabled:false}") boolean enabled,
            @Value("${app.outbound-audit.summary-interval-ms:600000}") long summaryIntervalMs
    ) {
        this.enabled = enabled;
        this.summaryIntervalMs = summaryIntervalMs;
    }

    public boolean enabled() {
        return enabled;
    }

    public void contarExecucao(String chave) {
        if (!enabled) return;
        methodCounters.computeIfAbsent("EXEC#" + chave, k -> new Counter()).increment(0, 0);
    }

    public void registrarHttp(
            String integracao,
            String urlBase,
            String metodoHttp,
            String origem,
            long bytesEnviados,
            long headersBytes,
            long bytesRecebidos,
            long duracaoMs,
            int statusHttp
    ) {
        if (!enabled) return;
        String time = LocalDateTime.now().format(TS);
        long totalEnviado = Math.max(0L, bytesEnviados) + Math.max(0L, headersBytes);
        integrationCounters.computeIfAbsent(integracao, k -> new Counter()).increment(totalEnviado, Math.max(0L, bytesRecebidos));
        methodCounters.computeIfAbsent(origem, k -> new Counter()).increment(totalEnviado, Math.max(0L, bytesRecebidos));
        log.info(
                "[outbound-audit][HTTP] time={} integration={} urlBase={} method={} caller={} sentBytes={} headersBytes={} receivedBytes={} durationMs={} status={}",
                time,
                integracao,
                urlBase,
                metodoHttp,
                origem,
                totalEnviado,
                Math.max(0L, headersBytes),
                Math.max(0L, bytesRecebidos),
                Math.max(0L, duracaoMs),
                statusHttp
        );
    }

    public void registrarPostgres(String origem, String sql, long bytesAproximados) {
        if (!enabled) return;
        String base = sql == null ? "" : sql.trim().replaceAll("\\s+", " ");
        String comando = base.isBlank() ? "UNKNOWN" : base.split(" ", 2)[0].toUpperCase(Locale.ROOT);
        String chave = origem + " | " + comando;
        postgresCounters.computeIfAbsent(chave, k -> new Counter()).increment(Math.max(0L, bytesAproximados), 0);
        integrationCounters.computeIfAbsent("PostgreSQL", k -> new Counter()).increment(Math.max(0L, bytesAproximados), 0);
    }

    public long bytesUtf8(String texto) {
        if (texto == null || texto.isEmpty()) return 0L;
        return texto.getBytes(StandardCharsets.UTF_8).length;
    }

    public long headersBytes(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return 0L;
        long total = 0L;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            total += bytesUtf8(entry.getKey());
            total += bytesUtf8(entry.getValue());
            total += 4L;
        }
        return total;
    }

    public String sanitizarBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            java.net.URI uri = java.net.URI.create(url);
            StringBuilder builder = new StringBuilder();
            if (uri.getScheme() != null) {
                builder.append(uri.getScheme()).append("://");
            }
            if (uri.getHost() != null) {
                builder.append(uri.getHost());
            }
            if (uri.getPort() > 0) {
                builder.append(":").append(uri.getPort());
            }
            if (uri.getPath() != null) {
                builder.append(uri.getPath());
            }
            return builder.toString();
        } catch (Exception ex) {
            return url.split("\\?", 2)[0];
        }
    }

    public String origem(String classe, String metodo) {
        return classe + "#" + metodo;
    }

    @Scheduled(fixedDelayString = "${app.outbound-audit.summary-interval-ms:600000}")
    public void resumir() {
        if (!enabled) return;
        log.info("[outbound-audit][SUMMARY] intervaloMs={} inicio={}", summaryIntervalMs, LocalDateTime.now().format(TS));
        resumirGrupo("Groq");
        resumirGrupo("Stripe");
        resumirGrupo("Resend");
        resumirGrupo("reCAPTCHA");
        resumirGrupo("PostgreSQL");

        log.info("[outbound-audit][METHODS] top10Bytes={}", topByBytes(methodCounters, 10));
        log.info("[outbound-audit][METHODS] top10Calls={}", topByCalls(methodCounters, 10));
        log.info("[outbound-audit][POSTGRES] top={}", topByBytes(postgresCounters, 10));
    }

    private void resumirGrupo(String integracao) {
        Counter counter = integrationCounters.get(integracao);
        if (counter == null) {
            log.info("[outbound-audit][SUMMARY] {}: chamadas=0 enviados=0 bytes recebidos=0 bytes", integracao);
            return;
        }
        log.info(
                "[outbound-audit][SUMMARY] {}: chamadas={} enviados={} bytes recebidos={} bytes",
                integracao,
                counter.calls.get(),
                counter.sentBytes.get(),
                counter.receivedBytes.get()
        );
    }

    private List<String> topByBytes(Map<String, Counter> source, int limit) {
        return source.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Counter>>comparingLong(e -> e.getValue().sentBytes.get()).reversed())
                .limit(limit)
                .map(e -> e.getKey() + " | calls=" + e.getValue().calls.get() + " | sent=" + e.getValue().sentBytes.get() + " | received=" + e.getValue().receivedBytes.get())
                .toList();
    }

    private List<String> topByCalls(Map<String, Counter> source, int limit) {
        return source.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Counter>>comparingLong(e -> e.getValue().calls.get()).reversed())
                .limit(limit)
                .map(e -> e.getKey() + " | calls=" + e.getValue().calls.get() + " | sent=" + e.getValue().sentBytes.get())
                .toList();
    }

    private static final class Counter {
        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong sentBytes = new AtomicLong();
        private final AtomicLong receivedBytes = new AtomicLong();

        private void increment(long sent, long received) {
            calls.incrementAndGet();
            sentBytes.addAndGet(Math.max(0L, sent));
            receivedBytes.addAndGet(Math.max(0L, received));
        }
    }
}

