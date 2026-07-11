package com.minhaempresa.agendapro.auth.controller;

import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.*;
import com.minhaempresa.agendapro.agendamento.service.AgendamentoService;
import com.minhaempresa.agendapro.auth.service.UsuarioSessionService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.servico.service.ServicoService;
import com.minhaempresa.agendapro.profissional.service.ProfissionalService;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.CookieHelper;
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
    private final ServicoService servicoService;
    private final ProfissionalService profissionalService;
    private final AgendamentoService agendamentoService;
    private final UsuarioSessionService usuarioSessionService;

    // --- Helper to find user from meu_gendaz_session cookie ---
    private UsuarioEntity findUserFromSession(HttpServletRequest request) {
        String session = CookieHelper.lerCookie(request, "meu_gendaz_session").orElse(null);
        if (session == null || session.isBlank()) {
            throw new BusinessException("Sessão não encontrada. Faça login novamente.");
        }
        return usuarioRepository.findBySessaoAtiva(session)
                .orElseThrow(() -> new BusinessException("Sessão inválida. Faça login novamente."));
    }

    private Long getEmpresaId(UsuarioEntity user) {
        if (user.getEmpresa() == null) {
            throw new BusinessException("Empresa não encontrada.");
        }
        return user.getEmpresa().getId();
    }

    // === PROFILE ===
    @GetMapping("/perfil")
    public ResponseEntity<?> perfil(HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            EmpresaEntity empresa = user.getEmpresa();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", user.getId());
            result.put("nome", user.getNome());
            result.put("email", user.getEmail());
            result.put("empresaId", empresa != null ? empresa.getId() : null);
            result.put("empresaNome", empresa != null ? empresa.getNomeFantasia() : null);
            result.put("empresaTelefone", empresa != null ? empresa.getTelefone() : null);
            result.put("empresaEmail", empresa != null ? empresa.getEmail() : null);
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
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Dados inválidos."));
        }
    }

    // === UPCOMING APPOINTMENTS ===
    @GetMapping("/agendamentos/proximos")
    public ResponseEntity<?> agendamentosProximos(HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            List<AgendamentoResponse> agendamentos = agendamentoService.listarPorCliente(user.getId());
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
            List<AgendamentoResponse> agendamentos = agendamentoService.listarPorCliente(user.getId());
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

            CriarAgendamentoRequest agendamentoRequest = new CriarAgendamentoRequest(
                    user.getId(),
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
            findUserFromSession(request);
            RemarcarAgendamentoRequest req = new RemarcarAgendamentoRequest(
                    java.time.LocalDate.parse(body.get("novaData")),
                    java.time.LocalTime.parse(body.get("novaHora"))
            );
            AgendamentoResponse response = agendamentoService.remarcar(id, req);
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
            findUserFromSession(request);
            agendamentoService.cancelar(id, null);
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
            Long empresaId = getEmpresaId(user);

            List<AgendamentoResponse> todos = agendamentoService.listarPorCliente(user.getId());
            
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

    // === UPDATE PROFILE ===
    @PatchMapping("/perfil")
    public ResponseEntity<?> atualizarPerfil(@RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            UsuarioEntity user = findUserFromSession(request);
            if (body.containsKey("nome")) user.setNome(body.get("nome"));
            if (body.containsKey("email")) user.setEmail(body.get("email"));
            usuarioRepository.save(user);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", user.getId());
            result.put("nome", user.getNome());
            result.put("email", user.getEmail());
            result.put("empresaId", user.getEmpresa() != null ? user.getEmpresa().getId() : null);
            result.put("empresaNome", user.getEmpresa() != null ? user.getEmpresa().getNomeFantasia() : null);
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
        }
    }
}
