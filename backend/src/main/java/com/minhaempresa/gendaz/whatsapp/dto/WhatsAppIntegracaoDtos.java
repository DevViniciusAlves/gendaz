package com.minhaempresa.gendaz.whatsapp.dto;

import java.time.LocalDate;

/**
 * Contrato publico da integracao WhatsApp para a UI (Fase 9). Apenas o
 * necessario para a interface: sem token, URL interna, JID, auth state,
 * Signal Keys, caminhos, codigos de disconnect ou stacks.
 */
public final class WhatsAppIntegracaoDtos {
    private WhatsAppIntegracaoDtos() {}

    public record ConexaoResponse(
            String estado,
            boolean hasQr,
            String connectedAt
    ) {}

    public record ConfiguracaoResponse(
            boolean lembretesAtivos
    ) {}

    public record AtualizarConfiguracaoRequest(
            Boolean lembretesAtivos
    ) {}

    public record CategoriaUsoResponse(
            int limite,
            int enviados,
            int reservados,
            int disponiveis
    ) {}

    public record UsoResponse(
            String plano,
            LocalDate cicloInicio,
            LocalDate cicloFim,
            CategoriaUsoResponse lembretes,
            CategoriaUsoResponse crm
    ) {}

    public record ResumoResponse(
            boolean disponivelNoPlano,
            ConexaoResponse conexao,
            ConfiguracaoResponse configuracao,
            UsoResponse uso
    ) {}

    public record QrResponse(
            String qr,
            String updatedAt
    ) {}
}
