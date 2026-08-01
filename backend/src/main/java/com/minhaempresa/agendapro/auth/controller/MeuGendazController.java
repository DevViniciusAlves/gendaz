package com.minhaempresa.agendapro.auth.controller;

import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.*;
import com.minhaempresa.agendapro.agendamento.service.AgendamentoService;
import com.minhaempresa.agendapro.auth.service.UsuarioSessionService;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.servico.service.ServicoService;
import com.minhaempresa.agendapro.profissional.service.ProfissionalService;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.SessaoExpiradaException;
import com.minhaempresa.agendapro.shared.CookieHelper;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meu-gendaz")
@RequiredArgsConstructor
@Slf4j
public class MeuGendazController {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final ServicoService servicoService;
    private final ProfissionalService profissionalService;
    private final AgendamentoService agendamentoService;
    private final UsuarioSessionService usuarioSessionService;
    private final SanitizacaoService sanitizacaoService;

    private UsuarioEntity findUserFromSession(HttpServletRequest request) {
        // Tenta cookie primeiro
        String session = CookieHelper.lerCookie(request, "meu_gendaz_session").orElse(null);

        // Se não, tenta header
        if (session == null || session.isBlank()) {
            session = request.getHeader("X-Session-Token");
        }

        if (session == null || session.isBlank()) {
            throw new SessaoExpiradaException("Sessão não encontrada. Faça login novamente.");
        }

        Optional<UsuarioEntity> user = usuarioRepository.findBySessaoAtiva(session);

        return user.orElseThrow(() -> new SessaoExpiradaException("Sessão inválida. Faça login novamente."));
    }

    private Long getEmpresaId(UsuarioEntity user) {
        if (user.getEmpresa() == null) {
            throw new BusinessException("Empresa não encontrada para este usuário.");
        }
        return user.getEmpresa().getId();
    }

    private ClienteEntity findOrCreateCliente(UsuarioEntity user) {
        Long empresaId = getEmpresaId(user);
        return clienteRepository.findFirstByEmpresaIdAndEmail(empresaId, user.getEmail())
                .orElseGet(() -> clienteRepository.save(ClienteEntity.builder()
                        .nome(user.getNome())
                        .email(user.getEmail())
                        .telefone(obterTelefoneValidoParaCliente(user))
                        .empresa(user.getEmpresa())
                        .build()));
    }

    private String obterTelefoneValidoParaCliente(UsuarioEntity user) {
        Long empresaId = getEmpresaId(user);
        String telefone = sanitizacaoService.telefone(user.getEmpresa() != null ? user.getEmpresa().getTelefone() : null);
        if (telefone == null || clienteRepository.existsByEmpresaIdAndTelefone(empresaId, telefone)) {
            throw new BusinessException("Complete seu perfil com um telefone valido antes de continuar.");
        }
        return telefone;
    }

    // === EMPRESA INFO BY SLUG ===
    @GetMapping("/empresa/{slug}")
    public ResponseEntity<?> empresaPorSlug(@PathVariable String slug) {
        Optional<EmpresaEntity> empresa = empresaRepository.findByAgendamentoSlug(slug);
        if (empresa.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensagem", "Empresa não encontrada."));
        }
        EmpresaEntity e = empresa.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", e.getId());
        result.put("nome", e.getNomeFantasia());
        result.put("telefone", e.getTelefone());
        result.put("email", e.getEmail());
        return ResponseEntity.ok(result);
    }

