package com.minhaempresa.gendaz.auth.controller;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.*;
import com.minhaempresa.gendaz.agendamento.service.AgendamentoService;
import com.minhaempresa.gendaz.auth.dto.MeuGendazDtos.CriarSuporteRequest;
import com.minhaempresa.gendaz.auth.entity.MeuGendazOtpChallengeEntity;
import com.minhaempresa.gendaz.auth.service.MeuGendazAuthService;
import com.minhaempresa.gendaz.auth.service.MeuGendazOnboardingService;
import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.CriarChamadoRequest;
import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.gendaz.chamado.enums.PrioridadeChamado;
import com.minhaempresa.gendaz.chamado.service.ChamadoService;
import com.minhaempresa.gendaz.auth.service.UsuarioSessionService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.cliente.service.ClienteEmailBloqueadoService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.InsightsRequest;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.MeuGendazIAResponse;
import com.minhaempresa.gendaz.insights.service.InsightsService;
import com.minhaempresa.gendaz.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.gendaz.meugendazacesso.repository.MeuGendazAcessoRepository;
import com.minhaempresa.gendaz.meugendazpromocao.service.MeuGendazPromocaoService;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.servico.service.ServicoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CookieHelper;
import com.minhaempresa.gendaz.shared.CookieService;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.SessaoExpiradaException;
import jakarta.servlet.http.HttpServletRequest;

import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/meu-gendaz")
@RequiredArgsConstructor
@Slf4j
public class MeuGendazController {
    private final MeuGendazAcessoRepository meuGendazAcessoRepository;
    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteEmailBloqueadoService clienteEmailBloqueadoService;
    private final ServicoService servicoService;
    private final ProfissionalService profissionalService;
    private final AgendamentoService agendamentoService;
    private final ChamadoService chamadoService;
    private final InsightsService insightsService;
    private final MeuGendazPromocaoService meuGendazPromocaoService;
    private final UsuarioSessionService usuarioSessionService;
    private final MeuGendazAuthService meuGendazAuthService;
    private final MeuGendazOnboardingService onboardingService;
    private final SanitizacaoService sanitizacaoService;
    private final CookieService cookieService;

    private String slugAtual(HttpServletRequest request) {
        String slug = request.getHeader("X-Meu-Gendaz-Slug");
        if (slug == null || slug.isBlank()) {
            throw new SessaoExpiradaException("Slug da empresa nao informado.");
        }
        return slug.trim().toLowerCase();
    }

    private String nomeCookie(String slug) {
        return "meu_gendaz_session_" + slug;
    }

    private String nomeOnboardingCookie(String slug) {
        return "meu_gendaz_onboarding_" + slug;
    }


    private MeuGendazAcessoEntity findAcessoFromSession(HttpServletRequest request) {
        String slug = slugAtual(request);
        EmpresaEntity empresa = empresaRepository.findByAgendamentoSlug(slug)
                .orElseThrow(() -> new SessaoExpiradaException("Loja nao encontrada."));
        String session = CookieHelper.lerCookie(request, nomeCookie(slug))
                .orElseThrow(() -> new SessaoExpiradaException("Sessao nao encontrada. Faca login novamente."));
        
        MeuGendazAcessoEntity acesso = meuGendazAcessoRepository.findByEmpresaIdAndSessaoAtiva(empresa.getId(), session)
                .orElseThrow(() -> new SessaoExpiradaException("Sessao invalida. Faca login novamente."));
        if (!usuarioSessionService.sessaoValidaMeuGendaz(acesso.getId(), session, empresa.getId())) {
            throw new SessaoExpiradaException("Sessao invalida. Faca login novamente.");
        }
        return acesso;
    }

    private EmpresaEntity empresaAtual(HttpServletRequest request) {
        String slug = slugAtual(request);
        return empresaRepository.findByAgendamentoSlug(slug)
                .orElseThrow(() -> new SessaoExpiradaException("Loja nao encontrada."));
    }

