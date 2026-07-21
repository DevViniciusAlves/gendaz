package com.minhaempresa.agendapro.shared;

import org.owasp.encoder.Encode;
import org.springframework.stereotype.Component;

@Component
public class SanitizacaoService {
    public String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim().replaceAll("\\s+", " ");
        return normalizado.isBlank() ? null : Encode.forHtmlContent(normalizado);
    }

    public String textoObrigatorio(String valor) {
        String sanitizado = texto(valor);
        return sanitizado == null ? "" : sanitizado;
    }

    public String email(String valor) {
        String sanitizado = texto(valor);
        return sanitizado == null ? null : sanitizado.toLowerCase();
    }

    public String telefone(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return null;
        }
        if (digitos.length() < 10 || digitos.length() > 14) {
            return null;
        }
        return digitos;
    }
}
