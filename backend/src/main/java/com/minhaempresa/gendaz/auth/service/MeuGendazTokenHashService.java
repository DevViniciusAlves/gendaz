package com.minhaempresa.gendaz.auth.service;

import com.minhaempresa.gendaz.auth.config.MeuGendazSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class MeuGendazTokenHashService {
    private final MeuGendazSecurityProperties properties;

    public MeuGendazTokenHashService(MeuGendazSecurityProperties properties) {
        this.properties = properties;
    }

    public String hashOtp(String codigo, String email, Long empresaId) {
        return hmac("otp:" + empresaId + ":" + email + ":" + codigo);
    }

    public String hashToken(String token) {
        return hmac("token:" + token);
    }

    public boolean matches(String esperado, String calculado) {
        if (esperado == null || calculado == null) {
            return false;
        }
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), calculado.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String valor) {
        try {
            String secret = properties.getOtp().getSecret();
            if (secret == null || secret.isBlank() || secret.startsWith("${")) {
                secret = System.getenv("MEU_GENDAZ_OTP_SECRET");
            }
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException("MEU_GENDAZ_OTP_SECRET não configurado.");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel gerar hash seguro.", e);
        }
    }
}
