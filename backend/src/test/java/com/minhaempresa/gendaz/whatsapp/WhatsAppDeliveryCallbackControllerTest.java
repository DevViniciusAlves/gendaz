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

import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppDeliveryCallbackControllerTest {

    @Mock
    private WhatsAppEntregaService entregaService;

    @Mock
    private HttpServletResponse servletResponse;

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

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido(), servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "delivered"));
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void JA_CONFIRMADO_deveRetornar200() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.JA_CONFIRMADO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido(), servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "duplicate"));
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void AGUARDANDO_NOTIFICACAO_deveRetornar202() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.AGUARDANDO_NOTIFICACAO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido(), servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(202);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "pending"));
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void IGNORADO_deveRetornar200() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.IGNORADO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido(), servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "ignored"));
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void INVALIDO_deveRetornar400() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.INVALIDO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido(), servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("error", "invalid_company_id"));
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void ERRO_TRANSITORIO_deveRetornar503() {
        when(entregaService.registrarEntrega(1L, "WAMID-VALIDO")).thenReturn(WhatsAppEntregaService.ResultadoEntrega.ERRO_TRANSITORIO);

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido(), servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(503);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("error", "service_unavailable"));
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void authorizationAusente_deveRetornar401() {
        ResponseEntity<?> response = controller.delivery(null, requestValido(), servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(401);
        verifyNoInteractions(entregaService);
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void tokenErrado_deveRetornar401() {
        ResponseEntity<?> response = controller.delivery("Bearer errado", requestValido(), servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(401);
        verifyNoInteractions(entregaService);
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void internalTokenNaoConfigurado_deveRetornar503() {
        ReflectionTestUtils.setField(controller, "internalToken", "");

        ResponseEntity<?> response = controller.delivery(AUTH, requestValido(), servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(503);
        verifyNoInteractions(entregaService);
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void companyIdInvalido_deveRetornar400() {
        var request = new WhatsAppDeliveryCallbackController.DeliveryCallbackRequest(
                "abc", "WAMID-VALIDO", "DELIVERED"
        );

        ResponseEntity<?> response = controller.delivery(AUTH, request, servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("error", "invalid_company_id"));
        verifyNoInteractions(entregaService);
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void messageId121chars_deveRetornar400() {
        String messageId = "X".repeat(121);
        var request = new WhatsAppDeliveryCallbackController.DeliveryCallbackRequest("1", messageId, "DELIVERED");

        ResponseEntity<?> response = controller.delivery(AUTH, request, servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("error", "invalid_request"));
        verifyNoInteractions(entregaService);
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void callbackHealth_tokenCorreto_deveRetornar200() {
        ResponseEntity<?> response = controller.callbackHealth(AUTH, servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "ok"));
        verifyNoInteractions(entregaService);
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void callbackHealth_tokenErrado_deveRetornar401() {
        ResponseEntity<?> response = controller.callbackHealth("Bearer errado", servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(401);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "unauthorized"));
        verifyNoInteractions(entregaService);
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }

    @Test
    void callbackHealth_tokenNaoConfigurado_deveRetornar503() {
        ReflectionTestUtils.setField(controller, "internalToken", "");

        ResponseEntity<?> response = controller.callbackHealth(AUTH, servletResponse);

        assertThat(response.getStatusCodeValue()).isEqualTo(503);
        assertThat(response.getBody()).usingRecursiveComparison().isEqualTo(Map.of("status", "service_unavailable"));
        verifyNoInteractions(entregaService);
        verify(servletResponse).setHeader("X-Gendaz-Internal-Handler", "whatsapp-delivery-controller");
    }
}