    private MeuGendazOtpChallengeEntity findOnboardingFromSession(HttpServletRequest request, EmpresaEntity empresa) {
        String slug = slugAtual(request);
        String token = CookieHelper.lerCookie(request, nomeOnboardingCookie(slug))
                .orElseThrow(() -> new SessaoExpiradaException("Cadastro temporario expirado. Solicite um novo codigo."));
        return onboardingService.exigirValidoParaAtualizacao(token, empresa.getId());
    }


    private ClienteEntity findClienteFromSession(HttpServletRequest request) {
        String slug = slugAtual(request);
        EmpresaEntity empresa = empresaRepository.findByAgendamentoSlug(slug)
                .orElseThrow(() -> new SessaoExpiradaException("Loja nao encontrada."));
        String session = CookieHelper.lerCookie(request, nomeCookie(slug))
                .orElseThrow(() -> new SessaoExpiradaException("Sessao nao encontrada. Faca login novamente."));

        MeuGendazAcessoEntity acesso = meuGendazAcessoRepository.findByEmpresaIdAndSessaoAtiva(empresa.getId(), session)
                .orElseThrow(() -> new SessaoExpiradaException("Sessao invalida. Faca login novamente."));
        clienteEmailBloqueadoService.validarAcesso(empresa.getId(), acesso.getEmail());
        ClienteEntity cliente;
        try {
            cliente = clienteRepository.findFirstByEmpresaIdAndEmailIgnoreCase(empresa.getId(), acesso.getEmail())
                    .orElseThrow(() -> new SessaoExpiradaException("Cadastro nao encontrado. Complete seu cadastro para continuar."));
        } catch (Exception e) {
            throw new SessaoExpiradaException("Sessao invalida. Faca login novamente.");
        }
        if (cliente.getEmpresa() == null || !empresa.getId().equals(cliente.getEmpresa().getId())) {
            throw new SessaoExpiradaException("Sessao invalida para esta loja.");
        }
        return cliente;
    }

    private Long getEmpresaId(ClienteEntity cliente) {
        if (cliente.getEmpresa() == null) {
            throw new BusinessException("Empresa nao encontrada para este cliente.");
        }
        return cliente.getEmpresa().getId();
    }

