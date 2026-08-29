package com.minhaempresa.gendaz.shared.security;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {
    // Fundacao incremental: nao criptografar e-mail/telefone de login/busca sem migration com hash normalizado e coluna criptografada separada.
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "v1:";

    private final SecureRandom secureRandom = new SecureRandom();
    private final String configuredKey;
    private SecretKeySpec keySpec;

    public CryptoService(Environment environment, @Value("${APP_DATA_ENCRYPTION_KEY:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @PostConstruct
    void inicializar() {
        if (configuredKey == null || configuredKey.isBlank()) {
            return;
        }
        keySpec = new SecretKeySpec(decodeKey(configuredKey), ALGORITHM);
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        SecretKeySpec key = requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Nao foi possivel criptografar dado sensivel.", ex);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        SecretKeySpec key = requireKey();
        if (!cipherText.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Formato de ciphertext invalido.");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("Ciphertext invalido.");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Nao foi possivel descriptografar dado sensivel.", ex);
        }
    }

    private SecretKeySpec requireKey() {
        if (keySpec == null) {
            throw new IllegalStateException("APP_DATA_ENCRYPTION_KEY nao configurada.");
        }
        return keySpec;
    }

    private byte[] decodeKey(String rawKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(rawKey);
            if (decoded.length != KEY_BYTES) {
                throw new IllegalArgumentException("APP_DATA_ENCRYPTION_KEY deve ter 32 bytes em Base64.");
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("APP_DATA_ENCRYPTION_KEY invalida. Use Base64 de 32 bytes.", ex);
        }
    }
}
