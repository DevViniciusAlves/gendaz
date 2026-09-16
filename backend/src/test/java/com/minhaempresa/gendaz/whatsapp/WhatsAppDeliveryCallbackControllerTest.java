package com.minhaempresa.gendaz.whatsapp.controller;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.minhaempresa.gendaz.whatsapp.service.WhatsAppEntregaService;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

// Use lenient stubbing via Mockito configuration
@ExtendWith(MockitoExtension.class)
class WhatsAppDeliveryCallbackControllerTest {

    @Mock
    private WhatsAppEntregaService entregaService;

    @InjectMocks
    private WhatsAppDeliveryCallbackController controller;

    @BeforeEach
    void setUp() {
        // Set the internal token field directly
        try {
            var field = WhatsAppDeliveryCallbackController.class.getDeclaredField("internalToken");
            field.setAccessible(true);
            field.set(controller, "test-token");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void CONFIRMADO_deveRetornar200() {
        when(entregaService.registrarEntrega(anyLong(), anyString())).thenReturn(WhatsAppEntregaService.ResultadoEntrega.CONFIRMADO);

        ResponseEntity<?> response = controller.delivery(null, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "delivered"));
    }

    @Test
    void JA_CONFIRMADO_deveRetornar200() {
        when(entregaService.registrarEntrega(anyLong(), anyString())).thenReturn(WhatsAppEntregaService.ResultadoEntrega.JA_CONFIRMADO);

        ResponseEntity<?> response = controller.delivery(null, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "duplicate"));
    }

    @Test
    void AGUARDANDO_NOTIFICACAO_deveRetornar202() {
        when(entregaService.registrarEntrega(anyLong(), anyString())).thenReturn(WhatsAppEntregaService.ResultadoEntrega.AGUARDANDO_NOTIFICACAO);

        ResponseEntity<?> response = controller.delivery(null, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(202);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "pending"));
    }

    @Test
    void IGNORADO_deveRetornar200() {
        when(entregaService.registrarEntrega(anyLong(), anyString())).thenReturn(WhatsAppEntregaService.ResultadoEntrega.IGNORADO);

        ResponseEntity<?> response = controller.delivery(null, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "ignored"));
    }

    @Test
    void INVALIDO_deveRetornar400() {
        when(entregaService.registrarEntrega(anyLong(), anyString())).thenReturn(WhatsAppEntregaService.ResultadoEntrega.INVALIDO);

        ResponseEntity<?> response = controller.delivery(null, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("error", "invalid_company_id"));
    }

    @Test
    void ERRO_TRANSITORIO_deveRetornar503() {
        when(entregaService.registrarEntrega(anyLong(), anyString())).thenReturn(WhatsAppEntregaService.ResultadoEntrega.ERRO_TRANSITORIO);

        ResponseEntity<?> response = controller.delivery(null, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(503);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("error", "service_unavailable"));
    }

    @Test
    void tokenErrado_deveRetornar401() {
        when(entregaService.registrarEntrega(anyLong(), anyString())).thenReturn(WhatsAppEntregaService.ResultadoEntrega.CONFIRMADO);

        ResponseEntity<?> response = controller.delivery("token-errado", null);

        assertThat(response.getStatusCodeValue()).isEqualTo(401);
    }

    @Test
    void companyIdInvalido_deveRetornar400() {
        when(entregaService.registrarEntrega(anyLong(), anyString())).thenReturn(WhatsAppEntregaService.ResultadoEntrega.CONFIRMADO);

        ResponseEntity<?> response = controller.delivery("Bearer tok", null);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
    }

    @Test
    void messageIdInvalido_deveRetornar400() {
        when(entregaService.registrarEntrega(anyLong(), anyString())).thenReturn(WhatsAppEntregaService.ResultadoEntrega.CONFIRMADO);

        var request = new WhatsAppDeliveryCallbackController.DeliveryCallbackRequest("empresa-1", "short-msg", "DELIVERED");
        ResponseEntity<?> response = controller.delivery("Bearer tok", request);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
    }
}