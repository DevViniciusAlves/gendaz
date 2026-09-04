package com.minhaempresa.gendaz.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.CriarContaRequest;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.LoginResponse;
import com.minhaempresa.gendaz.auth.idempotencia.entity.CadastroIdempotenciaEntity;
import com.minhaempresa.gendaz.auth.idempotencia.enums.CadastroIdempotenciaStatus;
import com.minhaempresa.gendaz.auth.idempotencia.exception.IdempotenciaException;
import com.minhaempresa.gendaz.auth.idempotencia.service.CadastroIdempotenciaService;
import com.minhaempresa.gendaz.auth.idempotencia.service.ReservaResultado;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.usuario.dto.UsuarioDtos.UsuarioResponse;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceIdempotenciaTest {

    private static final String TELEFONE = "+5511999999999";
    private static final String KEY_HASH = "hash-a";
    private static final String FINGERPRINT = "fp-a";

    @Mock PhoneNumberService phoneNumberService;
    @Mock CadastroIdempotenciaService cadastroIdempotenciaService;
    @Mock EmpresaRepository empresaRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock PlanoService planoService;
    @Mock AssinaturaService assinaturaService;
    @Mock PagamentoService pagamentoService;
    @Mock ProfissionalService profissionalService;
    @Mock PasswordService passwordService;
    @Mock ResendEmailService resendEmailService;
    @Mock UsuarioSessionService usuarioSessionService;
    @Mock AdminAuditService auditService;

    @InjectMocks AuthService authService;

    @BeforeEach
    void setup() {
        lenient().when(phoneNumberService.normalizarObrigatorio(any())).thenReturn(TELEFONE);
        lenient().when(passwordService.hash(anyString())).thenReturn("hash-senha");
        lenient().when(empresaRepository.findByTelefone(any())).thenReturn(Optional.empty());
        lenient().when(empresaRepository.findByNomeFantasiaNormalizado(any())).thenReturn(Optional.empty());
        lenient().when(usuarioRepository.findUsuariosPainelByEmailIgnoreCase(any(), any())).thenReturn(List.of());
        lenient().when(empresaRepository.save(any(EmpresaEntity.class))).thenReturn(empresa());
        lenient().when(usuarioRepository.save(any(UsuarioEntity.class))).thenReturn(usuario());
        lenient().when(planoService.buscarPorNomePermitido(anyString())).thenAnswer(invocacao -> {
            String nome = invocacao.getArgument(0);
            return "pro".equalsIgnoreCase(nome) ? planoPro() : planoBasico();
        });
        lenient().when(assinaturaService.criarTesteGratis(any(), any())).thenAnswer(invocacao -> assinaturaComPlano(invocacao.getArgument(1)));
        lenient().when(assinaturaService.criarPendentePagamento(any(), any())).thenAnswer(invocacao -> assinaturaComPlano(invocacao.getArgument(1)));
        lenient().when(resendEmailService.enviarBoasVindas(any(), any(), any())).thenReturn(true);
        lenient().when(usuarioSessionService.renovarSessao(any())).thenReturn("sessao-nova");
        lenient().when(cadastroIdempotenciaService.calcularKeyHash(any())).thenReturn(KEY_HASH);
        lenient().when(cadastroIdempotenciaService.calcularFingerprint(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(FINGERPRINT);
        lenient().when(pagamentoService.iniciarPagamentoPlanoOnboarding(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(pagamentoResponse());
    }

    private CriarContaRequest request(String plano) {
        return new CriarContaRequest(
                "Clinica Beta", "Ana Maria", "ana@gendaz.com.br", TELEFONE,
                "Senha123!", "Senha123!", plano, true);
    }

    private EmpresaEntity empresa() {
        return EmpresaEntity.builder()
                .id(1L)
                .nomeFantasia("Clinica Beta")
                .telefone(TELEFONE)
                .email("ana@gendaz.com.br")
                .status(StatusEmpresa.ATIVA)
                .build();
    }

    private UsuarioEntity usuario() {
        return UsuarioEntity.builder()
                .id(3L)
                .nome("Ana Maria")
                .email("ana@gendaz.com.br")
                .senha("hash-senha")
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .empresa(empresa())
                .aceitouTermos(true)
                .dataAceiteTermos(LocalDateTime.now())
                .dataAceitePolitica(LocalDateTime.now())
                .versaoTermos("2026-06-22")
                .versaoPolitica("2026-06-22")
                .build();
    }

    private AssinaturaEntity assinatura() {
        return assinaturaComPlano(planoBasico());
    }

    private AssinaturaEntity assinaturaComPlano(PlanoEntity plano) {
        return AssinaturaEntity.builder()
                .id(5L)
                .empresa(empresa())
                .plano(plano)
                .status(StatusAssinatura.TESTE)
                .dataInicio(java.time.LocalDate.now())
                .dataFim(java.time.LocalDate.now().plusDays(7))
                .dataInicioTeste(java.time.LocalDate.now())
                .dataFimTeste(java.time.LocalDate.now().plusDays(7))
                .build();
    }

    private PlanoEntity planoBasico() {
        return PlanoEntity.builder().id(2L).nome("BASICO").build();
    }

    private PlanoEntity planoPro() {
        return PlanoEntity.builder().id(3L).nome("PRO").build();
    }

    private AssinaturaResponse assinaturaResponse() {
        return new AssinaturaResponse(5L, 1L, "Clinica Beta", 2L, "BASICO",
                StatusAssinatura.TESTE, java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(7),
                java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(7), 7L);
    }

    private PagamentoPlanoResponse pagamentoResponse() {
        return new PagamentoPlanoResponse(6L, 1L, "Clinica Beta", 3L, "PRO",
                new BigDecimal("199.90"), MetodoPagamento.CREDIT_CARD, StatusPagamento.PENDENTE,
                null, null, null, null, null, null, null, null, "https://checkout.test", null,
                null, null, LocalDateTime.now(), LocalDateTime.now().plusMinutes(30), null, null);
    }

    private CadastroIdempotenciaEntity registro(CadastroIdempotenciaStatus status) {
        return CadastroIdempotenciaEntity.builder()
                .id(10L)
                .keyHash(KEY_HASH)
                .requestFingerprint(FINGERPRINT)
                .status(status)
                .criadoEm(LocalDateTime.now().minusMinutes(1))
                .atualizadoEm(LocalDateTime.now().minusMinutes(1))
                .expiraEm(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    private UsuarioResponse usuarioResponse() {
        LocalDateTime agora = LocalDateTime.now();
        return new UsuarioResponse(3L, "Ana Maria", "ana@gendaz.com.br", PerfilUsuario.DONO,
                StatusUsuario.ATIVO, 1L, "Clinica Beta", true, true, agora, "2026-06-22",
                agora, "2026-06-22", agora, agora);
    }

    @Test
    void primeiraRequestComChaveExecutaCadastroUmaVez() {
        when(cadastroIdempotenciaService.reservarChave(KEY_HASH, FINGERPRINT, "req-1"))
                .thenReturn(ReservaResultado.reservado(registro(CadastroIdempotenciaStatus.PROCESSING)));

        LoginResponse resposta = authService.criarConta(request("basico"), "key-a", "req-1");

        assertEquals("ACTIVE", resposta.statusConta());
        assertEquals("sessao-nova", resposta.sessionToken());
        verify(empresaRepository, times(1)).save(any(EmpresaEntity.class));
        verify(assinaturaService, times(1)).criarTesteGratis(any(), any());
        verify(resendEmailService).enviarBoasVindas(eq("ana@gendaz.com.br"), anyString(), anyString());
        verify(cadastroIdempotenciaService).marcarCompletado(KEY_HASH, 1L, 3L, 5L, null, "ACTIVE");
        verify(cadastroIdempotenciaService, never()).marcarFalha(any());
    }

    @Test
    void replayMesmaChaveNaoExecutaCriacaoDeNovo() {
        when(cadastroIdempotenciaService.reservarChave(KEY_HASH, FINGERPRINT, "req-2"))
                .thenReturn(ReservaResultado.completado(registro(CadastroIdempotenciaStatus.COMPLETED)));
        when(cadastroIdempotenciaService.recuperarResultado(any()))
                .thenReturn(new LoginResponse("Conta criada com sucesso. Seu teste gratis de 7 dias comecou.",
                        usuarioResponse(), assinaturaResponse(), null, "ACTIVE", "sessao-replay", null));

        LoginResponse resposta = authService.criarConta(request("basico"), "key-a", "req-2");

        assertEquals("ACTIVE", resposta.statusConta());
        assertEquals("sessao-replay", resposta.sessionToken());
        verify(empresaRepository, never()).save(any(EmpresaEntity.class));
        verify(assinaturaService, never()).criarTesteGratis(any(), any());
        verify(resendEmailService, never()).enviarBoasVindas(anyString(), anyString(), anyString());
        verify(cadastroIdempotenciaService).recuperarResultado(any());
        verify(cadastroIdempotenciaService, never()).marcarCompletado(any(), any(), any(), any(), any(), any());
        verify(cadastroIdempotenciaService, never()).marcarFalha(any());
    }

    @Test
    void chaveEmProcessamentoLancaInProgressSemExecutarCadastro() {
        when(cadastroIdempotenciaService.reservarChave(KEY_HASH, FINGERPRINT, "req-2"))
                .thenReturn(ReservaResultado.emProcessamento(registro(CadastroIdempotenciaStatus.PROCESSING)));

        IdempotenciaException ex = assertThrows(IdempotenciaException.class,
                () -> authService.criarConta(request("basico"), "key-a", "req-2"));

        assertEquals("IDEMPOTENCY_IN_PROGRESS", ex.getCodigo());
        verify(empresaRepository, never()).save(any(EmpresaEntity.class));
    }

    @Test
    void mesmaChaveFingerprintDiferenteLancaKeyReused() {
        when(cadastroIdempotenciaService.reservarChave(KEY_HASH, FINGERPRINT, "req-2"))
                .thenThrow(new IdempotenciaException("IDEMPOTENCY_KEY_REUSED", "chave reutilizada com dados diferentes"));

        IdempotenciaException ex = assertThrows(IdempotenciaException.class,
                () -> authService.criarConta(request("basico"), "key-a", "req-2"));

        assertEquals("IDEMPOTENCY_KEY_REUSED", ex.getCodigo());
        verify(empresaRepository, never()).save(any(EmpresaEntity.class));
    }

    @Test
    void conflitoDeTelefoneContinuaRetornandoConflictECancelaReserva() {
        when(cadastroIdempotenciaService.reservarChave(KEY_HASH, FINGERPRINT, "req-1"))
                .thenReturn(ReservaResultado.reservado(registro(CadastroIdempotenciaStatus.PROCESSING)));
        when(empresaRepository.findByTelefone(TELEFONE))
                .thenReturn(Optional.of(EmpresaEntity.builder().id(99L).nomeFantasia("Outra").build()));

        assertThrows(ConflictException.class, () -> authService.criarConta(request("basico"), "key-a", "req-1"));

        verify(cadastroIdempotenciaService).marcarFalha(KEY_HASH);
        verify(empresaRepository, never()).save(any(EmpresaEntity.class));
    }

    @Test
    void falhaNaCriacaoMarcaFalhaEEstouraAExcecao() {
        when(cadastroIdempotenciaService.reservarChave(KEY_HASH, FINGERPRINT, "req-1"))
                .thenReturn(ReservaResultado.reservado(registro(CadastroIdempotenciaStatus.PROCESSING)));
        when(empresaRepository.save(any(EmpresaEntity.class))).thenThrow(new RuntimeException("falha simulada"));

        assertThrows(RuntimeException.class, () -> authService.criarConta(request("basico"), "key-a", "req-1"));

        verify(cadastroIdempotenciaService).marcarFalha(KEY_HASH);
    }

    @Test
    void proComMesmaChaveNaoDuplicaCriacaoNemEmail() {
        when(cadastroIdempotenciaService.reservarChave(KEY_HASH, FINGERPRINT, "req-1"))
                .thenReturn(ReservaResultado.reservado(registro(CadastroIdempotenciaStatus.PROCESSING)));
        when(cadastroIdempotenciaService.reservarChave(KEY_HASH, FINGERPRINT, "req-2"))
                .thenReturn(ReservaResultado.completado(registro(CadastroIdempotenciaStatus.COMPLETED)));
        when(cadastroIdempotenciaService.recuperarResultado(any()))
                .thenReturn(new LoginResponse("Conta criada com sucesso. Seu teste gratis de 7 dias comecou.",
                        usuarioResponse(), assinaturaResponse(), null,
                        "ACTIVE", "sessao-nova", null));

        LoginResponse primeira = authService.criarConta(request("pro"), "key-a", "req-1");
        LoginResponse replay = authService.criarConta(request("pro"), "key-a", "req-2");

        assertEquals("ACTIVE", primeira.statusConta());
        assertNotNull(primeira.sessionToken());
        assertEquals("ACTIVE", replay.statusConta());

        verify(pagamentoService, never()).iniciarPagamentoPlanoOnboarding(any(), any(), any(), any(), any(), any(), any());
        verify(assinaturaService, never()).criarPendentePagamento(any(), any());
        verify(assinaturaService, times(1)).criarTesteGratis(any(), any());
        verify(resendEmailService, times(1)).enviarBoasVindas(anyString(), anyString(), anyString());
        verify(cadastroIdempotenciaService, times(1)).marcarCompletado(KEY_HASH, 1L, 3L, 5L, null, "ACTIVE");
    }

    @Test
    void semChaveDeIdempotenciaCadastroSegueSemReservarChave() {
        LoginResponse resposta = authService.criarConta(request("basico"), null, null);

        assertEquals("ACTIVE", resposta.statusConta());
        verify(cadastroIdempotenciaService, never()).reservarChave(any(), any(), any());
        verify(cadastroIdempotenciaService, never()).marcarCompletado(any(), any(), any(), any(), any(), any());
        verify(cadastroIdempotenciaService, never()).marcarFalha(any());
    }
}
