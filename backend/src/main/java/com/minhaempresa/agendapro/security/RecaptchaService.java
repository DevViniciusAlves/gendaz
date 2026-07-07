package com.minhaempresa.agendapro.security;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

    public boolean validarCaptcha(String token) {
        try {
            URL url = new URL(recaptchaVerifyUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            String postData = "secret=" + recaptchaSecretKey + "&response=" + token;

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = postData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                log.warn("[recaptcha] validacao falhou com status {}", statusCode);
                return false;
            }

            Scanner scanner = new Scanner(connection.getInputStream());
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

            boolean success = jsonResponse.get("success").getAsBoolean();
            float score = jsonResponse.has("score") ? jsonResponse.get("score").getAsFloat() : 0.0f;

            log.info("[recaptcha] validacao={} score={}", success, score);

            return success && score >= 0.5f;

        } catch (Exception ex) {
            log.error("[recaptcha] erro ao validar: {}", ex.getMessage());
            return false;
        }
    }
}
