/*
  ╔══════════════════════════════════════════════╗
  ║  ⚠️  DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.internal;

import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;
import com.minhaempresa.agendapro.profissional.repository.ProfissionalRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConnectRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConnectResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappAgendamentoIaRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappAgendarRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappAgendarResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConfigResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappFluxoConversaRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappFluxoConversaResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappDisponibilidadeResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappServicoResposta;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappStatusResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappSessionResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappSessionSaveRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappSessionSummaryResponse;
import com.minhaempresa.agendapro.whatsapp.service.WhatsappIntegrationProperties;
import com.minhaempresa.agendapro.whatsapp.service.WhatsappIntegrationService;
import com.minhaempresa.agendapro.whatsapp.service.WhatsappNodeClient;
import com.minhaempresa.agendapro.whatsapp.entity.WhatsappConversationEntity;
import com.minhaempresa.agendapro.whatsapp.entity.WhatsappMessageEntity;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappConversationStatus;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappMessageDirection;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappMessageStatus;
import com.minhaempresa.agendapro.whatsapp.repository.WhatsappConversationRepository;
import com.minhaempresa.agendapro.whatsapp.repository.WhatsappMessageRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsappInternalController {
    private final WhatsappIntegrationProperties properties;
    private final WhatsappNodeClient nodeClient;
    private final WhatsappIntegrationService whatsappIntegrationService;
    private final ClienteRepository clienteRepository;
    private final EmpresaRepository empresaRepository;
    private final ProfissionalRepository profissionalRepository;
    private final WhatsappConversationRepository conversationRepository;
    private final WhatsappMessageRepository messageRepository;

    // @GetMapping("/cliente")  // ⚠️ DESATIVADO
    public ResponseEntity<ClienteInternoResponse> buscarClientePorTelefone(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @RequestParam String phone) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        String telefone = normalizar(phone);
        ClienteEntity cliente = clienteRepository.findFirstByTelefone(telefone)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado."));
        return ResponseEntity.ok(new ClienteInternoResponse(cliente.getId(), cliente.getNome(), cliente.getEmpresa().getId()));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @GetMapping("/config/{tenantId}")  // ⚠️ DESATIVADO
    public ResponseEntity<WhatsappConfigResponse> config(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long tenantId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        WhatsappConfigResponse response = whatsappIntegrationService.contextoDaEmpresa(tenantId);
        log.info(
                "[whatsapp-config-debug] empresaId={} nomeEmpresa='{}' agendamentoSlug='{}' linkAgendamento='{}' servicos={}",
                tenantId,
                response.nomeEmpresa(),
                response.agendamentoSlug(),
                response.linkAgendamento(),
                response.servicos() == null ? 0 : response.servicos().size()
        );
        return ResponseEntity.ok(response);
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @GetMapping("/contexto/{tenantId}")  // ⚠️ DESATIVADO
    public ResponseEntity<WhatsappConfigResponse> contexto(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long tenantId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        WhatsappConfigResponse response = whatsappIntegrationService.contextoDaEmpresa(tenantId);
        log.info(
                "[whatsapp-config-debug] empresaId={} nomeEmpresa='{}' agendamentoSlug='{}' linkAgendamento='{}' servicos={}",
                tenantId,
                response.nomeEmpresa(),
                response.agendamentoSlug(),
                response.linkAgendamento(),
                response.servicos() == null ? 0 : response.servicos().size()
        );
        return ResponseEntity.ok(response);
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @GetMapping("/servicos/{empresaId}")  // ⚠️ DESATIVADO
    public ResponseEntity<List<WhatsappServicoResposta>> servicos(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long empresaId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(whatsappIntegrationService.listarServicosWhatsApp(empresaId));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @GetMapping("/disponibilidade/{empresaId}")  // ⚠️ DESATIVADO
    public ResponseEntity<WhatsappDisponibilidadeResponse> disponibilidade(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long empresaId,
            @RequestParam Long servicoId,
            @RequestParam(required = false) Long profissionalId,
            @RequestParam LocalDate data) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(whatsappIntegrationService.consultarDisponibilidadeWhatsApp(empresaId, servicoId, profissionalId, data));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @PostMapping("/agendar")  // ⚠️ DESATIVADO
    public ResponseEntity<?> agendar(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody WhatsappAgendarRequest request) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        try {
            log.info(
                    "[agendamento-whatsapp] request empresaId={} clienteNome='{}' servicoId={} data={} horario={} remoteJid='{}' origem='{}'",
                    request.empresaId(),
                    request.nomeCliente(),
                    request.servicoId(),
                    request.data(),
                    request.horario(),
                    request.remoteJid(),
                    request.origem()
            );
            WhatsappAgendarResponse response = whatsappIntegrationService.agendarWhatsApp(request);
            return ResponseEntity.ok(response);
        } catch (BusinessException ex) {
            log.warn("[agendamento-whatsapp] falha validacao empresaId={} clienteNome='{}' detalhe={}",
                    request.empresaId(), request.nomeCliente(), ex.getMessage());
            String mensagem = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            String erro = mensagem.contains("servico") ? "SERVICO_INVALIDO" : "VALIDACAO";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "erro", erro,
                    "mensagem", ex.getMessage()
            ));
        } catch (ResourceNotFoundException ex) {
            log.warn("[agendamento-whatsapp] recurso nao encontrado empresaId={} clienteNome='{}' detalhe={}",
                    request.empresaId(), request.nomeCliente(), ex.getMessage());
            String mensagem = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            String erro = mensagem.contains("servico") ? "SERVICO_INVALIDO" : "VALIDACAO";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "erro", erro,
                    "mensagem", ex.getMessage()
            ));
        } catch (com.minhaempresa.agendapro.shared.ConflictException ex) {
            log.warn("[agendamento-whatsapp] conflito empresaId={} clienteNome='{}' detalhe={}",
                    request.empresaId(), request.nomeCliente(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "erro", "HORARIO_INDISPONIVEL",
                    "success", false,
                    "mensagem", ex.getMessage()
            ));
        }
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @GetMapping("/agendamentos/protocolo/{protocolo}")  // ⚠️ DESATIVADO
    public ResponseEntity<Map<String, Object>> buscarAgendamentoPorProtocolo(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable String protocolo,
            @RequestParam Long empresaId,
            @RequestParam(required = false) String telefone) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(whatsappIntegrationService.buscarAgendamentoCancelamentoPorProtocolo(empresaId, protocolo, telefone));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @GetMapping("/agendamentos")  // ⚠️ DESATIVADO
    public ResponseEntity<List<Map<String, Object>>> listarAgendamentosParaCancelamento(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @RequestParam Long empresaId,
            @RequestParam LocalDate data,
            @RequestParam(required = false) String telefone) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(whatsappIntegrationService.listarAgendamentosCancelamentoPorData(empresaId, data, telefone));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @PostMapping("/agendamentos/{id}/cancelar")  // ⚠️ DESATIVADO
    public ResponseEntity<Map<String, Object>> cancelarAgendamento(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        Long empresaId = payload.get("empresaId") == null ? null : Long.valueOf(String.valueOf(payload.get("empresaId")));
        return ResponseEntity.ok(whatsappIntegrationService.cancelarAgendamentoWhatsapp(empresaId, id));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @PutMapping("/agendamentos/{id}/reagendar")  // ⚠️ DESATIVADO
    public ResponseEntity<Map<String, Object>> reagendarAgendamento(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody ReagendarAgendamentoRequest payload) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(whatsappIntegrationService.reagendarAgendamentoWhatsapp(
                payload.empresaId(),
                id,
                payload.novaData(),
                payload.novoHorario(),
                payload.profissionalId()));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @GetMapping("/profissionais/{empresaId}")  // ⚠️ DESATIVADO
    public ResponseEntity<List<Map<String, Object>>> profissionais(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long empresaId,
            @RequestParam(required = false) Long servicoId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        List<Map<String, Object>> response = profissionalRepository.findByEmpresaId(empresaId).stream()
                .filter(profissional -> profissional.getStatus() == com.minhaempresa.agendapro.shared.enums.StatusCadastro.ATIVO)
                .map(profissional -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", profissional.getId());
                    map.put("nome", profissional.getNome());
                    map.put("especialidade", profissional.getEspecialidade());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(response);
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @GetMapping("/sessoes")  // ⚠️ DESATIVADO
    public ResponseEntity<List<WhatsappSessionSummaryResponse>> listarSessoesPersistidas(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(whatsappIntegrationService.listarSessoesPersistidas());
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @GetMapping("/sessao/{empresaId}")  // ⚠️ DESATIVADO
    public ResponseEntity<WhatsappSessionResponse> obterSessaoPersistida(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long empresaId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(whatsappIntegrationService.obterSessaoPersistida(empresaId));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @PutMapping("/sessao/{empresaId}")  // ⚠️ DESATIVADO
    public ResponseEntity<WhatsappSessionResponse> salvarSessaoPersistida(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long empresaId,
            @Valid @RequestBody WhatsappSessionSaveRequest request) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(whatsappIntegrationService.salvarSessaoPersistida(empresaId, request));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @DeleteMapping("/sessao/{empresaId}")  // ⚠️ DESATIVADO
    public ResponseEntity<MensagemResposta> removerSessaoPersistida(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long empresaId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        whatsappIntegrationService.removerSessaoPersistida(empresaId);
        return ResponseEntity.ok(new MensagemResposta("ok"));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @PostMapping("/sessao/{empresaId}/conectar")  // ⚠️ DESATIVADO
    public ResponseEntity<Map<String, Object>> marcarConectado(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long empresaId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada"));
        empresa.setWhatsappConnected(Boolean.TRUE);
        empresaRepository.save(empresa);
        log.info("[whatsapp-endpoint] empresaId={} marcado como CONECTADO", empresaId);
        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "status", "CONECTADO",
                "empresaId", empresaId,
                "timestamp", new Date().toString()
        ));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @PostMapping("/sessao/{empresaId}/desconectar")  // ⚠️ DESATIVADO
    public ResponseEntity<Map<String, Object>> marcarDesconectado(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long empresaId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada"));
        empresa.setWhatsappConnected(Boolean.FALSE);
        empresaRepository.save(empresa);
        log.info("[whatsapp-endpoint] empresaId={} marcado como DESCONECTADO", empresaId);
        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "status", "DESCONECTADO",
                "empresaId", empresaId,
                "timestamp", new Date().toString()
        ));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @PostMapping("/mensagem")  // ⚠️ DESATIVADO
    public ResponseEntity<MensagemResposta> mensagem(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody MensagemRequest request) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        registrarMensagem(request.tenantId(), request.phone(), request.mensagem(), request.origem());
        return ResponseEntity.ok(new MensagemResposta("ok"));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @PostMapping("/notificar")  // ⚠️ DESATIVADO
    public ResponseEntity<MensagemResposta> notificar(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody NotificarRequest request) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        validarTokenInterno(internalToken);
        nodeClient.enviarMensagem(request.tenantId(), request.phone(), request.mensagem());
        return ResponseEntity.ok(new MensagemResposta("ok"));
        */
        return ResponseEntity.ok(null); // Stub desativado
    }

    // @PostMapping("/agendamento-ia")  // ⚠️ DESATIVADO
    public ResponseEntity<MensagemResposta> agendamentoIa(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody WhatsappAgendamentoIaRequest request) {
        // DESATIVADO
        /*
        validarTokenInterno(internalToken);
        whatsappIntegrationService.processarAgendamentoIa(request);
        return ResponseEntity.ok(new MensagemResposta("ok"));
        */
        return ResponseEntity.ok(null);
    }

    // @PostMapping("/marcar-lembrete-enviado")  // ⚠️ DESATIVADO
    public ResponseEntity<Map<String, Object>> marcarLembreteEnviado(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @RequestBody Map<String, Object> payload) {
        // DESATIVADO
        /*
        validarTokenInterno(internalToken);
        Long agendamentoId = payload.get("agendamentoId") == null ? null : Long.valueOf(String.valueOf(payload.get("agendamentoId")));
        String tipo = payload.get("tipo") == null ? null : String.valueOf(payload.get("tipo"));
        return ResponseEntity.ok(whatsappIntegrationService.marcarLembreteEnviado(agendamentoId, tipo));
        */
        return ResponseEntity.ok(null);
    }

    // @PutMapping("/agendamentos/{id}/confirmacao-pagamento-dono")  // ⚠️ DESATIVADO
    public ResponseEntity<Map<String, Object>> confirmarPagamentoDono(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        // DESATIVADO
        /*
        validarTokenInterno(internalToken);
        Long empresaId = payload.get("empresaId") == null ? null : Long.valueOf(String.valueOf(payload.get("empresaId")));
        String statusPagamento = payload.get("statusPagamento") == null ? null : String.valueOf(payload.get("statusPagamento"));
        return ResponseEntity.ok(whatsappIntegrationService.confirmarPagamentoDonoWhatsapp(empresaId, id, statusPagamento));
        */
        return ResponseEntity.ok(null);
    }

    // @GetMapping("/fluxo")  // ⚠️ DESATIVADO
    public ResponseEntity<WhatsappFluxoConversaResponse> obterFluxo(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @RequestParam Long empresaId,
            @RequestParam String telefone) {
        // DESATIVADO
        /*
        validarTokenInterno(internalToken);
        WhatsappFluxoConversaResponse response = whatsappIntegrationService.obterFluxoConversa(empresaId, telefone);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
        */
        return ResponseEntity.ok(null);
    }

    // @PostMapping("/fluxo/salvar")  // ⚠️ DESATIVADO
    public ResponseEntity<WhatsappFluxoConversaResponse> salvarFluxo(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody WhatsappFluxoConversaRequest request) {
        // DESATIVADO
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(whatsappIntegrationService.salvarFluxoConversa(request));
        */
        return ResponseEntity.ok(null);
    }

    // @PostMapping("/fluxo/resetar")  // ⚠️ DESATIVADO
    public ResponseEntity<MensagemResposta> resetarFluxo(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody FluxoResetRequest request) {
        // DESATIVADO
        /*
        validarTokenInterno(internalToken);
        whatsappIntegrationService.resetarFluxoConversa(request.empresaId(), request.telefoneCliente());
        return ResponseEntity.ok(new MensagemResposta("ok"));
        */
        return ResponseEntity.ok(null);
    }

    // @GetMapping("/conversa-pausada")  // ⚠️ DESATIVADO
    public ResponseEntity<ConversaPausadaResponse> conversaPausada(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @RequestParam Long empresaId,
            @RequestParam String telefone) {
        // DESATIVADO
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(new ConversaPausadaResponse(whatsappIntegrationService.conversaPausada(empresaId, telefone)));
        */
        return ResponseEntity.ok(null);
    }

    // @PostMapping("/conectar")  // ⚠️ DESATIVADO
    public ResponseEntity<WhatsappConnectResponse> conectar(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody WhatsappConnectRequest request) {
        // DESATIVADO
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(nodeClient.conectar(request));
        */
        return ResponseEntity.ok(null);
    }

    // @GetMapping("/status")  // ⚠️ DESATIVADO
    public ResponseEntity<WhatsappStatusResponse> status(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken) {
        // DESATIVADO
        /*
        validarTokenInterno(internalToken);
        return ResponseEntity.ok(nodeClient.status(null));
        */
        return ResponseEntity.ok(null);
    }

    // @PutMapping("/config/{tenantId}")  // ⚠️ DESATIVADO
    public ResponseEntity<WhatsappConfigResponse> atualizarConfig(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.PathVariable Long tenantId,
            @Valid @RequestBody AtualizarConfigRequest request) {
        // DESATIVADO
        /*
        validarTokenInterno(internalToken);
        EmpresaEntity empresa = empresaRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
        empresa.setWhatsappSecretariaIaEnabled(request.iaHabilitada());
        empresa.setWhatsappNotificationsEnabled(request.notificacoesHabilitadas());
        empresaRepository.save(empresa);
        return config(internalToken, tenantId);
        */
        return ResponseEntity.ok(null);
    }

    // ⚠️ DESATIVADO
    private void registrarMensagem(Long tenantId, String phone, String mensagem, String origem) {
        // DESATIVADO
        /*
        EmpresaEntity empresa = empresaRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
        String telefone = normalizar(phone);
        ClienteEntity cliente = clienteRepository.findFirstByEmpresaIdAndTelefone(tenantId, telefone).orElse(null);
        WhatsappConversationEntity conversa = conversationRepository.findByEmpresaIdAndContactPhone(tenantId, telefone)
                .orElseGet(() -> WhatsappConversationEntity.builder()
                        .empresa(empresa)
                        .contactName(cliente != null ? cliente.getNome() : telefone)
                        .contactPhone(telefone)
                        .status(WhatsappConversationStatus.OPEN)
                        .build());
        conversa.setLastMessageAt(LocalDateTime.now());
        WhatsappConversationEntity salva = conversationRepository.save(conversa);
        messageRepository.save(WhatsappMessageEntity.builder()
                .empresa(empresa)
                .conversation(salva)
                .direction("IA".equalsIgnoreCase(origem) ? WhatsappMessageDirection.OUTBOUND : WhatsappMessageDirection.INBOUND)
                .fromNumber("IA".equalsIgnoreCase(origem) ? empresa.getWhatsappPhone() : telefone)
                .toNumber("IA".equalsIgnoreCase(origem) ? telefone : empresa.getWhatsappPhone())
                .messageText(mensagem)
                .providerMessageId("internal-" + System.nanoTime())
                .status(WhatsappMessageStatus.RECEIVED)
                .build());
        */
    }

    // ⚠️ DESATIVADO
    private void validarTokenInterno(String recebido) {
        // DESATIVADO
        /*
        String esperado = properties.internalToken();
        if (esperado.isBlank()) {
            return;
        }
        if (recebido == null || recebido.isBlank() || !esperado.equals(recebido.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook interno nao autorizado.");
        }
        */
    }

    // ⚠️ DESATIVADO
    private String normalizar(String valor) {
        // DESATIVADO
        /*
        return valor == null ? "" : valor.replaceAll("\\D", "");
        */
        return "";
    }

    public record ClienteInternoResponse(Long id, String nome, Long tenantId) {}

    public record MensagemRequest(
            @NotNull Long tenantId,
            @NotBlank String phone,
            @NotBlank @Size(max = 4000) String mensagem,
            @NotBlank String origem) {}

    public record NotificarRequest(
            @NotNull Long tenantId,
            @NotBlank String phone,
            @NotBlank @Size(max = 4000) String mensagem) {}

    public record FluxoResetRequest(
            @NotNull Long empresaId,
            @NotBlank String telefoneCliente) {}

    public record ReagendarAgendamentoRequest(
            @NotNull Long empresaId,
            @NotNull LocalDate novaData,
            @NotNull LocalTime novoHorario,
            Long profissionalId) {}

    public record ConversaPausadaResponse(boolean pausada) {}

    public record AtualizarConfigRequest(boolean iaHabilitada, boolean notificacoesHabilitadas) {}

    public record MensagemResposta(String status) {}
}
