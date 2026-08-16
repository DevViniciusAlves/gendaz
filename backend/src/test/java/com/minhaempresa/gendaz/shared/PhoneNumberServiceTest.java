package com.minhaempresa.gendaz.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhoneNumberServiceTest {

    private PhoneNumberService service;

    @BeforeEach
    void setUp() {
        service = new PhoneNumberService();
    }

    // ---- Brasil ----

    @Test
    void brasilNacionalSemDDI() {
        assertEquals("5565993360341", service.normalizarObrigatorio("(65) 99336-0341"));
    }

    @Test
    void brasilInternacional() {
        assertEquals("5565993360341", service.normalizarObrigatorio("+55 65 99336-0341"));
    }

    @Test
    void brasilCanonicoComMais() {
        assertEquals("5565993360341", service.normalizarObrigatorio("+5565993360341"));
    }

    @Test
    void brasilCompatibilidadeLegadaSoDigitosSemMais() {
        assertEquals("5565993360341", service.normalizarObrigatorio("65993360341"));
    }

    // ---- Internacional ----

    @Test
    void estadosUnidos() {
        assertEquals("14155552671", service.normalizarObrigatorio("+1 415 555 2671"));
    }

    @Test
    void reinoUnido() {
        assertEquals("442079460018", service.normalizarObrigatorio("+44 20 7946 0018"));
    }

    @Test
    void argentina() {
        assertEquals("541141234567", service.normalizarObrigatorio("+54 11 4123-4567"));
    }

    @Test
    void japao() {
        assertEquals("81312345678", service.normalizarObrigatorio("+81 3 1234 5678"));
    }

    @Test
    void portugal() {
        assertEquals("351912345678", service.normalizarObrigatorio("+351 912 345 678"));
    }

    // ---- Obrigatório ----

    @Test
    void obrigatorioNulo() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.normalizarObrigatorio(null));
        assertEquals("Telefone é obrigatório.", ex.getMessage());
    }

    @Test
    void obrigatorioVazio() {
        assertThrows(BusinessException.class, () -> service.normalizarObrigatorio(""));
    }

    @Test
    void obrigatorioSomenteEspacos() {
        assertThrows(BusinessException.class, () -> service.normalizarObrigatorio("   "));
    }

    // ---- Opcional ----

    @Test
    void opcionalVazioRetornaNull() {
        assertNull(service.normalizarOpcional(null));
        assertNull(service.normalizarOpcional(""));
        assertNull(service.normalizarOpcional("   "));
    }

    @Test
    void opcionalPreenchidoValidoNormaliza() {
        assertEquals("5565993360341", service.normalizarOpcional("+55 65 99336-0341"));
    }

    @Test
    void opcionalPreenchidoInvalidoLancaErro() {
        assertThrows(BusinessException.class, () -> service.normalizarOpcional("abc"));
    }

    // ---- Inválidos ----

    @Test
    void textoAleatorioInvalido() {
        assertThrows(BusinessException.class, () -> service.normalizarObrigatorio("abcdefg"));
    }

    @Test
    void numeroCurtoInvalido() {
        assertThrows(BusinessException.class, () -> service.normalizarObrigatorio("+55 65 1"));
    }

    @Test
    void numeroImpossivelParaOPaisInvalido() {
        assertThrows(BusinessException.class, () -> service.normalizarObrigatorio("+55 11 00000-0000"));
    }

    @Test
    void nuncaLancaNumberParseException() {
        // Entradas que antes soltavam NumberParseException não podem escapar.
        assertThrows(BusinessException.class, () -> service.normalizarObrigatorio("+"));
        assertThrows(BusinessException.class, () -> service.normalizarObrigatorio("(abc)"));
        assertThrows(BusinessException.class, () -> service.normalizarObrigatorio("agendar"));
    }

    // ---- valido() ----

    @Test
    void validoAceitaNumerosVálidos() {
        assertTrue(service.valido("+55 65 99336-0341"));
        assertTrue(service.valido("+1 415 555 2671"));
    }

    @Test
    void validoRejeitaNulosVaziosEInvalidos() {
        assertFalse(service.valido(null));
        assertFalse(service.valido(""));
        assertFalse(service.valido("123"));
        assertFalse(service.valido("abc"));
    }

    // ---- Formatação e conversão ----

    @Test
    void paraE164AdicionaMais() {
        assertEquals("+5565993360341", service.paraE164("5565993360341"));
        assertEquals("+14155552671", service.paraE164("14155552671"));
    }

    @Test
    void paraE164NuloRetornaNull() {
        assertNull(service.paraE164(null));
        assertNull(service.paraE164(""));
    }

    @Test
    void formatarExibicaoBrasil() {
        assertEquals("+55 65 99336-0341", service.formatarExibicao("5565993360341"));
    }

    @Test
    void formatarExibicaoEua() {
        assertEquals("+1 415-555-2671", service.formatarExibicao("14155552671"));
    }

    @Test
    void formatarExibicaoNuloRetornaNull() {
        assertNull(service.formatarExibicao(null));
        assertNull(service.formatarExibicao(""));
    }
}