    // === PROFILE ===
    @GetMapping("/perfil")
    public ResponseEntity<?> perfil(HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            EmpresaEntity empresa = user.getEmpresa();
            ClienteEntity cliente = empresa == null ? null : clienteRepository.findFirstByEmpresaIdAndEmail(empresa.getId(), user.getEmail()).orElse(null);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", user.getId());
            result.put("nome", cliente != null ? cliente.getNome() : user.getNome());
            result.put("email", cliente != null ? cliente.getEmail() : user.getEmail());
            result.put("telefone", cliente != null ? cliente.getTelefone() : null);
            result.put("empresaId", empresa != null ? empresa.getId() : null);
            result.put("empresaNome", empresa != null ? empresa.getNomeFantasia() : null);
            result.put("empresaTelefone", empresa != null ? empresa.getTelefone() : null);
            result.put("empresaEmail", empresa != null ? empresa.getEmail() : null);
            result.put("empresaSlug", empresa != null ? empresa.getAgendamentoSlug() : null);
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    // === SERVICES ===
    @GetMapping("/servicos")
    public ResponseEntity<?> servicos(HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            Long empresaId = getEmpresaId(user);
            return ResponseEntity.ok(servicoService.listarPorEmpresa(empresaId));
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    // === PROFESSIONALS ===
    @GetMapping("/profissionais")
    public ResponseEntity<?> profissionais(HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            Long empresaId = getEmpresaId(user);
            return ResponseEntity.ok(profissionalService.listarPorEmpresa(empresaId));
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    // === AVAILABLE TIME SLOTS ===
    @GetMapping("/horarios-disponiveis")
    public ResponseEntity<?> horariosDisponiveis(
            @RequestParam Long servicoId,
            @RequestParam Long profissionalId,
            @RequestParam String data,
            HttpServletRequest request
    ) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            Long empresaId = getEmpresaId(user);
            java.time.LocalDate dataParsed = java.time.LocalDate.parse(data);
            List<String> horarios = agendamentoService.horariosDisponiveis(empresaId, profissionalId, servicoId, dataParsed);
            List<Map<String, Object>> result = horarios.stream().map(h -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("horario", h);
                map.put("disponivel", true);
                return map;
            }).toList();
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return ResponseEntity.status(400).body(Map.of("mensagem", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Dados inválidos."));
        }
    }

    // === UPCOMING APPOINTMENTS ===
    @GetMapping("/agendamentos/proximos")
    public ResponseEntity<?> agendamentosProximos(HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            Long empresaId = getEmpresaId(user);
            ClienteEntity cliente = findOrCreateCliente(user);
            List<AgendamentoResponse> agendamentos = agendamentoService.listarPorCliente(empresaId, cliente.getId());
            List<AgendamentoResponse> futuros = agendamentos.stream()
                    .filter(a -> a.data() != null && !a.data().isBefore(java.time.LocalDate.now()))
                    .sorted(Comparator.comparing(AgendamentoResponse::data).thenComparing(AgendamentoResponse::horaInicio))
                    .toList();
            return ResponseEntity.ok(futuros);
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    // === HISTORY ===
    @GetMapping("/agendamentos/historico")
    public ResponseEntity<?> historico(
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int limite,
            HttpServletRequest request
    ) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            Long empresaId = getEmpresaId(user);
            ClienteEntity cliente = findOrCreateCliente(user);
            List<AgendamentoResponse> agendamentos = agendamentoService.listarPorCliente(empresaId, cliente.getId());
            List<AgendamentoResponse> passados = agendamentos.stream()
                    .filter(a -> a.data() != null && a.data().isBefore(java.time.LocalDate.now()))
                    .sorted(Comparator.comparing(AgendamentoResponse::data).reversed())
                    .toList();
            int total = passados.size();
            int start = (pagina - 1) * limite;
            int end = Math.min(start + limite, total);
            List<AgendamentoResponse> page = start < total ? passados.subList(start, end) : List.of();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("agendamentos", page);
            result.put("total", total);
            result.put("pagina", pagina);
            result.put("totalPaginas", (int) Math.ceil((double) total / limite));
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    // === CREATE APPOINTMENT ===
    @PostMapping("/agendamentos/criar")
    public ResponseEntity<?> criarAgendamento(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            Long empresaId = getEmpresaId(user);
            ClienteEntity cliente = findOrCreateCliente(user);

            CriarAgendamentoRequest agendamentoRequest = new CriarAgendamentoRequest(
                    cliente.getId(),
                    Long.valueOf(body.get("servicoId").toString()),
                    body.get("profissionalId") != null ? Long.valueOf(body.get("profissionalId").toString()) : null,
                    empresaId,
                    java.time.LocalDate.parse(body.get("data").toString()),
                    java.time.LocalTime.parse(body.get("hora").toString()),
                    body.get("observacoes") != null ? body.get("observacoes").toString() : null
            );
            AgendamentoResponse response = agendamentoService.criar(agendamentoRequest);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Dados inválidos: " + e.getMessage()));
        }
    }

    // === REBOOK ===
    @PatchMapping("/agendamentos/{id}/reagendar")
    public ResponseEntity<?> reagendar(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            Long empresaId = getEmpresaId(user);
            RemarcarAgendamentoRequest req = new RemarcarAgendamentoRequest(
                    java.time.LocalDate.parse(body.get("novaData")),
                    java.time.LocalTime.parse(body.get("novaHora"))
            );
            AgendamentoResponse response = agendamentoService.remarcar(id, req, empresaId);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Dados inválidos: " + e.getMessage()));
        }
    }

    // === CANCEL ===
    @DeleteMapping("/agendamentos/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            agendamentoService.cancelar(id, getEmpresaId(user));
            return ResponseEntity.ok(Map.of("mensagem", "Agendamento cancelado com sucesso."));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
        }
    }

