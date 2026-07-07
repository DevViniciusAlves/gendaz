package com.minhaempresa.agendapro.shared;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.minhaempresa.agendapro.empresa.enums.TipoDocumento;
import org.junit.jupiter.api.Test;

class DocumentoUtilsTest {

    @Test
    void deveValidarCpfVazioOuRepetidoComoInvalido() {
        assertThrows(BusinessException.class, () -> DocumentoUtils.validar(TipoDocumento.CPF, "00000000000"));
    }

    @Test
    void deveValidarCpfReal() {
        assertDoesNotThrow(() -> DocumentoUtils.validar(TipoDocumento.CPF, "52998224725"));
    }

    @Test
    void deveValidarCnpjReal() {
        assertDoesNotThrow(() -> DocumentoUtils.validar(TipoDocumento.CNPJ, "11444777000161"));
    }
}
