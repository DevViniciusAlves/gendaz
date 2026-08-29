package com.minhaempresa.gendaz.admin.service;

import com.minhaempresa.gendaz.admin.dto.AdminCrmDtos.AdminCrmEmpresaResponse;
import com.minhaempresa.gendaz.admin.dto.AdminCrmDtos.AdminEnviarMensagemRequest;
import com.minhaempresa.gendaz.admin.service.AdminSessionService;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.crm.dto.CrmDtos.CrmUltimaMensagem;
import com.minhaempresa.gendaz.crm.dto.CrmDtos.HistoricoContatoResponse;
import com.minhaempresa.gendaz.crm.entity.CrmContatoEntity;
import com.minhaempresa.gendaz.crm.repository.CrmContatoRepository;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCrmService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final CrmContatoRepository crmContatoRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final AssinaturaService assinaturaService;
    private final ResendEmailService resendEmailService;
    private final AdminSessionService adminSessionService;

    @Value("${app.frontend-url:${FRONTEND_URL:https://gendaz.site}}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<AdminCrmEmpresaResponse> listarEmpresas(String token, String segment, String search,
                                                       String orderBy, Integer period) {
        adminSessionService.validarSessao(token);

        LocalDate hoje = LocalDate.now();
        LocalDate dataLimite = (period != null && period > 0) ? hoje.minusDays(period) : null;

        List<AdminCrmEmpresaResponse> resultado = new ArrayList<>();

        List<EmpresaEntity> empresas = empresaRepository.findAll();
        for (EmpresaEntity empresa : empresas) {
            Long empresaId = empresa.getId();
            if (empresaId == null) continue;

            UsuarioEntity dono = usuarioRepository.findByEmpresaIdAndPerfil(empresaId, PerfilUsuario.DONO)
                    .stream().findFirst().orElse(null);

            String planoAtual = assinaturaService.buscarAtualPorEmpresa(empresaId)
                    .map(a -> a.getPlano() != null ? a.getPlano().getNome() : "BASICO")
                    .orElse("BASICO");
            int quantidadePlanos = (int) assinaturaRepository.findByEmpresaId(empresaId).stream()
                    .filter(a -> a.getStatus() == StatusAssinatura.ATIVA)
                    .count();

            List<AgendamentoEntity> agendamentos = agendamentoRepository.findByEmpresaId(empresaId);

            List<AgendamentoEntity> agendamentosNoPeriodo = agendamentos.stream()
                    .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                    .filter(a -> dataLimite == null || !a.getData().isBefore(dataLimite))
                    .sorted(Comparator.comparing(AgendamentoEntity::getData).reversed())
                    .collect(Collectors.toList());

            int totalAgendamentos = agendamentosNoPeriodo.size();
            double totalGasto = pagamentoRepository
                    .somarValorByEmpresaIdAndStatusIn(
                            empresaId,
                            List.of(StatusPagamento.PAGO, StatusPagamento.PAYMENT_APPROVED)
                    )
                    .doubleValue();
            double gastoMedio = totalAgendamentos > 0 ? totalGasto / totalAgendamentos : 0.0;

            int diasSemAgendar = calcularDiasSemAgendar(empresa, agendamentos);
            LocalDate ultimoAgendamentoData = calcularUltimoAgendamentoData(agendamentos);
            int padraoFrequencia = calcularPadraoFrequencia(agendamentos);
            int scoreRisco = calcularScoreRisco(totalGasto, totalAgendamentos, diasSemAgendar, padraoFrequencia, empresa);

            String seg = calcularSegmento(scoreRisco, empresa);

            CrmUltimaMensagem ultimaMsg = null;
            Optional<CrmContatoEntity> ultimoContato = crmContatoRepository
                    .findFirstByEmpresaIdOrderByDataCriacaoDesc(empresaId);
            if (ultimoContato.isPresent()) {
                CrmContatoEntity c = ultimoContato.get();
                ultimaMsg = new CrmUltimaMensagem(c.getTipo(), c.getTemplate(), c.getDataCriacao(), c.getStatus());
            }

            LocalDateTime ultimaEntradaSite = null;
            for (UsuarioEntity u : usuarioRepository.findByEmpresaId(empresaId)) {
                if (u.getUltimoLogin() != null
                        && (ultimaEntradaSite == null || u.getUltimoLogin().isAfter(ultimaEntradaSite))) {
                    ultimaEntradaSite = u.getUltimoLogin();
                }
            }

            resultado.add(new AdminCrmEmpresaResponse(
                    empresaId,
                    dono != null ? dono.getNome() : null,
                    dono != null && dono.getEmail() != null ? dono.getEmail() : empresa.getEmail(),
                    empresa.getNomeFantasia() != null ? empresa.getNomeFantasia() : "",
                    empresa.getTelefone(),
                    seg,
                    diasSemAgendar,
                    ultimaEntradaSite,
                    ultimoAgendamentoData,
                    totalGasto,
                    gastoMedio,
                    totalAgendamentos,
                    padraoFrequencia,
                    scoreRisco,
                    planoAtual,
                    quantidadePlanos,
                    ultimaMsg
            ));
        }

        return resultado.stream()
                .filter(crm -> {
                    if (segment != null && !segment.isBlank() && !segment.equals("todos")) {
                        if (!crm.segment().equalsIgnoreCase(segment)) return false;
                    }
                    if (search != null && !search.isBlank()) {
                        String termo = search.toLowerCase();
                        String termoDigitos = termo.replaceAll("\\D", "");
                        boolean match = (crm.nome() != null && crm.nome().toLowerCase().contains(termo))
                                || (crm.email() != null && crm.email().toLowerCase().contains(termo))
                                || (crm.empresaNome() != null && crm.empresaNome().toLowerCase().contains(termo))
                                || (crm.telefone() != null && crm.telefone().toLowerCase().contains(termo));
                        if (!match && !termoDigitos.isEmpty()) {
                            if (crm.telefone() != null) {
                                match = crm.telefone().replaceAll("\\D", "").contains(termoDigitos);
                            }
                        }
                        if (!match) return false;
                    }
                    return true;
                })
                .sorted((a, b) -> {
                    if (orderBy == null || orderBy.isBlank() || orderBy.equals("recente")) {
                        return Long.compare(b.id(), a.id());
                    } else if (orderBy.equals("maior_gasto")) {
                        return Double.compare(b.totalGasto(), a.totalGasto());
                    } else if (orderBy.equals("menor_gasto")) {
                        return Double.compare(a.totalGasto(), b.totalGasto());
                    } else if (orderBy.equals("dias_sem_agendar_asc")) {
                        return Integer.compare(a.diasSemAgendar(), b.diasSemAgendar());
                    } else if (orderBy.equals("dias_sem_agendar_desc")) {
                        return Integer.compare(b.diasSemAgendar(), a.diasSemAgendar());
                    }
                    return 0;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> enviarMensagem(String token, Long empresaId, AdminEnviarMensagemRequest request) {
        adminSessionService.validarSessao(token);

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new BusinessException("Empresa nao encontrada."));

        UsuarioEntity dono = usuarioRepository.findByEmpresaIdAndPerfil(empresaId, PerfilUsuario.DONO)
                .stream().findFirst().orElse(null);

        String emailDestino = dono != null && dono.getEmail() != null && !dono.getEmail().isBlank()
                ? dono.getEmail()
                : empresa.getEmail();
        if (emailDestino == null || emailDestino.isBlank()) {
            throw new BusinessException("A empresa nao possui e-mail cadastrado para contato.");
        }
        String nomeDestino = dono != null && dono.getNome() != null ? dono.getNome() : empresa.getNomeFantasia();

        String assunto = montarAssunto(request.template(), nomeDestino);
        String slug = empresa.getAgendamentoSlug();
        String titulo = montarTitulo(request.template());
        String subtitulo = montarSubtitulo(request.template());
        String ctaTexto = "resgate".equals(request.template()) ? "Voltar para o Gendaz" : "Acessar o Gendaz";
        String corpo = montarCorpo(request.template(), nomeDestino, request.customMessage(), slug);
        boolean enviado = resendEmailService.enviarComTemplate(
                emailDestino,
                assunto,
                titulo,
                subtitulo,
                corpo,
                montarUrlMeuGendaz(request.template(), slug),
                ctaTexto
        );

        CrmContatoEntity contato = CrmContatoEntity.builder()
                .empresa(empresa)
                .tipo(request.canal() != null ? request.canal() : "email")
                .template(request.template())
                .assunto(assunto)
                .mensagem(request.customMessage() != null ? request.customMessage() : corpo)
                .status(enviado ? "enviado" : "nao_entregue")
                .build();
        crmContatoRepository.save(contato);

        return Map.of(
                "success", enviado,
                "messageId", String.valueOf(contato.getId()),
                "status", contato.getStatus(),
                "timestamp", contato.getDataCriacao().toString()
        );
    }

    @Transactional(readOnly = true)
    public List<HistoricoContatoResponse> historicoContatos(String token, Long empresaId) {
        adminSessionService.validarSessao(token);

        if (!empresaRepository.existsById(empresaId)) {
            throw new BusinessException("Empresa nao encontrada.");
        }
        List<CrmContatoEntity> contatos = crmContatoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId);
        return contatos.stream().map(c -> new HistoricoContatoResponse(
                c.getId(),
                c.getTipo(),
                c.getTemplate(),
                c.getAssunto(),
                c.getDataCriacao(),
                c.getStatus(),
                c.getAberturaData()
        )).toList();
    }

    private int calcularDiasSemAgendar(EmpresaEntity empresa, List<AgendamentoEntity> agendamentos) {
        Optional<AgendamentoEntity> ultimo = agendamentos.stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                .max(Comparator.comparing(AgendamentoEntity::getData));

        if (ultimo.isPresent()) {
            int dias = (int) ChronoUnit.DAYS.between(ultimo.get().getData(), LocalDate.now());
            return Math.max(0, dias);
        }

        if (empresa.getDataCriacao() != null) {
            int dias = (int) ChronoUnit.DAYS.between(empresa.getDataCriacao().toLocalDate(), LocalDate.now());
            return Math.max(0, dias);
        }

        return 0;
    }

    private LocalDate calcularUltimoAgendamentoData(List<AgendamentoEntity> agendamentos) {
        return agendamentos.stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                .max(Comparator.comparing(AgendamentoEntity::getData))
                .map(AgendamentoEntity::getData)
                .orElse(null);
    }

    private int calcularPadraoFrequencia(List<AgendamentoEntity> agendamentos) {
        List<AgendamentoEntity> ordenados = agendamentos.stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                .sorted(Comparator.comparing(AgendamentoEntity::getData))
                .toList();
        if (ordenados.size() < 2) return 30;
        long totalDias = 0;
        for (int i = 0; i < ordenados.size() - 1; i++) {
            totalDias += ChronoUnit.DAYS.between(
                    ordenados.get(i).getData(),
                    ordenados.get(i + 1).getData());
        }
        return (int) (totalDias / (ordenados.size() - 1));
    }

    private String calcularSegmento(int scoreRisco, EmpresaEntity empresa) {
        if (empresa.getDataCriacao() != null
                && ChronoUnit.DAYS.between(empresa.getDataCriacao().toLocalDate(), LocalDate.now()) < 7) return "novo";
        if (scoreRisco >= 70) return "at_risk";
        return "regular";
    }

    private int calcularScoreRisco(double totalGasto, int agendamentos, int diasSemAgendar, int padraoFrequencia, EmpresaEntity empresa) {
        if (agendamentos <= 0) {
            return 87;
        }
        if (diasSemAgendar > 60) {
            return 87;
        }
        if (diasSemAgendar > 30) {
            return 72;
        }
        if (diasSemAgendar > padraoFrequencia) {
            return 58;
        }
        if (totalGasto <= 0) {
            return 46;
        }
        if (empresa.getDataCriacao() != null
                && ChronoUnit.DAYS.between(empresa.getDataCriacao().toLocalDate(), LocalDate.now()) < 30) {
            return 24;
        }
        return 34;
    }

    private String montarAssunto(String template, String nome) {
        return switch (template) {
            case "resgate" -> "Estamos com saudade dos seus agendamentos, " + nome + "!";
            case "reconexao" -> nome + ", sentimos sua falta!";
            case "promocao" -> "Oferta especial pra voce, " + nome + "!";
            case "lembrete" -> nome + ", lembrete do seu proximo compromisso";
            default -> "Mensagem da nossa equipe";
        };
    }

    private String montarTitulo(String template) {
        return "resgate".equals(template) ? "Estamos com saudade dos seus agendamentos" : "Queremos falar com voce novamente";
    }

    private String montarSubtitulo(String template) {
        return "resgate".equals(template)
                ? "Faz tempo que voce nao acessa o Gendaz. Estamos com saudade dos seus agendamentos e queremos voce de volta."
                : "A Gendaz esta pronta para atender voce de novo com praticidade e proximidade.";
    }

    private String montarCorpo(String template, String nome, String customMessage, String slugEmpresa) {
        String nomeSafe = nome != null ? nome : "cliente";
        String msgPersonalizada = customMessage != null && !customMessage.isBlank() ? customMessage : null;

        String mensagemPadrao = switch (template) {
            case "resgate" -> "Oi " + nomeSafe + "! Faz um tempo que voce nao entra no Gendaz. O que aconteceu? Estamos com saudade dos seus agendamentos e queremos voce de volta por aqui.";
            case "reconexao" -> nomeSafe + ", faz tempo que nao aparece por aqui! Queremos saber como voce esta e deixar tudo pronto para sua volta.";
            case "promocao" -> nomeSafe + ", preparamos uma oferta especial so pra voce! Aproveite e agende seu proximo atendimento com desconto.";
            case "lembrete" -> nomeSafe + ", lembrete: voce tem um compromisso agendado. Se precisar remarcar, esta tudo bem!";
            default -> "Entre em contato conosco para mais informacoes.";
        };

        String textoFinal = msgPersonalizada != null ? msgPersonalizada : mensagemPadrao;
        String ctaUrl = montarUrlGendaz(template, slugEmpresa);

        return """
                <p style="margin:0 0 12px; font-size:15px; line-height:1.8; color:#111111;">%s</p>
                <p style="margin:0; font-size:14px; line-height:1.7; color:#6b7280;">
                  Voce tambem pode acessar diretamente o Gendaz da sua empresa:
                  <a href="%s" style="color:#111111; text-decoration:underline; font-weight:700;">%s</a>
                </p>
                """.formatted(textoFinal, ctaUrl, ctaUrl);
    }

    private String montarUrlMeuGendaz(String template, String slugEmpresa) {
        if ("resgate".equals(template) || "reconexao".equals(template)) {
            return montarUrlBase();
        }
        String baseNormalizada = montarUrlBase();
        if (slugEmpresa == null || slugEmpresa.isBlank()) {
            return baseNormalizada + "/meu-gendaz";
        }
        return baseNormalizada + "/meu-gendaz/" + slugEmpresa.trim().toLowerCase();
    }

    private String montarUrlGendaz(String template, String slugEmpresa) {
        if ("resgate".equals(template) || "reconexao".equals(template)) {
            return montarUrlBase();
        }
        String baseNormalizada = montarUrlBase();
        return baseNormalizada + "/sistema/dashboard";
    }

    private String montarUrlBase() {
        String base = frontendUrl == null || frontendUrl.isBlank() ? "https://gendaz.site" : frontendUrl.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
