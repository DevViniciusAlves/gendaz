package com.minhaempresa.agendapro.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordServiceTest {
    private final PasswordService passwordService = new PasswordService();

    @Test
    void deveGerarHashBCrypt() {
        String hash = passwordService.hash("SenhaForte123!");

        assertTrue(hash.startsWith("$2"));
        assertNotEquals("SenhaForte123!", hash);
        assertTrue(passwordService.matches("SenhaForte123!", hash));
        assertFalse(passwordService.matches("SenhaErrada123!", hash));
    }
}
