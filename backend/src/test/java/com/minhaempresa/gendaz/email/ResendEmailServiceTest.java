package com.minhaempresa.gendaz.email;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResendEmailServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deveRetornarFalseQuandoApiKeyVazia() {
        var service = new ResendEmailService(objectMapper, "", "from@test.com", "Test", "https://test.com", "admin@test.com");
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).email("dono@test.com").build();
        ClienteEntity cliente = ClienteEntity.builder()
                .nome("João Silva").telefone("11999999999").email("joao@test.com").build();
        AgendamentoEntity agendamento = AgendamentoEntity.builder()
                .protocolo("123456").cliente(cliente)
                .data(LocalDate.of(2025, 1, 15)).horaInicio(LocalTime.of(14, 0))
                .empresa(empresa).build();

        boolean result = service.enviarEmailNovoAgendamento(empresa, agendamento);

        assertFalse(result);
    }

    @Test
    void deveMontarPayloadComDadosDoAgendamento() throws Exception {
        HttpClient.Builder builderMock = mock(HttpClient.Builder.class, RETURNS_SELF);
        HttpClient httpClientMock = mock(HttpClient.class);
        when(builderMock.build()).thenReturn(httpClientMock);

        HttpResponse<String> responseMock = mock(HttpResponse.class);
        when(responseMock.statusCode()).thenReturn(200);
        doReturn(responseMock).when(httpClientMock).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        try (MockedStatic<HttpClient> httpClientStatic = mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newBuilder).thenReturn(builderMock);

            var service = new ResendEmailService(objectMapper, "re_test_key", "agendamentos@meudominio.com", "Gendaz", "https://gendaz.site", "admin@test.com");

            EmpresaEntity empresa = EmpresaEntity.builder().id(1L).email("dono@empresa.com").build();
            ClienteEntity cliente = ClienteEntity.builder()
                    .nome("Maria Souza").telefone("11988888888").email("maria@test.com").build();
            AgendamentoEntity agendamento = AgendamentoEntity.builder()
                    .protocolo("654321").cliente(cliente)
                    .data(LocalDate.of(2025, 6, 10)).horaInicio(LocalTime.of(9, 30))
                    .empresa(empresa).build();

            boolean result = service.enviarEmailNovoAgendamento(empresa, agendamento);

            assertTrue(result);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClientMock).send(requestCaptor.capture(), any());

            HttpRequest request = requestCaptor.getValue();
            String body = new String(lerBodyPublisher(request.bodyPublisher().orElseThrow()));

            assertTrue(body.contains("\"to\":\"dono@empresa.com\""));
            assertTrue(body.contains("Novo agendamento recebido - Protocolo 654321"));
            assertTrue(body.contains("Maria Souza"));
            assertTrue(body.contains("maria@test.com"));
            assertTrue(body.contains("11988888888"));
            assertTrue(body.contains("10/06/2025"));
            assertTrue(body.contains("09:30"));
            assertTrue(body.contains("654321"));
            assertTrue(body.contains("agendamentos@meudominio.com"));
            assertTrue(request.headers().firstValue("Authorization").orElse("").contains("Bearer re_test_key"));
        }
    }

    @Test
    void deveRetornarFalseQuandoEmpresaSemEmail() {
        var service = new ResendEmailService(objectMapper, "re_test_key", "from@test.com", "Test", "https://test.com", "admin@test.com");
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).build();
        ClienteEntity cliente = ClienteEntity.builder().nome("João").build();
        AgendamentoEntity agendamento = AgendamentoEntity.builder()
                .protocolo("999999").cliente(cliente)
                .data(LocalDate.now()).horaInicio(LocalTime.of(10, 0))
                .empresa(empresa).build();

        boolean result = service.enviarEmailNovoAgendamento(empresa, agendamento);

        assertFalse(result);
    }

    @Test
    void deveIgnorarClienteComDadosNulos() throws Exception {
        HttpClient.Builder builderMock = mock(HttpClient.Builder.class, RETURNS_SELF);
        HttpClient httpClientMock = mock(HttpClient.class);
        when(builderMock.build()).thenReturn(httpClientMock);

        HttpResponse<String> responseMock = mock(HttpResponse.class);
        when(responseMock.statusCode()).thenReturn(200);
        doReturn(responseMock).when(httpClientMock).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        try (MockedStatic<HttpClient> httpClientStatic = mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newBuilder).thenReturn(builderMock);

            var service = new ResendEmailService(objectMapper, "re_test_key", "from@test.com", "Test", "https://test.com", "admin@test.com");

            EmpresaEntity empresa = EmpresaEntity.builder().id(1L).email("dono@empresa.com").build();
            AgendamentoEntity agendamento = AgendamentoEntity.builder()
                    .protocolo("111222").cliente(null)
                    .data(LocalDate.of(2025, 3, 20)).horaInicio(LocalTime.of(15, 45))
                    .empresa(empresa).build();

            boolean result = service.enviarEmailNovoAgendamento(empresa, agendamento);

            assertTrue(result);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClientMock).send(requestCaptor.capture(), any());

            String body = new String(lerBodyPublisher(requestCaptor.getValue().bodyPublisher().orElseThrow()));
            assertTrue(body.contains("Nao informado"));
            assertTrue(body.contains("20/03/2025"));
            assertTrue(body.contains("15:45"));
            assertTrue(body.contains("111222"));
        }
    }

    private byte[] lerBodyPublisher(HttpRequest.BodyPublisher publisher) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        BufferedOutputStream out = new BufferedOutputStream(buffer);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }
            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                try { out.write(bytes); } catch (IOException ignored) {}
            }
            @Override
            public void onError(Throwable throwable) {}
            @Override
            public void onComplete() {
                try { out.close(); } catch (IOException ignored) {}
            }
        });
        return buffer.toByteArray();
    }
}
