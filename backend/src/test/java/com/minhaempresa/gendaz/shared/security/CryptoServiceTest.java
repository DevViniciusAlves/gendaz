package com.minhaempresa.gendaz.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class CryptoServiceTest {

    @Test
    void deveCriptografarEDescriptografarCorretamente() {
        CryptoService service = serviceComChaveValida();
        String encrypted = service.encrypt("dado sensivel");

        assertNotEquals("dado sensivel", encrypted);
        assertEquals("dado sensivel", service.decrypt(encrypted));
    }

    @Test
    void deveGerarCiphertextsDiferentesParaMesmoTexto() {
        CryptoService service = serviceComChaveValida();

        String primeiro = service.encrypt("mesmo texto");
        String segundo = service.encrypt("mesmo texto");

        assertNotEquals(primeiro, segundo);
        assertEquals("mesmo texto", service.decrypt(primeiro));
        assertEquals("mesmo texto", service.decrypt(segundo));
    }

    @Test
    void deveFalharComChaveInvalida() {
        Environment environment = org.mockito.Mockito.mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
        CryptoService service = new CryptoService(environment, Base64.getEncoder().encodeToString(new byte[16]));

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::inicializar);
        assertTrue(ex.getMessage().contains("APP_DATA_ENCRYPTION_KEY invalida"));
    }

    @Test
    void deveFalharEmProdSemChave() {
        Environment environment = org.mockito.Mockito.mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
        CryptoService service = new CryptoService(environment, "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::inicializar);
        assertTrue(ex.getMessage().contains("obrigatoria em prod"));
    }

    private CryptoService serviceComChaveValida() {
        Environment environment = org.mockito.Mockito.mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
        CryptoService service = new CryptoService(environment, Base64.getEncoder().encodeToString(new byte[32]));
        service.inicializar();
        return service;
    }
}