    @GetMapping("/empresa/{slug}")
    public ResponseEntity<?> empresaPorSlug(@PathVariable String slug) {
        Optional<EmpresaEntity> empresa = empresaRepository.findByAgendamentoSlug(slug);
        if (empresa.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensagem", "Empresa nao encontrada."));
        }
        EmpresaEntity e = empresa.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nome", e.getNomeFantasia());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/perfil")
    public ResponseEntity<?> perfil(HttpServletRequest request) {
        try {
            try {
                MeuGendazAcessoEntity acesso = findAcessoFromSession(request);
                if (acesso.getEmpresa() == null) {
                    throw new SessaoExpiradaException("Empresa nao encontrada para este acesso.");
                }
                EmpresaEntity empresa = empresaRepository.findById(acesso.getEmpresa().getId())
                        .orElseThrow(() -> new SessaoExpiradaException("Empresa nao encontrada para este acesso."));
                Optional<ClienteEntity> clienteOpt = clienteRepository.findFirstByEmpresaIdAndEmailIgnoreCase(
                        empresa.getId(),
                        acesso.getEmail()
                );
                ClienteEntity cliente = clienteOpt.orElse(null);
                boolean cadastroPendente = cliente == null;
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("cadastroPendente", cadastroPendente);
                result.put("id", cliente != null ? cliente.getId() : acesso.getId());
                result.put("nome", cliente != null ? cliente.getNome() : acesso.getNome());
                result.put("email", acesso.getEmail());
                result.put("telefone", cliente != null ? cliente.getTelefone() : null);
                result.put("empresaNome", empresa.getNomeFantasia());
                if (cadastroPendente) {
                    result.put("mensagem", "Complete seu cadastro para continuar.");
                    return ResponseEntity.ok(result);
                }
                result.put("cadastroPendente", false);
                return ResponseEntity.ok(result);
            } catch (SessaoExpiradaException semSessaoDefinitiva) {
                EmpresaEntity empresa = empresaAtual(request);
                MeuGendazOtpChallengeEntity onboarding = findOnboardingFromSession(request, empresa);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("cadastroPendente", true);
                result.put("id", onboarding.getId());
                result.put("nome", "");
                result.put("email", onboarding.getEmail());
                result.put("telefone", null);
                result.put("empresaNome", empresa.getNomeFantasia());
                result.put("mensagem", "Complete seu cadastro para continuar.");
                return ResponseEntity.ok(result);
            }
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }


    @GetMapping("/servicos")
    public ResponseEntity<?> servicos(HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            Long empresaId = getEmpresaId(cliente);
            return ResponseEntity.ok(servicoService.listarPorEmpresa(empresaId));
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    @GetMapping("/profissionais")
    public ResponseEntity<?> profissionais(@RequestParam(required = false) String data, HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            Long empresaId = getEmpresaId(cliente);
            var profissionais = profissionalService.listarPorEmpresa(empresaId);
            if (data != null && !data.isBlank()) {
                java.time.LocalDate dataParsed = java.time.LocalDate.parse(data);
                profissionais = profissionais.stream()
                        .filter(profissional -> profissional.diasTrabalho() != null
                                && profissional.diasTrabalho().contains(com.minhaempresa.gendaz.profissional.enums.DiaSemana.from(dataParsed.getDayOfWeek())))
                        .toList();
            }
            return ResponseEntity.ok(profissionais);
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }


    @GetMapping("/horarios-disponiveis")
    public ResponseEntity<?> horariosDisponiveis(
            @RequestParam Long servicoId,
            @RequestParam Long profissionalId,
            @RequestParam String data,
            HttpServletRequest request
    ) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            Long empresaId = getEmpresaId(cliente);
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
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Dados invalidos."));
        }
    }

    @GetMapping("/agendamentos/proximos")
    public ResponseEntity<?> agendamentosProximos(HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            Long empresaId = getEmpresaId(cliente);
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

    @GetMapping("/agendamentos/historico")
    public ResponseEntity<?> historico(
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int limite,
            HttpServletRequest request
    ) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            Long empresaId = getEmpresaId(cliente);
            List<AgendamentoResponse> agendamentos = agendamentoService.listarPorCliente(empresaId, cliente.getId());
            List<AgendamentoResponse> passados = agendamentos.stream()
                    .filter(a -> a.status() != null && isStatusHistorico(a.status().name()))
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

    @PostMapping("/agendamentos/criar")
    public ResponseEntity<?> criarAgendamento(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            Long empresaId = getEmpresaId(cliente);
            CriarAgendamentoRequest agendamentoRequest = new CriarAgendamentoRequest(
                    cliente.getId(),
                    Long.valueOf(body.get("servicoId").toString()),
                    body.get("profissionalId") != null ? Long.valueOf(body.get("profissionalId").toString()) : null,
                    empresaId,
                    java.time.LocalDate.parse(body.get("data").toString()),
                    java.time.LocalTime.parse(body.get("hora").toString()),
                    body.get("cupomCodigo") != null ? body.get("cupomCodigo").toString() : null,
                    body.get("observacoes") != null ? body.get("observacoes").toString() : null
            );
            AgendamentoResponse response = agendamentoService.criar(agendamentoRequest);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Dados invalidos: " + e.getMessage()));
        }
    }

