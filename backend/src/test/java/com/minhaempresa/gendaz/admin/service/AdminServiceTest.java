package com.minhaempresa.gendaz.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.minhaempresa.gendaz.admin.dto.AdminAssinaturaDtos.AdminAssinaturaOperacaoRequest;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.AdminLoginRequest;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.AdminAcaoEmpresaRequest;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.AdminAtualizarEmpresaRequest;
import com.minhaempresa.gendaz.admin.repository.AdminImpersonationSessionRepository;
import com.minhaempresa.gendaz.chamado.repository.ChamadoRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.auth.service.PasswordService;
import com.minhaempresa.gendaz.auth.service.UsuarioSessionService;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.security.SecurityMonitoringService;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdminServiceTest {
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final EmpresaRepository empresaRepository = mock(EmpresaRepository.class);
    private final PagamentoPlanoRepository pagamentoPlanoRepository = mock(PagamentoPlanoRepository.class);
    private final AssinaturaRepository assinaturaRepository = mock(AssinaturaRepository.class);
    private final AdminImpersonationSessionRepository impersonationRepository = mock(AdminImpersonationSessionRepository.class);
    private final ChamadoRepository chamadoRepository = mock(ChamadoRepository.class);
    private final AssinaturaService assinaturaService = mock(AssinaturaService.class);
    private final PlanoService planoService = mock(PlanoService.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final PasswordService passwordService = new PasswordService();
    private final UsuarioSessionService usuarioSessionService = mock(UsuarioSessionService.class);
    private final SecurityMonitoringService securityMonitoringService = mock(SecurityMonitoringService.class);
    private final PagamentoService pagamentoService = mock(PagamentoService.class);
    private final ProfissionalService profissionalService = mock(ProfissionalService.class);
    private final AdminSessionService adminSessionService = mock(AdminSessionService.class);
    private final SubscriptionAdminService subscriptionAdminService = mock(SubscriptionAdminService.class);
    private final AdminService adminService = new AdminService(
            usuarioRepository,
            empresaRepository,
            pagamentoPlanoRepository,
            assinaturaRepository,
            impersonationRepository,
            chamadoRepository,
            assinaturaService,
            planoService,
            auditService,
            passwordService,
            usuarioSessionService,
            securityMonitoringService,
            pagamentoService,
            profissionalService,
            adminSessionService,
            null
    );

    @Test
    void deveBloquearUsuarioComumNoLoginAdmin() {
        UsuarioEntity usuario = UsuarioEntity.builder()
                .id(1L)
                .nome("Dono")
                .email("dono@Gendaz.com")
                .senha(passwordService.hash("SenhaForte123!"))
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .build();
        when(usuarioRepository.findAllByEmailIgnoreCase("dono@gendaz.com")).thenReturn(java.util.List.of(usuario));

        assertThrows(BusinessException.class, () -> adminService.login(new AdminLoginRequest("dono@Gendaz.com", "SenhaForte123!"), "127.0.0.1", "test"));
        verify(auditService).registrar(eq("ADMIN_LOGIN_FAILED"), eq("SECURITY"), isNull(), isNull(), isNull(), anyString(), isNull(), eq("127.0.0.1"), eq("test"));
    }

    @Test
    void devePermitirSuperAdminNoLoginAdmin() {
        UsuarioEntity admin = UsuarioEntity.builder()
                .id(2L)
                .nome("Admin")
                .email("admin@Gendaz.com")
                .senha(passwordService.hash("SenhaForte123!"))
                .perfil(PerfilUsuario.SUPER_ADMIN)
                .status(StatusUsuario.ATIVO)
                .build();
        when(usuarioRepository.findAllByEmailIgnoreCase("admin@gendaz.com")).thenReturn(java.util.List.of(admin));
        when(adminSessionService.criarSessao(admin, "127.0.0.1", "test")).thenReturn("token-admin");

        var response = adminService.login(new AdminLoginRequest("admin@Gendaz.com", "SenhaForte123!"), "127.0.0.1", "test");

        assertNotNull(response.token());
        assertEquals("admin@Gendaz.com", response.admin().email());
    }

    @Test
    void deveExigirTokenValidoParaAcessoAdmin() {
        doThrow(new com.minhaempresa.gendaz.shared.SessaoExpiradaException(
                "Sessão admin inválida."
        )).when(adminSessionService)
                .validarSessao("token-invalido");

        assertThrows(com.minhaempresa.gendaz.shared.SessaoExpiradaException.class, () -> adminService.exigirAdmin("token-invalido"));
    }

    @Test
    void deveDesativarEmpresaEEncerrarSessoesAtivas() {
        UsuarioEntity admin = UsuarioEntity.builder().id(1L).perfil(PerfilUsuario.SUPER_ADMIN).build();
        EmpresaEntity empresa = EmpresaEntity.builder()
                .id(10L)
                .nomeFantasia("Empresa teste")
                .email("empresa@teste.com")
                .status(StatusEmpresa.ATIVA)
                .build();
        UsuarioEntity dono = UsuarioEntity.builder()
                .id(20L)
                .empresa(empresa)
                .perfil(PerfilUsuario.DONO)
                .sessaoAtiva("sessao-cliente")
                .build();

        when(adminSessionService.validarSessao("token-admin")).thenReturn(admin);
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(empresa)).thenReturn(empresa);
        when(usuarioRepository.findByEmpresaId(10L)).thenReturn(java.util.List.of(dono));
        when(usuarioRepository.findByEmpresaIdAndPerfil(10L, PerfilUsuario.DONO)).thenReturn(java.util.List.of(dono));
        when(assinaturaService.buscarAtualPorEmpresa(10L)).thenReturn(Optional.empty());
        when(pagamentoPlanoRepository.findByEmpresaIdOrderByDataCriacaoDesc(10L)).thenReturn(java.util.List.of());

        var response = adminService.desativarEmpresa(
                "token-admin", 10L, new AdminAcaoEmpresaRequest("Bloqueio administrativo"), "127.0.0.1", "test"
        );

        assertEquals(StatusEmpresa.BLOQUEADA, empresa.getStatus());
        assertEquals("BLOQUEADA", response.statusEmpresa());
        verify(usuarioSessionService).encerrarSessao("sessao-cliente");
    }

    @Test
    void adminEditaNomeFantasiaETelefoneDaEmpresaSemMudarEmailDoDono() {
        UsuarioEntity admin = UsuarioEntity.builder().id(1L).nome("Admin").perfil(PerfilUsuario.SUPER_ADMIN).build();
        EmpresaEntity empresa = EmpresaEntity.builder()
                .id(10L)
                .nomeFantasia("Empresa teste")
                .telefone("5565993360300")
                .email("dono@empresa.com")
                .status(StatusEmpresa.ATIVA)
                .build();
        UsuarioEntity dono = UsuarioEntity.builder()
                .id(20L)
                .nome("Dono teste")
                .email("dono@empresa.com")
                .perfil(PerfilUsuario.DONO)
                .empresa(empresa)
                .build();

        ReflectionTestUtils.setField(adminService, "phoneNumberService", new PhoneNumberService());
        when(adminSessionService.validarSessao("token-admin")).thenReturn(admin);
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(empresa)).thenReturn(empresa);
        when(usuarioRepository.findByEmpresaIdAndPerfil(10L, PerfilUsuario.DONO)).thenReturn(java.util.List.of(dono));
        when(assinaturaService.buscarAtualPorEmpresa(10L)).thenReturn(Optional.empty());
        when(pagamentoPlanoRepository.findByEmpresaIdOrderByDataCriacaoDesc(10L)).thenReturn(java.util.List.of());

        var response = adminService.atualizarEmpresa(
                "token-admin", 10L,
                new AdminAtualizarEmpresaRequest("Novo Nome", "(65) 99336-0300", "dono@empresa.com", null, null,
                        "Correcao cadastral", null),
                "127.0.0.1", "test"
        );

        assertEquals("Novo Nome", empresa.getNomeFantasia());
        assertEquals("5565993360300", empresa.getTelefone());
        assertEquals("dono@empresa.com", empresa.getEmail());
        assertEquals("dono@empresa.com", dono.getEmail());
        verify(usuarioRepository, never()).save(dono);
    }

    @Test
    void adminEditaEmailDaEmpresaEAtualizaEmailDoDono() {
        UsuarioEntity admin = UsuarioEntity.builder().id(1L).nome("Admin").perfil(PerfilUsuario.SUPER_ADMIN).build();
        EmpresaEntity empresa = EmpresaEntity.builder()
                .id(10L)
                .nomeFantasia("Empresa teste")
                .telefone("5565993360300")
                .email("empresa@teste.com")
                .status(StatusEmpresa.ATIVA)
                .build();
        UsuarioEntity dono = UsuarioEntity.builder()
                .id(20L)
                .nome("Dono teste")
                .email("dono@teste.com")
                .perfil(PerfilUsuario.DONO)
                .empresa(empresa)
                .build();

        ReflectionTestUtils.setField(adminService, "phoneNumberService", new PhoneNumberService());
        when(adminSessionService.validarSessao("token-admin")).thenReturn(admin);
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(empresa)).thenReturn(empresa);
        when(usuarioRepository.findByEmpresaIdAndPerfil(10L, PerfilUsuario.DONO)).thenReturn(java.util.List.of(dono));
        when(usuarioRepository.findByEmailIgnoreCase("novo@teste.com")).thenReturn(Optional.empty());
        when(assinaturaService.buscarAtualPorEmpresa(10L)).thenReturn(Optional.empty());
        when(pagamentoPlanoRepository.findByEmpresaIdOrderByDataCriacaoDesc(10L)).thenReturn(java.util.List.of());

        var response = adminService.atualizarEmpresa(
                "token-admin", 10L,
                new AdminAtualizarEmpresaRequest("Empresa teste", "(65) 99336-0300", "novo@teste.com", null, null,
                        "Dono informou novo email", null),
                "127.0.0.1", "test"
        );

        assertEquals("novo@teste.com", empresa.getEmail());
        assertEquals("novo@teste.com", dono.getEmail());
        verify(usuarioRepository).save(dono);
    }

    @Test
    void adminNaoUsaEmailJaExistenteEmOutroUsuario() {
        UsuarioEntity admin = UsuarioEntity.builder().id(1L).nome("Admin").perfil(PerfilUsuario.SUPER_ADMIN).build();
        EmpresaEntity empresa = EmpresaEntity.builder()
                .id(10L)
                .nomeFantasia("Empresa teste")
                .telefone("5565993360300")
                .email("empresa@teste.com")
                .status(StatusEmpresa.ATIVA)
                .build();
        UsuarioEntity dono = UsuarioEntity.builder()
                .id(20L)
                .nome("Dono teste")
                .email("dono@teste.com")
                .perfil(PerfilUsuario.DONO)
                .empresa(empresa)
                .build();
        UsuarioEntity outroUsuario = UsuarioEntity.builder()
                .id(99L)
                .nome("Outro usuario")
                .email("novo@teste.com")
                .perfil(PerfilUsuario.DONO)
                .build();

        ReflectionTestUtils.setField(adminService, "phoneNumberService", new PhoneNumberService());
        when(adminSessionService.validarSessao("token-admin")).thenReturn(admin);
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.findByEmpresaIdAndPerfil(10L, PerfilUsuario.DONO)).thenReturn(java.util.List.of(dono));
        when(usuarioRepository.findByEmailIgnoreCase("novo@teste.com")).thenReturn(Optional.of(outroUsuario));

        BusinessException ex = assertThrows(BusinessException.class, () -> adminService.atualizarEmpresa(
                "token-admin", 10L,
                new AdminAtualizarEmpresaRequest("Empresa teste", "(65) 99336-0300", "novo@teste.com", null, null,
                        "Tentativa com email em uso", null),
                "127.0.0.1", "test"
        ));

        assertEquals("O e-mail informado ja esta em uso por outra conta.", ex.getMessage());
        verify(usuarioRepository, never()).save(dono);
    }

    @Test
    void adminEditaPlanoUsandoOperacoesDeAssinatura() {
        UsuarioEntity admin = UsuarioEntity.builder().id(1L).nome("Admin").perfil(PerfilUsuario.SUPER_ADMIN).build();
        EmpresaEntity empresa = EmpresaEntity.builder()
                .id(10L)
                .nomeFantasia("Empresa teste")
                .telefone("5565993360300")
                .email("dono@empresa.com")
                .status(StatusEmpresa.ATIVA)
                .build();
        UsuarioEntity dono = UsuarioEntity.builder()
                .id(20L)
                .nome("Dono teste")
                .email("dono@empresa.com")
                .perfil(PerfilUsuario.DONO)
                .empresa(empresa)
                .build();

        ReflectionTestUtils.setField(adminService, "phoneNumberService", new PhoneNumberService());
        ReflectionTestUtils.setField(adminService, "subscriptionAdminService", subscriptionAdminService);
        when(adminSessionService.validarSessao("token-admin")).thenReturn(admin);
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(empresa)).thenReturn(empresa);
        when(usuarioRepository.findByEmpresaIdAndPerfil(10L, PerfilUsuario.DONO)).thenReturn(java.util.List.of(dono));
        when(assinaturaService.buscarAtualPorEmpresa(10L)).thenReturn(Optional.empty());
        when(pagamentoPlanoRepository.findByEmpresaIdOrderByDataCriacaoDesc(10L)).thenReturn(java.util.List.of());

        var assinaturas = java.util.List.of(
                new AdminAssinaturaOperacaoRequest("REMOVER", 11L, null, null, null, null, null),
                new AdminAssinaturaOperacaoRequest("CRIAR", null, 3L, 30, null, null, null)
        );

        adminService.atualizarEmpresa(
                "token-admin", 10L,
                new AdminAtualizarEmpresaRequest("Novo Nome", "(65) 99336-0300", "dono@empresa.com", null, null,
                        "Substituicao de plano", assinaturas),
                "127.0.0.1", "test"
        );

        verify(subscriptionAdminService).removerAssinatura(10L, 11L);
        verify(subscriptionAdminService).criarAssinatura(eq(10L), any());
    }

    @Test
    void dashboardCalculaContasPeloEstadoVigenteENaoConfundeVencidaComCancelada() {
        UsuarioEntity admin = UsuarioEntity.builder().id(1L).nome("Admin").perfil(PerfilUsuario.SUPER_ADMIN).build();
        LocalDate hoje = LocalDate.now();
        PlanoEntity basico = PlanoEntity.builder().id(1L).nome("BASICO").build();
        PlanoEntity pro = PlanoEntity.builder().id(2L).nome("PRO").build();

        EmpresaEntity emTeste = empresa(10L, StatusEmpresa.ATIVA);
        EmpresaEntity ativa = empresa(11L, StatusEmpresa.ATIVA);
        EmpresaEntity vencida = empresa(12L, StatusEmpresa.ATIVA);
        EmpresaEntity bloqueada = empresa(13L, StatusEmpresa.BLOQUEADA);
        EmpresaEntity encerrada = empresa(14L, StatusEmpresa.ENCERRADA);
        EmpresaEntity cancelada = empresa(15L, StatusEmpresa.ATIVA);

        List<AssinaturaEntity> assinaturas = List.of(
                assinatura(emTeste, basico, StatusAssinatura.TESTE, hoje.minusDays(1), hoje.plusDays(1)),
                assinatura(ativa, pro, StatusAssinatura.ATIVA, hoje.minusDays(2), hoje.plusDays(2)),
                assinatura(vencida, basico, StatusAssinatura.EXPIRADA, hoje.minusDays(20), hoje.minusDays(1)),
                assinatura(bloqueada, pro, StatusAssinatura.ATIVA, hoje.minusDays(5), hoje.plusDays(5)),
                assinatura(cancelada, basico, StatusAssinatura.CANCELADA, hoje.minusDays(20), hoje.minusDays(1))
        );

        when(adminSessionService.validarSessao("token-admin")).thenReturn(admin);
        when(empresaRepository.findAll()).thenReturn(List.of(emTeste, ativa, vencida, bloqueada, encerrada, cancelada));
        when(assinaturaRepository.findAllComPlano()).thenReturn(assinaturas);
        when(pagamentoPlanoRepository.findAll()).thenReturn(List.of());

        var resposta = adminService.dashboard("token-admin", YearMonth.now().toString());

        assertEquals(1, resposta.contasAtivas());
        assertEquals(2, resposta.contasCanceladas());
        assertEquals(1, resposta.contasTeste());
        assertEquals(1, resposta.empresasVencidas());
    }

    @Test
    void dashboardCalculaReceitaPorDiaEFaturamentoDoMes() {
        UsuarioEntity admin = UsuarioEntity.builder().id(1L).nome("Admin").perfil(PerfilUsuario.SUPER_ADMIN).build();
        LocalDate hoje = LocalDate.now();
        PlanoEntity basico = PlanoEntity.builder().id(1L).nome("BASICO").build();
        EmpresaEntity ativa = empresa(11L, StatusEmpresa.ATIVA);
        PlanoEntity pro = PlanoEntity.builder().id(2L).nome("PRO").build();

        LocalDate inicioMes = YearMonth.now().atDay(1);
        LocalDate mesAnterior = YearMonth.now().minusMonths(1).atDay(1);

        when(adminSessionService.validarSessao("token-admin")).thenReturn(admin);
        when(empresaRepository.findAll()).thenReturn(List.of(ativa));
        when(assinaturaRepository.findAllComPlano()).thenReturn(List.of(
                assinatura(ativa, pro, StatusAssinatura.ATIVA, hoje.minusDays(2), hoje.plusDays(2))
        ));
        when(pagamentoPlanoRepository.findAll()).thenReturn(List.of(
                pagamento(100, StatusPagamento.PAGO, hoje.atTime(12, 0)),
                pagamento(200, StatusPagamento.PAYMENT_APPROVED, inicioMes.atTime(9, 0)),
                pagamento(300, StatusPagamento.PENDENTE, null),
                pagamento(400, StatusPagamento.PAYMENT_REJECTED, hoje.atTime(10, 0)),
                pagamento(500, StatusPagamento.PAGO, mesAnterior.atTime(15, 0))
        ));

        var resposta = adminService.dashboard("token-admin", YearMonth.now().toString());

        assertEquals(0, new BigDecimal("800").compareTo(resposta.totalGanho()));
        assertEquals(0, new BigDecimal("300").compareTo(resposta.faturamentoMes()));
        assertEquals(1, resposta.pagamentosPendentes());
        assertEquals(inicioMes.lengthOfMonth(), resposta.receitaDia().size());
        assertEquals(0, new BigDecimal("300").compareTo(resposta.receitaDia().stream()
                .map(com.minhaempresa.gendaz.admin.dto.AdminDtos.ReceitaDiaResponse::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(1, resposta.distribuicaoPlanos().stream().filter(p -> p.plano().equals("PRO")).findFirst().orElseThrow().total());
        assertEquals(0, resposta.distribuicaoPlanos().stream().filter(p -> p.plano().equals("ENTERPRISE")).findFirst().orElseThrow().total());
    }

    @Test
    void dashboardRejeitaMesEmFormatoInvalido() {
        UsuarioEntity admin = UsuarioEntity.builder().id(1L).nome("Admin").perfil(PerfilUsuario.SUPER_ADMIN).build();
        when(adminSessionService.validarSessao("token-admin")).thenReturn(admin);

        BusinessException excecao = assertThrows(BusinessException.class, () -> adminService.dashboard("token-admin", "2026-13"));
        assertEquals("Mes invalido. Use o formato yyyy-MM.", excecao.getMessage());

        assertThrows(BusinessException.class, () -> adminService.dashboard("token-admin", "nao-e-mes"));
    }

    private EmpresaEntity empresa(Long id, StatusEmpresa status) {
        return EmpresaEntity.builder()
                .id(id)
                .nomeFantasia("Empresa " + id)
                .email("empresa" + id + "@teste.com")
                .status(status)
                .dataCriacao(LocalDateTime.now().minusMonths(2))
                .build();
    }

    private AssinaturaEntity assinatura(EmpresaEntity empresa, PlanoEntity plano, StatusAssinatura status,
                                        LocalDate dataInicio, LocalDate dataFim) {
        return AssinaturaEntity.builder()
                .empresa(empresa)
                .plano(plano)
                .status(status)
                .dataInicio(dataInicio)
                .dataFim(dataFim)
                .build();
    }

    private PagamentoPlanoEntity pagamento(double valor, StatusPagamento status, LocalDateTime dataPagamento) {
        return PagamentoPlanoEntity.builder()
                .id((long) valor)
                .valor(BigDecimal.valueOf(valor))
                .status(status)
                .dataPagamento(dataPagamento)
                .build();
    }
}

