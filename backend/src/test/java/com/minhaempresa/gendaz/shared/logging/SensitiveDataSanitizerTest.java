package com.minhaempresa.gendaz.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

class SensitiveDataSanitizerTest {

    @Test
    void mascaraEmailCredenciaisCookieJwtOtpTelefoneEQuebraDeLinha() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature";
        String entrada = "email=cliente.real@example.com "
                + "senha=MinhaSenha123 token=token-secreto Authorization=Bearer abc.def-123 "
                + "cookie=Gendaz_session=valor "
                + "payload={\"otp\":\"938271\",\"telefone\":\"+5565999999999\"} "
                + "jwt=" + jwt + "\r\nlinha-injetada";

        String resultado = SensitiveDataSanitizer.sanitize(entrada);

        assertThat(resultado)
                .doesNotContain("cliente.real@example.com")
                .doesNotContain("MinhaSenha123")
                .doesNotContain("token-secreto")
                .doesNotContain("abc.def-123")
                .doesNotContain("valor")
                .doesNotContain("938271")
                .doesNotContain("+5565999999999")
                .doesNotContain(jwt)
                .doesNotContain("\r", "\n", "\t")
                .contains("***");
    }

    @Test
    void mascaraSegredosEmQueryStringSemRemoverContextoOperacional() {
        String resultado = SensitiveDataSanitizer.sanitize(
                "empresaId=42 status=401 path=/callback?token=abc123&codigo=999999");

        assertThat(resultado)
                .contains("empresaId=42", "status=401", "path=/callback?token=***&codigo=***")
                .doesNotContain("abc123", "999999");
    }

    @Test
    void aceitaValorNulo() {
        assertThat(SensitiveDataSanitizer.sanitize(null)).isNull();
    }

    @Test
    void converterMascaraMensagemFormatadaAntesDeEnviarAoAppender() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn(
                "login email=cliente@example.com Authorization=Bearer segredo");

        String resultado = new MaskingConverter().convert(event);

        assertThat(resultado)
                .isEqualTo("login email=*** Authorization=***")
                .doesNotContain("cliente@example.com", "segredo");
    }
}
