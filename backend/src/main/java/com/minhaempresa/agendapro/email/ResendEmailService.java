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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    /**
     * Envia qualquer email usando o template padrao unico (marca "gendaz" em texto + CTA preto).
     */
    public boolean enviarComTemplate(
            String email,
            String assunto,
            String titulo,
            String subtitulo,
            String corpoHtml,
            String ctaUrl,
            String ctaTexto
    ) {
        String html = montarEmailPadrao(
                "Gendaz",
                titulo,
                subtitulo,
                corpoHtml,
                ctaUrl,
                ctaTexto,
                "Este e um e-mail automatico da Gendaz. Se preferir, responda diretamente por este canal."
        );
        return enviarEmail(email, assunto, html);
    }

    /**
     * Email de promocao/cupom com o template padrao.
     */
    public boolean enviarPromocao(
            String emailCliente,
            String nomeCliente,
            String nomeEmpresa,
            String cupomCodigo,
            String desconto,
            String descricao,
            LocalDateTime validoAte,
            String slug
    ) {
        if (emailCliente == null || emailCliente.isBlank()) {
            log.warn("[resend] email do cliente vazio, promocao ignorada");
            return false;
        }
        try {
            String dataFim = validoAte != null
                    ? validoAte.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "consulte os termos no portal";
            String codigo = safe(cupomCodigo, "-");
            String corpo = """
                    <div style="text-align:center; margin:6px 0 18px;">
                      <div style="display:inline-block; background:#111111; border:1px solid #000000; border-radius:12px; padding:16px 24px; text-align:left;">
                        <p style="margin:0 0 4px; color:#ffffff;"><strong>Cupom:</strong> %s</p>
                        <p style="margin:0 0 4px; color:#ffffff;"><strong>Desconto:</strong> %s</p>
                        <p style="margin:0; color:#d4d4d8;">Valido ate %s</p>
                      </div>
                    </div>
                    <p style="margin:0 0 12px;">%s</p>
                    <p style="margin:0 0 8px;"><strong>Como usar:</strong></p>
                    <ol style="margin:0 0 12px; padding-left:20px; color:#111111;">
                      <li style="margin-bottom:6px;">Acesse nosso portal de agendamentos</li>
                      <li style="margin-bottom:6px;">Escolha o servico desejado</li>
                      <li style="margin-bottom:6px;">Aplique o cupom <strong>%s</strong> no checkout</li>
                      <li style="margin-bottom:6px;">Pronto! Seu desconto foi aplicado</li>
                    </ol>
                    <p style="margin:0;">Qualquer duvida, e so responder este e-mail.</p>
                    """.formatted(
                    codigo,
                    safe(desconto, "-"),
                    dataFim,
                    safe(descricao, "Temos uma oferta especial para voce. Aproveite antes que acabe!"),
                    codigo
            );
            return enviarComTemplate(
                    emailCliente,
                    "Promocao especial: " + codigo,
                    "Oferta especial para voce",
                    "Ola %s, %s preparou um cupom novo para voce.".formatted(
                            safe(nomeCliente, "cliente"), safe(nomeEmpresa, "nossa empresa")),
                    corpo,
                    montarUrlMeuGendaz(slug) + "/agenda",
                    "Agendar com desconto"
            );
        } catch (Exception e) {
            log.error("[resend] erro ao montar email promocao: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Email de confirmacao de agendamento com o template padrao.
     */
    public boolean enviarConfirmacaoAgendamento(
            String emailCliente,
            String nomeCliente,
            String nomeServico,
            String nomeProfissional,
            LocalDate data,
            LocalTime horaInicio,
            String nomeEmpresa,
            String slug
    ) {
        if (emailCliente == null || emailCliente.isBlank()) {
            log.warn("[resend] email do cliente vazio, confirmacao ignorada");
            return false;
        }
        try {
            String dataFormatada = (data != null
                    ? data.format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy"))
                    : "data a definir")
                    + " as "
                    + (horaInicio != null ? horaInicio.format(DateTimeFormatter.ofPattern("HH:mm")) : "--:--");
            String corpo = """
                    <div style="background:#111111; border:1px solid #000000; border-radius:12px; padding:16px 20px; color:#ffffff; font-size:14px; line-height:1.8;">
                      <p style="margin:0 0 6px;"><strong>Servico:</strong> %s</p>
                      <p style="margin:0 0 6px;"><strong>Profissional:</strong> %s</p>
                      <p style="margin:0 0 6px;"><strong>Data e hora:</strong> %s</p>
                      <p style="margin:0;"><strong>Empresa:</strong> %s</p>
                    </div>
                    <p style="margin:16px 0 0;">Dica: chegue com alguns minutos de antecedencia para a melhor experiencia.</p>
                    <p style="margin:8px 0 0;">Precisa reagendar? Acesse seu portal a qualquer momento para fazer alteracoes.</p>
                    """.formatted(
                    safe(nomeServico, "servico"),
                    safe(nomeProfissional, "profissional"),
                    dataFormatada,
                    safe(nomeEmpresa, "nossa empresa")
            );
            return enviarComTemplate(
                    emailCliente,
                    "Confirmacao de agendamento",
                    "Seu agendamento foi confirmado",
                    "Ola %s, tudo certo com o seu compromisso.".formatted(safe(nomeCliente, "cliente")),
                    corpo,
                    montarUrlMeuGendaz(slug) + "/agenda",
                    "Ver meus agendamentos"
            );
        } catch (Exception e) {
            log.error("[resend] erro ao montar email confirmacao: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Email de lembrete de agendamento com o template padrao.
     */
    public boolean enviarLembreteAgendamento(
            String emailCliente,
            String nomeCliente,
            String nomeServico,
            String nomeProfissional,
            LocalDate data,
            LocalTime horaInicio,
            String nomeEmpresa,
            String slug
    ) {
        if (emailCliente == null || emailCliente.isBlank()) {
            log.warn("[resend] email do cliente vazio, lembrete ignorado");
            return false;
        }
        try {
            String dataFormatada = (data != null
                    ? data.format(DateTimeFormatter.ofPattern("EEEE 'de' dd 'de' MMMM"))
                    : "data a definir")
                    + " as "
                    + (horaInicio != null ? horaInicio.format(DateTimeFormatter.ofPattern("HH:mm")) : "--:--");
            String corpo = """
                    <div style="background:#111111; border:1px solid #000000; border-radius:12px; padding:16px 20px; color:#ffffff; font-size:14px; line-height:1.8;">
                      <p style="margin:0 0 6px;"><strong>Quando:</strong> %s</p>
                      <p style="margin:0 0 6px;"><strong>Servico:</strong> %s</p>
                      <p style="margin:0;"><strong>Profissional:</strong> %s</p>
                    </div>
                    <p style="margin:16px 0 0;">Precisa cancelar ou reagendar? Faca isso com antecedencia no seu portal.</p>
                    """.formatted(
                    dataFormatada,
                    safe(nomeServico, "servico"),
                    safe(nomeProfissional, "profissional")
            );
            return enviarComTemplate(
                    emailCliente,
                    "Lembrete de agendamento",
                    "Voce tem um agendamento marcado",
                    "Ola %s, nao esqueca do seu compromisso.".formatted(safe(nomeCliente, "cliente")),
                    corpo,
                    montarUrlMeuGendaz(slug) + "/agenda",
                    "Acessar o portal"
            );
        } catch (Exception e) {
            log.error("[resend] erro ao montar email lembrete: {}", e.getMessage(), e);
            return false;
        }
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

    private String montarUrlMeuGendaz(String slug) {
        String base = montarUrlBase();
        if (slug == null || slug.isBlank()) {
            return base + "/meu-gendaz";
        }
        return base + "/meu-gendaz/" + slug.trim().toLowerCase();
    }

    private String montarHtmlBoasVindas(String nomeCliente, String nomeEmpresa) {
        String corpo = """
                <p style=\"margin:0 0 10px;\">Que bom ter voce conosco! Sua conta na <strong>%s</strong> ja esta pronta para uso.</p>
                <p style=\"margin:0;\">Acesse o painel para organizar sua agenda, clientes, servicos e financeiro em um so lugar.</p>
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
                "Ola %s, recebemos sua solicitacao para redefinir a senha.".formatted(nomeCliente),
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
                "Seu codigo de acesso ao Meu Gendaz",
                "Ola %s, use o codigo abaixo para entrar.".formatted(nomeCliente),
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
        // Nunca deixar o cabecalho "so gendaz": se o titulo vier vazio, usa o
        // subtitulo; se ambos vierem vazios, usa uma frase generica.
        String tituloFinal = safe(titulo, "");
        String subtituloFinal = safe(subtitulo, "");
        if (tituloFinal.isBlank()) {
            tituloFinal = subtituloFinal.isBlank() ? "Obrigado pela atencao" : subtituloFinal;
        }
        return """
                <html>
                  <body style=\"margin:0; padding:0; background:#0b0b0c; font-family:Arial, Helvetica, sans-serif; color:#111111;\">
                    <div style=\"max-width:760px; margin:0 auto; padding:36px 20px;\">
                      <div style=\"background:#ffffff; border-radius:20px; overflow:hidden; box-shadow:0 18px 60px rgba(0,0,0,0.18); border:1px solid #e5e7eb;\">
                        <div style=\"background:#ffffff; padding:28px 36px; text-align:center; border-bottom:1px solid #e5e7eb;\">
                          <span style=\"font-size:24px; font-weight:800; letter-spacing:-0.02em; color:#111111;\">gendaz</span>
                        </div>
                        <div style=\"padding:32px 36px 28px; text-align:center; background:#ffffff;\">
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
                safe(badge, "Gendaz"),
                tituloFinal,
                subtituloFinal,
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