    // === LOGOUT ===
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            String session = CookieHelper.lerCookie(request, "meu_gendaz_session").orElse(null);
            if (session == null || session.isBlank()) {
                session = request.getHeader("X-Session-Token");
            }
            if (session != null) {
                UsuarioEntity user = usuarioRepository.findBySessaoAtiva(session).orElse(null);
                if (user != null) {
                    user.setSessaoAtiva(null);
                    usuarioRepository.save(user);
                }
            }
        } catch (Exception e) {
            log.warn("[meu-gendaz] erro no logout: {}", e.getMessage());
        }
        ResponseCookie clearCookie = ResponseCookie.from("meu_gendaz_session", "")
                .httpOnly(true).secure(true).path("/").sameSite("None").maxAge(Duration.ZERO).build();
        response.addHeader("Set-Cookie", clearCookie.toString());
        return ResponseEntity.ok(Map.of("mensagem", "Logout realizado."));
    }

    // === DASHBOARD ===
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            ClienteEntity cliente = findOrCreateCliente(user);

            List<AgendamentoResponse> todos = agendamentoService.listarPorCliente(getEmpresaId(user), cliente.getId());

            List<AgendamentoResponse> futuros = todos.stream()
                    .filter(a -> a.data() != null && !a.data().isBefore(java.time.LocalDate.now()))
                    .sorted(Comparator.comparing(AgendamentoResponse::data).thenComparing(AgendamentoResponse::horaInicio))
                    .toList();

            List<AgendamentoResponse> passados = todos.stream()
                    .filter(a -> a.data() != null && a.data().isBefore(java.time.LocalDate.now()))
                    .sorted(Comparator.comparing(AgendamentoResponse::data).reversed())
                    .limit(5)
                    .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("proximoAgendamento", futuros.isEmpty() ? null : futuros.get(0));
            result.put("ultimosAtendimentos", passados);
            result.put("totalAgendamentos", todos.size());
            result.put("agendamentosFuturos", futuros.size());
            result.put("promocoes", List.of());
            result.put("notificacoes", List.of());
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    // === PROMOTIONS / COUPONS ===
    @GetMapping("/promocoes")
    public ResponseEntity<?> promocoes(HttpServletRequest request) {
        findUserFromSession(request);
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/cupons")
    public ResponseEntity<?> cupons(HttpServletRequest request) {
        findUserFromSession(request);
        return ResponseEntity.ok(List.of());
    }

    // === NOTIFICATIONS ===
    @GetMapping("/notificacoes")
    public ResponseEntity<?> notificacoes(HttpServletRequest request) {
        findUserFromSession(request);
        return ResponseEntity.ok(List.of());
    }

    @PatchMapping("/notificacoes")
    public ResponseEntity<?> atualizarNotificacoes(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        findUserFromSession(request);
        return ResponseEntity.ok(body);
    }

    // === PRIVACY ===
    @PatchMapping("/privacidade")
    public ResponseEntity<?> atualizarPrivacidade(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        findUserFromSession(request);
        return ResponseEntity.ok(body);
    }

    // === UPDATE PROFILE WITH VALIDATION ===
    @PatchMapping("/perfil")
    public ResponseEntity<?> atualizarPerfil(@RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            EmpresaEntity empresa = user.getEmpresa();
            if (empresa == null) {
                throw new BusinessException("Empresa nao encontrada para este usuario.");
            }

            Long empresaId = empresa.getId();
            ClienteEntity cliente = clienteRepository.findFirstByEmpresaIdAndEmail(empresaId, user.getEmail()).orElse(null);
            String nome = body.get("nome") == null ? "" : body.get("nome").trim();
            String email = body.get("email") == null ? "" : body.get("email").trim().toLowerCase();
            String telefone = sanitizacaoService.telefone(body.get("telefone"));

            List<String> erros = new ArrayList<>();

            if (nome.length() < 3) {
                erros.add("Nome deve ter pelo menos 3 caracteres.");
            }
            if (nome.matches("^\\d+$")) {
                erros.add("Nome nao pode conter apenas numeros.");
            }
            if (email.isBlank() || !email.contains("@") || !email.contains(".")) {
                erros.add("Email invalido.");
            }
            if (telefone == null || telefone.isBlank()) {
                erros.add("Telefone e obrigatorio.");
            }

            if (telefone != null) {
                Optional<ClienteEntity> clienteMesmoTelefone = clienteRepository.findFirstByEmpresaIdAndTelefone(empresaId, telefone);
                if (clienteMesmoTelefone.isPresent() && (cliente == null || !clienteMesmoTelefone.get().getId().equals(cliente.getId()))) {
                    erros.add("Ja existe um cliente com este telefone.");
                }
            }
            if (!email.isBlank()) {
                Optional<ClienteEntity> clienteMesmoEmail = clienteRepository.findFirstByEmpresaIdAndEmail(empresaId, email);
                if (clienteMesmoEmail.isPresent() && (cliente == null || !clienteMesmoEmail.get().getId().equals(cliente.getId()))) {
                    erros.add("Ja existe um cliente com este e-mail.");
                }
            }

            if (!erros.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("mensagem", String.join(" ", erros)));
            }

            user.setNome(nome);
            user.setEmail(email);
            usuarioRepository.save(user);

            if (cliente == null) {
                cliente = ClienteEntity.builder()
                        .nome(nome)
                        .email(email)
                        .telefone(telefone)
                        .empresa(empresa)
                        .build();
            } else {
                cliente.setNome(nome);
                cliente.setEmail(email);
                cliente.setTelefone(telefone);
            }
            clienteRepository.save(cliente);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", user.getId());
            result.put("nome", user.getNome());
            result.put("email", user.getEmail());
            result.put("telefone", cliente.getTelefone());
            result.put("empresaId", empresa.getId());
            result.put("empresaNome", empresa.getNomeFantasia());
            result.put("mensagem", "Perfil atualizado com sucesso!");
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
        }
    }
}

