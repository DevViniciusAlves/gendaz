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
        String normalizado = valor.replaceAll("\\D", "");
        if (normalizado.isBlank()) {
            return null;
        }
        if (!normalizado.startsWith("55")) {
            normalizado = "55" + normalizado;
        }
        return normalizado;
    }
}
