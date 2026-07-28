package com.minhaempresa.agendapro.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.shared.audit.OutboundTrafficAuditService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ResendEmailService {
    private static final URI RESEND_URI = URI.create("https://api.resend.com/emails");
    private static final String EMAIL_LOGO_URL = "https://api.gendaz.site/email/gendazpngpreto.png";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OutboundTrafficAuditService auditService;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final String frontendUrl;
    private final String adminNotificationEmail;

    @Autowired
    public ResendEmailService(
            ObjectMapper objectMapper,
            OutboundTrafficAuditService auditService,
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from-email:no-reply@gendaz.site}") String fromEmail,
            @Value("${resend.from-name:Gendaz}") String fromName,
            @Value("${app.frontend-url:${FRONTEND_URL:https://gendaz.site}}") String frontendUrl,
            @Value("${app.admin-notification-email:viniciushf0360@gmail.com}") String adminNotificationEmail
    ) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
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

    public ResendEmailService(
            ObjectMapper objectMapper,
            String apiKey,
            String fromEmail,
            String fromName,
            String frontendUrl,
            String adminNotificationEmail
    ) {
        this(objectMapper, null, apiKey, fromEmail, fromName, frontendUrl, adminNotificationEmail);
    }

    public boolean enviarBoasVindas(String emailCliente, String nomeCliente, String nomeEmpresa) {
        if (emailCliente == null || emailCliente.isBlank()) {
            log.warn("[resend] email do cliente vazio, boas-vindas ignorado");
            return false;
        }
        try {
            String assunto = "Bem-vindo ao " + safe(nomeEmpresa, "Gendaz") + "!";
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
            log.error("[resend] erro ao montar email novo agendamento: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean enviarCodigoMeuGendaz(String emailCliente, String nomeCliente, String codigo) {
        if (emailCliente == null || emailCliente.isBlank()) {
            log.warn("[resend] email do cliente vazio, codigo do Meu Gendaz ignorado");
            return false;
        }
        try {
            String assunto = "Seu codigo de acesso ao Meu Gendaz";
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
        contarExecucao("ResendEmailService#enviarEmail");
        if (apiKey.isBlank()) {
            log.warn("[resend] RESEND_API_KEY ausente; email nao enviado");
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

            long inicio = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            registrarHttp(
                    "Resend",
                    RESEND_URI.toString(),
                    "POST",
                    auditServiceOrNull().origem("ResendEmailService", "enviarEmail"),
                    body,
                    Map.of(
                            "Authorization", "Bearer " + apiKey,
                            "Content-Type", "application/json"
                    ),
                    response.body(),
                    System.currentTimeMillis() - inicio,
                    response.statusCode()
            );
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[resend] email enviado com sucesso");
                return true;
            }

            log.warn("[resend] resposta nao-sucedida status={}", response.statusCode());
            return false;
        } catch (Exception e) {
            log.error("[resend] falha ao enviar email: {}", e.getMessage(), e);
            return false;
        }
    }

    private void contarExecucao(String chave) {
        if (auditService != null) {
            auditService.contarExecucao(chave);
        }
    }

    private void registrarHttp(
            String integracao,
            String urlBase,
            String metodoHttp,
            String origem,
            String bodyEnviado,
            Map<String, String> headers,
            String bodyRecebido,
            long duracaoMs,
            int statusHttp
    ) {
        if (auditService == null) {
            return;
        }
        auditService.registrarHttp(
                integracao,
                auditService.sanitizarBaseUrl(urlBase),
                metodoHttp,
                origem,
                auditService.bytesUtf8(bodyEnviado),
                auditService.headersBytes(headers),
                auditService.bytesUtf8(bodyRecebido),
                duracaoMs,
                statusHttp
        );
    }

    private OutboundTrafficAuditService auditServiceOrNull() {
        return auditService == null ? NOOP_AUDIT_SERVICE : auditService;
    }

    private static final OutboundTrafficAuditService NOOP_AUDIT_SERVICE = new OutboundTrafficAuditService(false, 600000L);

    private String montarLinkRecuperacao(String token) {
        return montarUrlBase() + "/redefinir-senha?token=" + token;
    }

    private String montarUrlBase() {
        return frontendUrl == null || frontendUrl.isBlank() ? "https://gendaz.site" : frontendUrl.replaceAll("/+$", "");
    }

    private String montarHtmlBoasVindas(String nomeCliente, String nomeEmpresa) {
        String corpo = """
                <p style=\"margin:0 0 10px;\">Agora voce faz parte da <strong>%s</strong>.</p>
                <p style=\"margin:0;\">Voce ja pode acessar sua conta e comecar a usar a plataforma.</p>
                """.formatted(nomeEmpresa);
        return montarEmailPadrao(
                "Gendaz",
                "Bem-vindo, %s!".formatted(nomeCliente),
                "Seu acesso foi confirmado com sucesso.",
                corpo,
                montarUrlBase(),
                "Acessar o painel",
                "Este e um e-mail automatico da Gendaz. Se preferir, responda diretamente por este canal."
        );
    }

    private String montarHtmlRecuperacao(String nomeCliente, String linkRecuperacao) {
        String corpo = """
                <p style=\"margin:0 0 10px;\">Recebemos uma solicitacao para redefinir sua senha.</p>
                <p style=\"margin:0;\">Clique no botao abaixo para continuar com a redefinicao.</p>
                """;
        return montarEmailPadrao(
                "Gendaz",
                "Recuperacao de senha",
                "Ola %s,".formatted(nomeCliente),
                corpo,
                linkRecuperacao,
                "Redefinir senha",
                "Se voce nao solicitou isso, pode ignorar este e-mail."
        );
    }

    private String montarHtmlNovoAgendamento(AgendamentoEntity agendamento) {
        String nomeCliente = agendamento.getCliente() != null ? safe(agendamento.getCliente().getNome(), "Nao informado") : "Nao informado";
        String emailCliente = agendamento.getCliente() != null ? safe(agendamento.getCliente().getEmail(), "-") : "-";
        String telefoneCliente = agendamento.getCliente() != null ? safe(agendamento.getCliente().getTelefone(), "-") : "-";
        String data = agendamento.getData() != null ? agendamento.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "-";
        String hora = agendamento.getHoraInicio() != null ? agendamento.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm")) : "-";
        String protocolo = safe(agendamento.getProtocolo(), "N/A");

        String corpo = """
                <table style=\"width:100%%; border-collapse:collapse; margin:0; color:#111111;\">
                  <tr>
                    <td style=\"padding:10px 0; border-bottom:1px solid #e5e7eb; color:#6b7280; font-weight:700; width:40%%;\">Cliente</td>
                    <td style=\"padding:10px 0; border-bottom:1px solid #e5e7eb;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:10px 0; border-bottom:1px solid #e5e7eb; color:#6b7280; font-weight:700;\">E-mail</td>
                    <td style=\"padding:10px 0; border-bottom:1px solid #e5e7eb;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:10px 0; border-bottom:1px solid #e5e7eb; color:#6b7280; font-weight:700;\">Telefone</td>
                    <td style=\"padding:10px 0; border-bottom:1px solid #e5e7eb;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:10px 0; border-bottom:1px solid #e5e7eb; color:#6b7280; font-weight:700;\">Data</td>
                    <td style=\"padding:10px 0; border-bottom:1px solid #e5e7eb;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:10px 0; border-bottom:1px solid #e5e7eb; color:#6b7280; font-weight:700;\">Horario</td>
                    <td style=\"padding:10px 0; border-bottom:1px solid #e5e7eb;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:10px 0; color:#6b7280; font-weight:700;\">Protocolo</td>
                    <td style=\"padding:10px 0;\"><strong>%s</strong></td>
                  </tr>
                </table>
                """.formatted(nomeCliente, emailCliente, telefoneCliente, data, hora, protocolo);
        return montarEmailPadrao(
                "Gendaz",
                "Novo agendamento recebido",
                "Um novo agendamento foi realizado. Confira os detalhes abaixo.",
                corpo,
                montarUrlBase() + "/sistema/agenda",
                "Abrir agenda",
                "Este e um e-mail automatico. Nao responda a esta mensagem."
        );
    }

    private String montarHtmlCodigoMeuGendaz(String nomeCliente, String codigo) {
        String corpo = """
                <p style=\"margin:0 0 10px;\">Seu codigo de acesso e:</p>
                <div style=\"font-size:32px; font-weight:800; letter-spacing:6px; margin:24px 0; padding:16px 20px; background:#111111; color:#ffffff; border-radius:12px; text-align:center;\">%s</div>
                <p style=\"margin:0;\">Este codigo expira em 10 minutos.</p>
                <p style=\"margin:10px 0 0;\">Se voce nao solicitou este acesso, ignore este e-mail.</p>
                """.formatted(codigo);
        return montarEmailPadrao(
                "Gendaz",
                "Meu Gendaz",
                "Ola %s,".formatted(nomeCliente),
                corpo,
                montarUrlBase() + "/meu-gendaz",
                "Abrir Meu Gendaz",
                "Este e um e-mail automatico da Gendaz. Se preferir, responda diretamente por este canal."
        );
    }

    public boolean sendNewCustomerNotification(String nomeCliente, String emailCliente, String telefoneCliente,
                                                String nomeEmpresa, String plano, String dataCadastro,
                                                Long empresaId, String cpfCnpj, Long usuarioId) {
        try {
            String assunto = "Novo cliente cadastrado na Gendaz";
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
        String dataFormatada = dataCadastro != null && dataCadastro.length() >= 10 ? dataCadastro.substring(0, 10) : "-";
        String horaFormatada = dataCadastro != null && dataCadastro.length() > 11 ? dataCadastro.substring(11) : "-";

        String corpo = """
                <table style=\"width:100%%; border-collapse:collapse; margin:0; color:#111111;\">
                  <tr>
                    <td style=\"padding:8px 0; color:#6b7280; font-weight:700; width:40%%;\">Nome da empresa:</td>
                    <td style=\"padding:8px 0;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:8px 0; color:#6b7280; font-weight:700;\">Plano:</td>
                    <td style=\"padding:8px 0;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:8px 0; color:#6b7280; font-weight:700;\">ID da empresa:</td>
                    <td style=\"padding:8px 0;\">%d</td>
                  </tr>
                </table>
                <hr style=\"border:none; border-top:1px solid #e5e7eb; margin:20px 0;\">
                <table style=\"width:100%%; border-collapse:collapse; margin:0; color:#111111;\">
                  <tr>
                    <td style=\"padding:8px 0; color:#6b7280; font-weight:700; width:40%%;\">Nome:</td>
                    <td style=\"padding:8px 0;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:8px 0; color:#6b7280; font-weight:700;\">E-mail:</td>
                    <td style=\"padding:8px 0;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:8px 0; color:#6b7280; font-weight:700;\">Telefone:</td>
                    <td style=\"padding:8px 0;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:8px 0; color:#6b7280; font-weight:700;\">CPF/CNPJ:</td>
                    <td style=\"padding:8px 0;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:8px 0; color:#6b7280; font-weight:700;\">ID do usuario:</td>
                    <td style=\"padding:8px 0;\">%d</td>
                  </tr>
                  <tr>
                    <td style=\"padding:8px 0; color:#6b7280; font-weight:700;\">Data de cadastro:</td>
                    <td style=\"padding:8px 0;\">%s</td>
                  </tr>
                  <tr>
                    <td style=\"padding:8px 0; color:#6b7280; font-weight:700;\">Horario:</td>
                    <td style=\"padding:8px 0;\">%s</td>
                  </tr>
                </table>
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
        return montarEmailPadrao(
                "Gendaz",
                "Novo cliente cadastrado",
                "A Gendaz acabou de receber um novo cliente.",
                corpo,
                montarUrlBase() + "/sistema/clientes",
                "Abrir clientes",
                "Mensagem automatica enviada pela Gendaz."
        );
    }

    private String montarEmailPadrao(String badge, String titulo, String subtitulo, String corpo, String ctaUrl, String ctaTexto, String rodape) {
        return """
                <html>
                  <body style=\"margin:0; padding:0; background:#0b0b0c; font-family:Arial, Helvetica, sans-serif; color:#111111;\">
                    <div style=\"max-width:760px; margin:0 auto; padding:36px 20px;\">
                      <div style=\"background:#ffffff; border-radius:20px; overflow:hidden; box-shadow:0 18px 60px rgba(0,0,0,0.18); border:1px solid #e5e7eb;\">
                        <div style=\"padding:36px 36px 28px; text-align:center; background:#ffffff;\">
                          <img src=\"%s\" alt=\"Gendaz\" style=\"max-width:180px; width:100%%; height:auto; display:block; margin:0 auto 16px;\" />
                          <div style=\"display:inline-block; padding:6px 12px; border-radius:999px; background:#111111; color:#ffffff; font-size:12px; font-weight:700; letter-spacing:0.08em; text-transform:uppercase;\">%s</div>
                          <h1 style=\"margin:18px 0 10px; font-size:28px; line-height:1.2; color:#111111;\">%s</h1>
                          <p style=\"margin:0 auto; max-width:520px; font-size:16px; line-height:1.7; color:#4b5563;\">%s</p>
                        </div>

                        <div style=\"padding:0 36px 28px;\">
                          <div style=\"background:#f7f7f7; border:1px solid #e5e7eb; border-radius:16px; padding:22px 20px; color:#111111; font-size:14px; line-height:1.8;\">
                            %s
                          </div>

                          <div style=\"text-align:center; margin-top:24px;\">
                            <a href=\"%s\" style=\"display:inline-block; background:#111111; color:#ffffff; text-decoration:none; font-weight:700; padding:14px 26px; border-radius:999px; font-size:15px;\">%s</a>
                          </div>
                        </div>

                        <div style=\"padding:0 36px 30px; text-align:center;\">
                          <p style=\"margin:0; font-size:12px; line-height:1.6; color:#6b7280;\">%s</p>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(
                EMAIL_LOGO_URL,
                safe(badge, "Gendaz"),
                safe(titulo, "Gendaz"),
                safe(subtitulo, ""),
                corpo,
                safe(ctaUrl, montarUrlBase()),
                safe(ctaTexto, "Abrir"),
                safe(rodape, "Este e um e-mail automatico da Gendaz.")
        );
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
