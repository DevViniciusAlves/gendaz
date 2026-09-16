package com.minhaempresa.gendaz.whatsapp.controller;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.minhaempresa.gendaz.whatsapp.service.WhatsAppEntregaService;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppDeliveryCallbackControllerTest {

    @Mock
    private WhatsAppEntregaService entregaService;

    @InjectMocks
    private WhatsAppDeliveryCallbackController controller;

    private static final String AUTH = "Bearer test-token";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "internalToken", "test-token");
    }

    private WhatsAppDeliveryCallbackController.DeliveryCallbackRequest requestValido() {
        return new WhatsAppDeliveryCallbackController.DeliveryCallbackRequest(
                "1",
                "WAMID-VALIDO",
                "DELIVERED"
        );
    }

    @Test
    void CONFIRMADO_deveRetornar200() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.CONFIRMADO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido());

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "delivered"));
    }

    @Test
    void JA_CONFIRMADO_deveRetornar200() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.JA_CONFIRMADO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido());

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "duplicate"));
    }

    @Test
    void AGUARDANDO_NOTIFICACAO_deveRetornar202() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.AGUARDANDO_NOTIFICACAO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido());

        assertThat(response.getStatusCodeValue()).isEqualTo(202);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "pending"));
    }

    @Test
    void IGNORADO_deveRetornar200() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.IGNORADO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido());

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "ignored"));
    }

    @Test
    void INVALIDO_deveRetornar400() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.INVALIDO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido());

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("error", "invalid_company_id"));
    }

    @Test
    void ERRO_TRANSITORIO_deveRetornar503() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.ERRO_TRANSITORIO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido());

        assertThat(response.getStatusCodeValue()).isEqualTo(503);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("error", "service_unavailable"));
    }

    @Test
    void authorizationAusente_deveRetornar401() {
        ResponseEntity<?> response = controller.delivery(null, requestValido());

        assertThat(response.getStatusCodeValue()).isEqualTo(401);
        verifyNoInteractions(entregaService);
    }

    @Test
    void tokenErrado_deveRetornar401() {
        ResponseEntity<?> response = controller.delivery("Bearer errado", requestValido());

        assertThat(response.getStatusCodeValue()).isEqualTo(401);
        verifyNoInteractions(entregaService);
    }

    @Test
    void internalTokenNaoConfigurado_deveRetornar503() {
        ReflectionTestUtils.setField(controller, "internalToken", "");

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido());

        assertThat(response.getStatusCodeValue()).isEqualTo(503);
        verifyNoInteractions(entregaService);
    }

    @Test
    void companyIdInvalido_deveRetornar400() {
        var request = new WhatsAppDeliveryCallbackController.DeliveryCallbackRequest(
                "abc", "WAMID-VALIDO", "DELIVERED"
        );

        ResponseEntity<?> response = controller.delivery(AUTH, request);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("error", "invalid_company_id"));
        verifyNoInteractions(entregaService);
    }

    @Test
    void messageId121chars_deveRetornar400() {
        String messageId = "X".repeat(121);
        var request = new WhatsAppDeliveryCallbackController.DeliveryCallbackRequest("1", messageId, "DELIVERED");

        ResponseEntity<?> response = controller.delivery(AUTH, request);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("error", "invalid_request"));
        verifyNoInteractions(entregaService);
    }
}
