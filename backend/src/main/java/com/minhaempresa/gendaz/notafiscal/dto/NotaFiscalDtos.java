package com.minhaempresa.gendaz.notafiscal.dto;

import com.minhaempresa.gendaz.notafiscal.enums.StatusNotaFiscal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class NotaFiscalDtos {
    private NotaFiscalDtos() {}

    public record EmitirNotaFiscalRequest(
            @NotNull Long clienteId,
            @NotNull Long empresaId,
            @NotNull @Positive @DecimalMax(value = "999999.99", message = "Valor deve ser menor ou igual a 999999.99.") BigDecimal valor
    ) {}

    public record NotaFiscalResponse(
            Long id,
            Long clienteId,
            String clienteNome,
            Long empresaId,
            BigDecimal valor,
            StatusNotaFiscal status,
            String numeroFake,
            LocalDateTime dataEmissao
    ) {}
}

