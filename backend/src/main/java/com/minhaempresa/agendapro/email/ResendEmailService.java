package com.minhaempresa.agendapro.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ResendEmailService {
    private static final URI RESEND_URI = URI.create("https://api.resend.com/emails");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final String frontendUrl;
    private final String adminNotificationEmail;

    public ResendEmailService(
            ObjectMapper objectMapper,
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from-email:no-reply@gendaz.site}") String fromEmail,
            @Value("${resend.from-name:Gendaz}") String fromName,
            @Value("${app.frontend-url:${FRONTEND_URL:https://gendaz.site}}") String frontendUrl,
            @Value("${app.admin-notification-email:viniciushf0360@gmail.com}") String adminNotificationEmail
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.fromEmail = fromEmail == null ? "no-reply@gendaz.site" : fromEmail.trim();
        this.fromName = fromName == null ? "Gendaz" : fromName.trim();
        this.frontendUrl = frontendUrl == null ? "https://gendaz.site" : frontendUrl.trim();
        this.adminNotificationEmail = adminNotificationEmail == null || adminNotificationEmail.isBlank()
                ? "viniciushf0360@gmail.com" : adminNotificationEmail.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean enviarBoasVindas(String emailCliente, String nomeCliente, String nomeEmpresa) {
        if (emailCliente == null || emailCliente.isBlank()) {
            log.warn("[resend] email do cliente vazio, boas-vindas ignorado");
            return false;
        }
        try {
            String assunto = "Bem-vindo(a) ao " + safe(nomeEmpresa, "Gendaz") + "!";
            String html = montarHtmlBoasVindas(safe(nomeCliente, "cliente"), safe(nomeEmpresa, "Gendaz"));
            return enviarEmail(emailCliente, assunto, html);
        } catch (Exception e) {
            log.error("[resend] erro ao montar email boas-vindas: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean enviarRecuperacaoSenha(String emailCliente, String nomeCliente, String token) {
        if (emailCliente == null || emailCliente.isBlank()) {
            log.warn("[resend] email do cliente vazio, recuperacao ignorada");
            return false;
        }
        try {
            String link = montarLinkRecuperacao(token);
            String assunto = "Recupere sua senha - Gendaz";
            String html = montarHtmlRecuperacao(safe(nomeCliente, "cliente"), link);
            return enviarEmail(emailCliente, assunto, html);
        } catch (Exception e) {
            log.error("[resend] erro ao montar email recuperacao: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean enviarEmailNovoAgendamento(EmpresaEntity empresa, AgendamentoEntity agendamento) {
        if (empresa == null || empresa.getEmail() == null || empresa.getEmail().isBlank()) {
            log.warn("[resend] email da empresa vazio, notificacao ignorada");
            return false;
        }
        try {
            String assunto = "Novo agendamento recebido - Protocolo " + safe(agendamento.getProtocolo(), "N/A");
            String html = montarHtmlNovoAgendamento(agendamento);
            return enviarEmail(empresa.getEmail(), assunto, html);
        } catch (Exception e) {
            log.error("[resend] erro ao montar email novo agendamento protocolo {}: {}", agendamento.getProtocolo(), e.getMessage(), e);
            return false;
        }
    }

    public boolean enviarCodigoMeuGendaz(String emailCliente, String nomeCliente, String codigo) {
        if (emailCliente == null || emailCliente.isBlank()) {
            log.warn("[resend] email do cliente vazio, codigo do Meu Gendaz ignorado");
            return false;
        }
        try {
            String assunto = "Seu código de acesso ao Meu Gendaz";
            String html = montarHtmlCodigoMeuGendaz(safe(nomeCliente, "cliente"), safe(codigo, "000000"));
            return enviarEmail(emailCliente, assunto, html);
        } catch (Exception e) {
            log.error("[resend] erro ao montar email de codigo Meu Gendaz: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean enviarEmailCrm(String destinatario, String assunto, String html) {
        return enviarEmail(destinatario, assunto, html);
    }

    private boolean enviarEmail(String destinatario, String assunto, String html) {
        if (apiKey.isBlank()) {
            log.warn("[resend] RESEND_API_KEY ausente; email nao enviado para {}", destinatario);
            return false;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", fromName + " <" + fromEmail + ">");
            payload.put("to", destinatario);
            payload.put("subject", assunto);
            payload.put("html", html);

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(RESEND_URI)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[resend] email enviado para {}", destinatario);
                return true;
            }

            log.warn("[resend] resposta nao-sucedida status={} body={}", response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.error("[resend] falha ao enviar email para {}: {}", destinatario, e.getMessage(), e);
            return false;
        }
    }

    private String montarLinkRecuperacao(String token) {
        String base = frontendUrl == null || frontendUrl.isBlank() ? "https://gendaz.site" : frontendUrl.replaceAll("/+$", "");
        return base + "/redefinir-senha?token=" + token;
    }

    private String montarHtmlBoasVindas(String nomeCliente, String nomeEmpresa) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; background-color: #f5f5f5; padding: 24px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 32px;">
                      <h2 style="margin-top: 0;">Bem-vindo(a), %s!</h2>
                      <p>Agora você faz parte da %s.</p>
                      <p>Você já pode acessar sua conta e começar a usar a plataforma.</p>
                      <p style="margin-top: 24px;">Atenciosamente,<br><strong>Equipe %s</strong></p>
                    </div>
                  </body>
                </html>
                """.formatted(nomeCliente, nomeEmpresa, fromName);
    }

    private String montarHtmlRecuperacao(String nomeCliente, String linkRecuperacao) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; background-color: #f5f5f5; padding: 24px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 32px;">
                      <h2 style="margin-top: 0;">Recuperação de senha</h2>
                      <p>Olá %s,</p>
                      <p>Recebemos uma solicitação para redefinir sua senha. Clique no botão abaixo para continuar:</p>
                      <p style="margin: 28px 0;">
                        <a href="%s" style="display: inline-block; background: #0ea5e9; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 8px;">
                          Redefinir senha
                        </a>
                      </p>
                      <p style="color: #6b7280; font-size: 12px;">Se você não solicitou isso, pode ignorar este e-mail.</p>
                      <p style="margin-top: 24px;">Atenciosamente,<br><strong>Equipe %s</strong></p>
                    </div>
                  </body>
                </html>
                """.formatted(nomeCliente, linkRecuperacao, fromName);
    }

    private String montarHtmlNovoAgendamento(AgendamentoEntity agendamento) {
        String nomeCliente = agendamento.getCliente() != null ? safe(agendamento.getCliente().getNome(), "Nao informado") : "Nao informado";
        String emailCliente = agendamento.getCliente() != null ? safe(agendamento.getCliente().getEmail(), "-") : "-";
        String telefoneCliente = agendamento.getCliente() != null ? safe(agendamento.getCliente().getTelefone(), "-") : "-";
        String data = agendamento.getData() != null ? agendamento.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "-";
        String hora = agendamento.getHoraInicio() != null ? agendamento.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm")) : "-";
        String protocolo = safe(agendamento.getProtocolo(), "N/A");

        return """
                <html>
                  <body style="font-family: Arial, sans-serif; background-color: #f5f5f5; padding: 24px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 32px;">
                      <h2 style="margin-top: 0;">Novo agendamento recebido</h2>
                      <p>Ol&aacute;, um novo agendamento foi realizado. Confira os detalhes:</p>
                      <table style="width: 100%%; border-collapse: collapse; margin: 20px 0;">
                        <tr>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb; color: #6b7280; font-weight: bold;">Cliente</td>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb; color: #6b7280; font-weight: bold;">E-mail</td>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb; color: #6b7280; font-weight: bold;">Telefone</td>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb; color: #6b7280; font-weight: bold;">Data</td>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb; color: #6b7280; font-weight: bold;">Hor&aacute;rio</td>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb; color: #6b7280; font-weight: bold;">Protocolo</td>
                          <td style="padding: 10px; border-bottom: 1px solid #e5e7eb;"><strong>%s</strong></td>
                        </tr>
                      </table>
                      <p style="color: #6b7280; font-size: 12px;">Este &eacute; um e-mail autom&aacute;tico. N&atilde;o responda a esta mensagem.</p>
                      <p>Atenciosamente,<br><strong>Equipe %s</strong></p>
                    </div>
                  </body>
                </html>
                """.formatted(nomeCliente, emailCliente, telefoneCliente, data, hora, protocolo, fromName);
    }

    private String montarHtmlCodigoMeuGendaz(String nomeCliente, String codigo) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; background-color: #f5f5f5; padding: 24px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 32px; color: #111111;">
                      <h2 style="margin-top: 0;">Meu Gendaz</h2>
                      <p>Olá %s,</p>
                      <p>Seu código de acesso é:</p>
                      <div style="font-size: 32px; font-weight: bold; letter-spacing: 6px; margin: 24px 0; padding: 16px 20px; background: #111111; color: #ffffff; border-radius: 12px; text-align: center;">
                        %s
                      </div>
                      <p>Este código expira em 10 minutos.</p>
                      <p>Se você não solicitou este acesso, ignore este e-mail.</p>
                      <p style="margin-top: 24px;">Atenciosamente,<br><strong>Equipe %s</strong></p>
                    </div>
                  </body>
                </html>
                """.formatted(nomeCliente, codigo, fromName);
    }

    public boolean sendNewCustomerNotification(String nomeCliente, String emailCliente, String telefoneCliente,
                                                String nomeEmpresa, String plano, String dataCadastro,
                                                Long empresaId, String cpfCnpj, Long usuarioId) {
        try {
            String assunto = "\uD83C\uDF89 Novo cliente cadastrado na Gendaz";
            String html = montarHtmlNovoCliente(nomeCliente, emailCliente, telefoneCliente, nomeEmpresa,
                    plano, dataCadastro, empresaId, cpfCnpj, usuarioId);
            return enviarEmail(adminNotificationEmail, assunto, html);
        } catch (Exception e) {
            log.error("[resend] erro ao montar email novo cliente: {}", e.getMessage(), e);
            return false;
        }
    }

    private String montarHtmlNovoCliente(String nomeCliente, String emailCliente, String telefoneCliente,
                                          String nomeEmpresa, String plano, String dataCadastro,
                                          Long empresaId, String cpfCnpj, Long usuarioId) {
        String dataFormatada = dataCadastro != null ? dataCadastro.substring(0, 10) : "-";
        String horaFormatada = dataCadastro != null && dataCadastro.length() > 11 ? dataCadastro.substring(11) : "-";

        return """
                <html>
                  <body style="font-family: Arial, sans-serif; background-color: #f5f5f5; padding: 24px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 32px;">
                      <h2 style="margin-top: 0; color: #1a1a2e;">\uD83C\uDF89 Novo cliente cadastrado na Gendaz</h2>
                      <p style="color: #374151;">Ol&aacute; Vinicius,</p>
                      <p style="color: #374151;">A Gendaz acaba de receber um novo cliente.</p>
                      <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 24px 0;">
                      <p style="font-weight: bold; color: #1a1a2e; font-size: 14px; margin-bottom: 8px;">Dados da Empresa</p>
                      <table style="width: 100%%; border-collapse: collapse; margin: 0 0 16px 0;">
                        <tr>
                          <td style="padding: 8px 0; color: #6b7280; font-weight: bold; width: 40%%;">Nome da empresa:</td>
                          <td style="padding: 8px 0; color: #1a1a2e;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 8px 0; color: #6b7280; font-weight: bold;">Plano:</td>
                          <td style="padding: 8px 0; color: #1a1a2e;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 8px 0; color: #6b7280; font-weight: bold;">ID da empresa:</td>
                          <td style="padding: 8px 0; color: #1a1a2e;">%d</td>
                        </tr>
                      </table>
                      <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 24px 0;">
                      <p style="font-weight: bold; color: #1a1a2e; font-size: 14px; margin-bottom: 8px;">Respons&aacute;vel</p>
                      <table style="width: 100%%; border-collapse: collapse; margin: 0 0 16px 0;">
                        <tr>
                          <td style="padding: 8px 0; color: #6b7280; font-weight: bold; width: 40%%;">Nome:</td>
                          <td style="padding: 8px 0; color: #1a1a2e;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 8px 0; color: #6b7280; font-weight: bold;">E-mail:</td>
                          <td style="padding: 8px 0; color: #1a1a2e;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 8px 0; color: #6b7280; font-weight: bold;">Telefone:</td>
                          <td style="padding: 8px 0; color: #1a1a2e;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 8px 0; color: #6b7280; font-weight: bold;">CPF/CNPJ:</td>
                          <td style="padding: 8px 0; color: #1a1a2e;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 8px 0; color: #6b7280; font-weight: bold;">ID do usu&aacute;rio:</td>
                          <td style="padding: 8px 0; color: #1a1a2e;">%d</td>
                        </tr>
                      </table>
                      <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 24px 0;">
                      <table style="width: 100%%; border-collapse: collapse; margin: 0 0 16px 0;">
                        <tr>
                          <td style="padding: 8px 0; color: #6b7280; font-weight: bold; width: 40%%;">Data de cadastro:</td>
                          <td style="padding: 8px 0; color: #1a1a2e;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding: 8px 0; color: #6b7280; font-weight: bold;">Hor&aacute;rio:</td>
                          <td style="padding: 8px 0; color: #1a1a2e;">%s</td>
                        </tr>
                      </table>
                      <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 24px 0;">
                      <p style="color: #9ca3af; font-size: 12px; text-align: center;">Mensagem autom&aacute;tica enviada pela Gendaz.</p>
                    </div>
                  </body>
                </html>
                """.formatted(
                        safe(nomeEmpresa, "Nao informado"),
                        safe(plano, "Nao informado"),
                        empresaId != null ? empresaId : 0L,
                        safe(nomeCliente, "Nao informado"),
                        safe(emailCliente, "Nao informado"),
                        safe(telefoneCliente, "Nao informado"),
                        safe(cpfCnpj, "Nao informado"),
                        usuarioId != null ? usuarioId : 0L,
                        safe(dataFormatada, "-"),
                        safe(horaFormatada, "-")
                );
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
