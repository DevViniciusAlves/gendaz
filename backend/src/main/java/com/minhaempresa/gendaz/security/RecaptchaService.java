package com.minhaempresa.gendaz.security;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minhaempresa.gendaz.shared.audit.OutboundTrafficAuditService;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RecaptchaService {

    @Value("${recaptcha.secret-key}")
    private String recaptchaSecretKey;

    @Value("${recaptcha.verify-url}")
    private String recaptchaVerifyUrl;

    private final OutboundTrafficAuditService auditService;

    public RecaptchaService(OutboundTrafficAuditService auditService) {
        this.auditService = auditService;
    }

    public boolean validarCaptcha(String token) {
        auditService.contarExecucao("RecaptchaService#validarCaptcha");
        try {
            URL url = new URL(recaptchaVerifyUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            String postData = "secret=" + recaptchaSecretKey + "&response=" + token;

            long inicio = System.currentTimeMillis();
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = connection.getResponseCode();
            String responseBody = "";
            InputStream responseStream = statusCode >= 200 && statusCode < 400 ? connection.getInputStream() : connection.getErrorStream();
            if (responseStream != null) {
                try (Scanner scanner = new Scanner(responseStream, StandardCharsets.UTF_8)) {
                    if (scanner.hasNext()) {
                        responseBody = scanner.useDelimiter("\\A").next();
                    }
                }
            }
            auditService.registrarHttp(
                    "reCAPTCHA",
                    auditService.sanitizarBaseUrl(recaptchaVerifyUrl),
                    "POST",
                    auditService.origem("RecaptchaService", "validarCaptcha"),
                    auditService.bytesUtf8(postData),
                    auditService.headersBytes(java.util.Map.of("Content-Type", "application/x-www-form-urlencoded")),
                    auditService.bytesUtf8(responseBody),
                    System.currentTimeMillis() - inicio,
                    statusCode
            );
            if (statusCode != 200) {
                log.warn("[recaptcha] validacao falhou com status {}", statusCode);
                return false;
            }

            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

            boolean success = jsonResponse.get("success").getAsBoolean();
            float score = jsonResponse.has("score") ? jsonResponse.get("score").getAsFloat() : 0.0f;

            log.info("[recaptcha] validacao={} score={}", success, score);

            return success && score >= 0.5f;

        } catch (Exception ex) {
            log.error("[recaptcha] erro ao validar. erroTipo={}", ex.getClass().getSimpleName());
            return false;
        }
    }
}

