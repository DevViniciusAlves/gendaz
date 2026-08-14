package com.minhaempresa.gendaz.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PersistentRateLimitService {
    private final SecurityRateLimitEntryRepository repository;

    @Transactional
    public void consumir(String scope, int limite, Duration janela, Duration bloqueio) {
        if (limite <= 0 || janela == null || janela.isZero() || janela.isNegative()) {
            throw new IllegalArgumentException("Configuracao de rate limit invalida.");
        }
        LocalDateTime agora = LocalDateTime.now();
        String scopeKey = hashScope(scope);
        SecurityRateLimitEntryEntity entry = repository.findByScopeKeyForUpdate(scopeKey)
                .orElseGet(() -> SecurityRateLimitEntryEntity.builder()
                        .scopeKey(scopeKey)
                        .janelaInicio(agora)
                        .quantidade(0)
                        .expiraEm(agora.plus(janela).plus(bloqueio == null ? Duration.ZERO : bloqueio))
                        .build());

        if (entry.getBloqueadoAte() != null && agora.isBefore(entry.getBloqueadoAte())) {
            throw tooManyRequests(Duration.between(agora, entry.getBloqueadoAte()).getSeconds());
        }

        if (entry.getJanelaInicio() == null || !agora.isBefore(entry.getJanelaInicio().plus(janela))) {
            entry.setJanelaInicio(agora);
            entry.setQuantidade(0);
            entry.setBloqueadoAte(null);
        }

        entry.setQuantidade(entry.getQuantidade() + 1);
        entry.setExpiraEm(entry.getJanelaInicio().plus(janela).plus(bloqueio == null ? Duration.ofMinutes(5) : bloqueio));

        if (entry.getQuantidade() > limite) {
            Duration bloqueioEfetivo = bloqueio == null || bloqueio.isZero() || bloqueio.isNegative() ? janela : bloqueio;
            entry.setBloqueadoAte(agora.plus(bloqueioEfetivo));
            repository.save(entry);
            throw tooManyRequests(bloqueioEfetivo.getSeconds());
        }

        repository.save(entry);
    }

    @Transactional
    public int limparExpirados(LocalDateTime limite) {
        return repository.deleteExpiredBefore(limite);
    }

    private ResponseStatusException tooManyRequests(long retryAfterSeconds) {
        return new RateLimitExceededException("Muitas tentativas. Aguarde um momento e tente novamente.", Math.max(1, retryAfterSeconds));
    }

    private String hashScope(String scope) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.valueOf(scope).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel calcular chave de rate limit.", e);
        }
    }
}
