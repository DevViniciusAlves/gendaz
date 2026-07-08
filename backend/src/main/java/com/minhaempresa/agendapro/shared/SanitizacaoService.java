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
        if (!digitos.startsWith("55")) {
            digitos = "55" + digitos;
        }
        if (digitos.length() == 12 && digitos.startsWith("55")) {
            digitos = digitos.substring(0, 4) + "9" + digitos.substring(4);
        }
        if (digitos.length() != 13) {
            return null;
        }
        int ddd = Integer.parseInt(digitos.substring(2, 4));
        if (ddd < 11 || ddd > 99) {
            return null;
        }
        return digitos;
    }
}