    @PatchMapping("/agendamentos/{id}/reagendar")
    public ResponseEntity<?> reagendar(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            Long empresaId = getEmpresaId(cliente);
            RemarcarAgendamentoRequest req = new RemarcarAgendamentoRequest(
                    java.time.LocalDate.parse(body.get("novaData")),
                    java.time.LocalTime.parse(body.get("novaHora"))
            );
            AgendamentoResponse response = agendamentoService.remarcar(id, req, empresaId);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Dados invalidos: " + e.getMessage()));
        }
    }

    @DeleteMapping("/agendamentos/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            agendamentoService.cancelar(id, getEmpresaId(cliente));
            return ResponseEntity.ok(Map.of("mensagem", "Agendamento cancelado com sucesso."));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String slug = slugAtual(request);
        EmpresaEntity empresa = empresaRepository.findByAgendamentoSlug(slug)
                .orElseThrow(() -> new SessaoExpiradaException("Loja nao encontrada."));
        String cookieName = nomeCookie(slug);
        String session = CookieHelper.lerCookie(request, cookieName).orElse(null);
        if (session != null && !session.isBlank()) {
            meuGendazAcessoRepository.findBySessaoAtiva(session)
                    .filter(acesso -> acesso.getEmpresa() != null)
                    .filter(acesso -> empresa.getId().equals(acesso.getEmpresa().getId()))
                    .ifPresent(acesso -> usuarioSessionService.encerrarSessaoMeuGendaz(acesso.getId(), session));
        }
        cookieService.limparCookie(request, response, cookieName);
        cookieService.limparCookie(request, response, nomeOnboardingCookie(slug));
        return ResponseEntity.ok(Map.of("mensagem", "Logout realizado."));
    }


    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            List<AgendamentoResponse> todos = agendamentoService.listarPorCliente(getEmpresaId(cliente), cliente.getId());
            List<AgendamentoResponse> futuros = todos.stream()
                    .filter(a -> a.data() != null && !a.data().isBefore(java.time.LocalDate.now()))
                    .sorted(Comparator.comparing(AgendamentoResponse::data).thenComparing(AgendamentoResponse::horaInicio))
                    .toList();
            List<AgendamentoResponse> passados = todos.stream()
                    .filter(a -> a.data() != null && a.data().isBefore(java.time.LocalDate.now()))
                    .sorted(Comparator.comparing(AgendamentoResponse::data).reversed())
                    .limit(5)
                    .toList();
            List<AgendamentoResponse> concluidos = todos.stream()
                    .filter(a -> a.status() != null && isStatusConcluido(a.status().name()))
                    .toList();
            BigDecimal totalGasto = concluidos.stream()
                    .map(AgendamentoResponse::valor)
                    .filter(valor -> valor != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String servicoMaisEscolhido = concluidos.stream()
                    .map(AgendamentoResponse::servicoNome)
                    .filter(nome -> nome != null && !nome.isBlank())
                    .collect(java.util.stream.Collectors.groupingBy(nome -> nome, java.util.stream.Collectors.counting()))
                    .entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("-----");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("proximoAgendamento", futuros.isEmpty() ? null : futuros.get(0));
            result.put("ultimosAtendimentos", passados);
            result.put("totalAgendamentos", todos.size());
            result.put("agendamentosFuturos", futuros.size());
            result.put("totalGasto", totalGasto);
            result.put("servicoMaisEscolhido", servicoMaisEscolhido);
            result.put("promocoes", meuGendazPromocaoService.listarPromocoes(cliente));
            result.put("notificacoes", meuGendazPromocaoService.listarNotificacoesNaoLidas(cliente));
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    @PostMapping("/ia")
    public ResponseEntity<?> ia(@Valid @RequestBody InsightsRequest request, HttpServletRequest httpRequest) {
        try {
            ClienteEntity cliente = findClienteFromSession(httpRequest);
            Long empresaId = getEmpresaId(cliente);
            MeuGendazIAResponse resposta = insightsService.responderCliente(empresaId, request.pergunta(), request.historico());
            return ResponseEntity.ok(resposta);
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("mensagem", "Nao foi possivel processar a IA.", "erro", e.getMessage()));
        }
    }

    @PostMapping("/suporte")
    public ResponseEntity<?> criarSuporte(@Valid @RequestBody CriarSuporteRequest request, HttpServletRequest httpRequest) {
        try {
            MeuGendazAcessoEntity acesso = findAcessoFromSession(httpRequest);
            String tipo = request.tipoOcorrencia().trim();
            String motivo = request.motivo().trim();
            String mensagem = request.mensagem().trim();
            boolean bugSistema = "BUGS SISTEMA".equalsIgnoreCase(tipo);
            String assunto = "Meu Gendaz - " + tipo + " - " + motivo;
            String mensagemCompleta = "Origem: Meu Gendaz\n"
                    + "Tipo de ocorrência: " + tipo + "\n"
                    + "Motivo: " + motivo + "\n\n"
                    + mensagem;
            PrioridadeChamado prioridade = bugSistema ? PrioridadeChamado.ALTA : PrioridadeChamado.MEDIA;
            CriarChamadoRequest chamadoRequest = new CriarChamadoRequest(assunto, prioridade, mensagemCompleta);
            ChamadoResponse chamado = chamadoService.criarMeuGendaz(chamadoRequest, acesso);
            return ResponseEntity.ok(chamado);
        } catch (SessaoExpiradaException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        } catch (Exception e) {
            log.error("[meu-gendaz] erro ao abrir chamado. erroTipo={}", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(Map.of("mensagem", "Nao foi possivel abrir o chamado.", "erro", e.getMessage()));
        }
    }

    @GetMapping("/suporte")
    public ResponseEntity<?> listarSuporte(HttpServletRequest httpRequest) {
        try {
            ClienteEntity cliente = findClienteFromSession(httpRequest);
            MeuGendazAcessoEntity acesso = findAcessoFromSession(httpRequest);
            Long empresaId = getEmpresaId(cliente);
            return ResponseEntity.ok(chamadoService.listarPorEmpresaEMeuGendazAcesso(empresaId, acesso.getId()));
        } catch (SessaoExpiradaException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        } catch (Exception e) {
            log.error("[meu-gendaz] erro ao listar chamados. erroTipo={}", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(Map.of("mensagem", "Nao foi possivel carregar os chamados."));
        }
    }

    private boolean isStatusConcluido(String status) {
        if (status == null) {
            return false;
        }
        String normalizado = status.trim().toUpperCase();
        return "FINALIZADO".equals(normalizado)
                || "CONCLUIDO".equals(normalizado)
                || "CONCLUÍDO".equals(normalizado)
                || "CONCLUIDA".equals(normalizado)
                || "CONCLUÍDA".equals(normalizado);
    }

    private boolean isStatusHistorico(String status) {
        if (status == null) {
            return false;
        }
        String normalizado = status.trim().toUpperCase();
        return isStatusConcluido(normalizado)
                || "PENDENTE".equals(normalizado)
                || "CANCELADO".equals(normalizado);
    }

    private boolean isSafariMobile(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return false;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        boolean isIos = ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod");
        boolean isSafari = ua.contains("safari") && !ua.contains("crios") && !ua.contains("fxios") && !ua.contains("edgios") && !ua.contains("chrome");
        return isIos && isSafari;
    }

    @GetMapping("/promocoes")
    public ResponseEntity<?> promocoes(HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            return ResponseEntity.ok(meuGendazPromocaoService.listarPromocoes(cliente));
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    @GetMapping("/cupons")
    public ResponseEntity<?> cupons(HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            return ResponseEntity.ok(meuGendazPromocaoService.listarUsadas(cliente));
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    @GetMapping("/notificacoes")
    public ResponseEntity<?> notificacoes(HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            var notificacoes = meuGendazPromocaoService.listarNotificacoesNaoLidas(cliente);
            return ResponseEntity.ok(Map.of(
                    "totalNotificacoes", notificacoes.size(),
                    "notificacoes", notificacoes
            ));
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    @PatchMapping("/notificacoes/{promocaoId}/lido")
    public ResponseEntity<?> atualizarNotificacoes(@PathVariable Long promocaoId, HttpServletRequest request) {
        try {
            ClienteEntity cliente = findClienteFromSession(request);
            meuGendazPromocaoService.marcarNotificacaoComoLida(cliente, promocaoId);
            return ResponseEntity.ok(Map.of("mensagem", "Notificacao marcada como lida."));
        } catch (BusinessException e) {
            return ResponseEntity.status(401).body(Map.of("mensagem", e.getMessage()));
        }
    }

    @PatchMapping("/privacidade")
    public ResponseEntity<?> atualizarPrivacidade(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        findClienteFromSession(request);
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/perfil")
    public ResponseEntity<?> atualizarPerfil(@RequestBody Map<String, String> body, HttpServletRequest request, HttpServletResponse response) {
        try {
            EmpresaEntity empresa;
            String email;
            MeuGendazOtpChallengeEntity onboarding = null;
            MeuGendazAcessoEntity acesso = null;
            try {
                acesso = findAcessoFromSession(request);
                empresa = acesso.getEmpresa() == null ? null : empresaRepository.findById(acesso.getEmpresa().getId()).orElse(null);
                if (empresa == null) {
                    throw new BusinessException("Empresa nao encontrada para este acesso.");
                }
                email = acesso.getEmail() == null ? "" : acesso.getEmail().trim().toLowerCase();
            } catch (SessaoExpiradaException semSessaoDefinitiva) {
                empresa = empresaAtual(request);
                onboarding = findOnboardingFromSession(request, empresa);
                email = onboarding.getEmail() == null ? "" : onboarding.getEmail().trim().toLowerCase();
            }

            Long empresaId = empresa.getId();
            String nome = body.get("nome") == null ? "" : body.get("nome").trim();
            String telefone = sanitizacaoService.telefone(body.get("telefone"));

            Optional<ClienteEntity> clienteExistente = clienteRepository.findFirstByEmpresaIdAndEmailIgnoreCase(empresaId, email);
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
                if (clienteMesmoTelefone.isPresent() && (clienteExistente.isEmpty() || !clienteMesmoTelefone.get().getId().equals(clienteExistente.get().getId()))) {
                    erros.add("Ja existe um cliente com este telefone.");
                }
            }
            if (!email.isBlank()) {
                Optional<ClienteEntity> clienteMesmoEmail = clienteRepository.findFirstByEmpresaIdAndEmailIgnoreCase(empresaId, email);
                if (clienteMesmoEmail.isPresent() && (clienteExistente.isEmpty() || !clienteMesmoEmail.get().getId().equals(clienteExistente.get().getId()))) {
                    erros.add("Ja existe um cliente com este e-mail.");
                }
            }

            if (!erros.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("mensagem", String.join(" ", erros)));
            }

            ClienteEntity cliente;
            if (clienteExistente.isPresent()) {
                cliente = clienteExistente.get();
            } else {
                cliente = ClienteEntity.builder()
                        .empresa(empresa)
                        .email(email)
                        .build();
            }

            cliente.setNome(nome);
            cliente.setTelefone(telefone);
            clienteRepository.save(cliente);

            MeuGendazAcessoEntity acessoDefinitivo = acesso != null ? acesso : meuGendazAuthService.buscarOuCriarAcessoDefinitivo(empresa, email, nome);
            acessoDefinitivo.setNome(nome);
            meuGendazAcessoRepository.save(acessoDefinitivo);
            String sessaoDefinitiva = usuarioSessionService.criarSessaoMeuGendaz(acessoDefinitivo);

            String slug = slugAtual(request);
            cookieService.limparCookie(request, response, nomeOnboardingCookie(slug));
            cookieService.adicionarCookie(request, response, nomeCookie(slug), sessaoDefinitiva, (int) java.time.Duration.ofDays(90).getSeconds());
            onboardingService.invalidar(onboarding);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", cliente.getId());
            result.put("nome", cliente.getNome());
            result.put("email", cliente.getEmail());
            result.put("telefone", cliente.getTelefone());
            result.put("empresaId", empresa.getId());
            result.put("empresaNome", empresa.getNomeFantasia());
            result.put("cadastroPendente", false);
            result.put("mensagem", "Perfil atualizado com sucesso!");
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
        }
    }

}


