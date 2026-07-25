package com.minhaempresa.agendapro.crm.service;

import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.crm.dto.CrmDtos.*;
import com.minhaempresa.agendapro.crm.entity.CrmContatoEntity;
import com.minhaempresa.agendapro.crm.repository.CrmContatoRepository;
import com.minhaempresa.agendapro.email.ResendEmailService;
import com.minhaempresa.agendapro.shared.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrmService {
    private final ClienteRepository clienteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final CrmContatoRepository crmContatoRepository;
    private final ResendEmailService resendEmailService;

    @Transactional(readOnly = true)
    public List<CrmClienteResponse> listarClientes(Long empresaId, String segment, String search,
                                                    String orderBy, Integer period) {
        List<ClienteEntity> todosClientes = clienteRepository.findByEmpresaId(empresaId);
        LocalDate hoje = LocalDate.now();
        LocalDate dataLimite = (period != null && period > 0) ? hoje.minusDays(period) : null;

        List<CrmClienteResponse> resultado = todosClientes.stream()
                .map(cliente -> {
                    List<AgendamentoEntity> agendamentos = agendamentoRepository.findByClienteId(cliente.getId());

                    List<AgendamentoEntity> agendamentosNoPeriodo = agendamentos.stream()
                            .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                            .filter(a -> dataLimite == null || !a.getData().isBefore(dataLimite))
                            .sorted(Comparator.comparing(AgendamentoEntity::getData).reversed())
                            .collect(Collectors.toList());

                    int totalAgendamentos = agendamentosNoPeriodo.size();
                    double totalGasto = agendamentosNoPeriodo.stream()
                            .mapToDouble(a -> a.getServico() != null ? a.getServico().getValor().doubleValue() : 0.0)
                            .sum();

                    int diasSemAgendar = calcularDiasSemAgendar(agendamentos);
                    LocalDate ultimoAgendamentoData = calcularUltimoAgendamentoData(agendamentos);
                    int padraoFrequencia = calcularPadraoFrequencia(agendamentos);

                    String seg = calcularSegmento(totalGasto, totalAgendamentos, diasSemAgendar, cliente);

                    CrmUltimaMensagem ultimaMsg = null;
                    Optional<CrmContatoEntity> ultimoContato = crmContatoRepository.findFirstByClienteIdOrderByDataCriacaoDesc(cliente.getId());
                    if (ultimoContato.isPresent()) {
                        CrmContatoEntity c = ultimoContato.get();
                        ultimaMsg = new CrmUltimaMensagem(c.getTipo(), c.getTemplate(), c.getDataCriacao(), c.getStatus());
                    }

                    return new CrmClienteResponse(
                            cliente.getId(),
                            cliente.getNome() != null ? cliente.getNome() : "",
                            cliente.getTelefone() != null ? cliente.getTelefone() : "",
                            cliente.getEmail() != null ? cliente.getEmail() : "",
                            seg,
                            diasSemAgendar,
                            ultimoAgendamentoData,
                            totalGasto,
                            totalAgendamentos,
                            padraoFrequencia,
                            ultimaMsg
                    );
                })
                .filter(crm -> {
                    if (segment != null && !segment.isBlank() && !segment.equals("todos")) {
                        if (!crm.segment().equalsIgnoreCase(segment)) return false;
                    }
                    if (search != null && !search.isBlank()) {
                        String termo = search.toLowerCase();
                        boolean match = crm.nome().toLowerCase().contains(termo)
                                || crm.telefone().toLowerCase().contains(termo)
                                || crm.email().toLowerCase().contains(termo);
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

        return resultado;
    }

    @Transactional
    public Map<String, Object> enviarMensagem(Long empresaId, Long clienteId, EnviarMensagemRequest request) {
        ClienteEntity cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new BusinessException("Cliente nao encontrado."));

        if (cliente.getEmpresa() == null || !Objects.equals(cliente.getEmpresa().getId(), empresaId)) {
            throw new BusinessException("Empresa nao foi encontrada");
        }

        if (cliente.getEmail() == null || cliente.getEmail().isBlank()) {
            throw new BusinessException("Cliente nao possui e-mail cadastrado.");
        }

        String assunto = montarAssunto(request.template(), cliente.getNome());
        String html = montarHtml(request.template(), cliente.getNome(), request.customMessage());

        boolean enviado = resendEmailService.enviarEmailCrm(cliente.getEmail(), assunto, html);

        CrmContatoEntity contato = CrmContatoEntity.builder()
                .empresa(cliente.getEmpresa())
                .cliente(cliente)
                .tipo(request.canal() != null ? request.canal() : "email")
                .template(request.template())
                .assunto(assunto)
                .mensagem(request.customMessage() != null ? request.customMessage() : html)
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
    public List<HistoricoContatoResponse> historicoContatos(Long empresaId, Long clienteId) {
        ClienteEntity cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new BusinessException("Cliente nao encontrado."));
        if (cliente.getEmpresa() == null || !Objects.equals(cliente.getEmpresa().getId(), empresaId)) {
            throw new BusinessException("Empresa nao foi encontrada");
        }
        List<CrmContatoEntity> contatos = crmContatoRepository.findByClienteIdOrderByDataCriacaoDesc(clienteId);
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

    @Transactional
    public void registrarAbertura(String messageId) {
        try {
            Long id = Long.parseLong(messageId);
            crmContatoRepository.findById(id).ifPresent(contato -> {
                contato.setStatus("aberto");
                contato.setAberturaData(LocalDateTime.now());
                crmContatoRepository.save(contato);
            });
        } catch (Exception e) {
            log.warn("[crm] erro ao registrar abertura messageId={}: {}", messageId, e.getMessage());
        }
    }

    private int calcularDiasSemAgendar(List<AgendamentoEntity> agendamentos) {
        Optional<AgendamentoEntity> ultimo = agendamentos.stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                .max(Comparator.comparing(AgendamentoEntity::getData));
        if (ultimo.isEmpty()) return 9999;
        int dias = (int) ChronoUnit.DAYS.between(ultimo.get().getData(), LocalDate.now());
        return Math.max(0, dias);
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

    private String calcularSegmento(double totalGasto, int agendamentos, int diasSemAgendar, ClienteEntity cliente) {
        if (agendamentos <= 2) return "novo";
        if (cliente.getDataCriacao() != null
                && ChronoUnit.DAYS.between(cliente.getDataCriacao(), LocalDate.now()) < 30) return "novo";
        if (diasSemAgendar > 30) return "at_risk";
        return "regular";
    }

    private String montarAssunto(String template, String nome) {
        return switch (template) {
            case "resgate" -> "Estamos com saudade, " + nome + "!";
            case "reconexao" -> nome + ", sentimos sua falta!";
            case "promocao" -> "Oferta especial pra voce, " + nome + "!";
            case "lembrete" -> nome + ", lembrete do seu proximo compromisso";
            default -> "Mensagem da nossa equipe";
        };
    }

    private String montarHtml(String template, String nome, String customMessage) {
        String nomeSafe = nome != null ? nome : "cliente";
        String msgPersonalizada = customMessage != null && !customMessage.isBlank() ? customMessage : null;

        String mensagemPadrao = switch (template) {
            case "resgate" -> "Oi " + nomeSafe + "! Ta muito tempo sem nos ver. Que tal agendar um horario essa semana? Estamos de portas abertas pra voce!";
            case "reconexao" -> nomeSafe + ", faz tempo que nao aparece por aqui! Queremos saber como voce esta. Que tal um novo atendimento?";
            case "promocao" -> nomeSafe + ", preparamos uma oferta especial so pra voce! Aproveite e agende seu proximo atendimento com desconto.";
            case "lembrete" -> nomeSafe + ", lembrete: voce tem um compromisso agendado. Se precisar remarcar, esta tudo bem!";
            default -> "Entre em contato conosco para mais informacoes.";
        };

        String textoFinal = msgPersonalizada != null ? msgPersonalizada : mensagemPadrao;

        return """
                <html>
                  <body style="font-family: Arial, sans-serif; background-color: #f5f5f5; padding: 24px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 32px;">
                      <h2 style="margin-top: 0;">Mensagem</h2>
                      <p>%s</p>
                      <p style="color: #6b7280; font-size: 12px; margin-top: 24px;">Este e um e-mail automatico.</p>
                    </div>
                  </body>
                </html>
                """.formatted(textoFinal);
    }
}
