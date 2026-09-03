package com.minhaempresa.gendaz;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class GendazApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Sao_Paulo"));
        SpringApplication.run(GendazApplication.class, args);
    }

    @PostConstruct
    public void validarVariaveisAmbiente() {
        // Corrigido: bloqueia inicializacao com variaveis criticas ausentes ou valores padrao inseguros.
        List<String> variaveisObrigatorias = List.of("JWT_SECRET", "SUPER_ADMIN_PASSWORD");
        List<String> variaveisOpcionais = List.of("CAKTO_CLIENT_SECRET", "PAYMENT_WEBHOOK_SECRET");
        List<String> valoresProibidos = List.of("replace-with-", "example_", "troque-este-", "local-dev-");

        variaveisObrigatorias.forEach(variavel -> {
            String valor = System.getenv(variavel);
            if (valor == null || valor.isBlank()) {
                valor = System.getProperty(variavel);
            }
            if (valor == null || valor.isBlank()) {
                throw new IllegalStateException("Variável de ambiente " + variavel + " não está configurada.");
            }
            validarValorSeguro(variavel, valor, valoresProibidos);
        });

        variaveisOpcionais.forEach(variavel -> {
            String valor = System.getenv(variavel);
            if (valor != null && !valor.isBlank()) {
                validarValorSeguro(variavel, valor, valoresProibidos);
            }
        });
    }

    private void validarValorSeguro(String variavel, String valor, List<String> valoresProibidos) {
        valoresProibidos.forEach(proibido -> {
            if (valor.toLowerCase().contains(proibido.toLowerCase())) {
                throw new IllegalStateException("Variável de ambiente " + variavel + " contém um valor padrão inseguro.");
            }
        });
    }
}

