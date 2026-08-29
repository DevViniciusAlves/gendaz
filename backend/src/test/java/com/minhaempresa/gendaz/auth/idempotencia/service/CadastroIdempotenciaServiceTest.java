package com.minhaempresa.gendaz.auth.idempotencia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.auth.idempotencia.entity.CadastroIdempotenciaEntity;
import com.minhaempresa.gendaz.auth.idempotencia.enums.CadastroIdempotenciaStatus;
import com.minhaempresa.gendaz.auth.idempotencia.exception.IdempotenciaException;
import com.minhaempresa.gendaz.auth.idempotencia.repository.CadastroIdempotenciaRepository;
import com.minhaempresa.gendaz.auth.idempotencia.service.ReservaResultado.TipoReserva;
import com.minhaempresa.gendaz.auth.service.UsuarioSessionService;
import com.minhaempresa.gendaz.pagamento.mapper.PagamentoMapper;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class CadastroIdempotenciaServiceTest {

    @Mock CadastroIdempotenciaRepository repository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock AssinaturaRepository assinaturaRepository;
    @Mock PagamentoPlanoRepository pagamentoPlanoRepository;
    @Mock AssinaturaService assinaturaService;
    @Mock PagamentoMapper pagamentoMapper;
    @Mock UsuarioSessionService usuarioSessionService;
    @Mock PlatformTransactionManager txManager;

    CadastroIdempotenciaService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        TransactionStatus status = org.mockito.Mockito.mock(TransactionStatus.class);
        when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        service = new CadastroIdempotenciaService(repository, usuarioRepository, assinaturaRepository,
                pagamentoPlanoRepository, assinaturaService, pagamentoMapper, usuarioSessionService, txManager);
    }

    private CadastroIdempotenciaEntity registro(CadastroIdempotenciaStatus status) {
        return CadastroIdempotenciaEntity.builder()
                .id(10L)
                .keyHash("hash-a")
                .requestFingerprint("fp-a")
                .status(status)
                .criadoEm(LocalDateTime.now().minusMinutes(1))
                .atualizadoEm(LocalDateTime.now().minusMinutes(1))
                .expiraEm(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    @Test
    void chaveNovaReservaComoProcessamento() {
        when(repository.findByKeyHashForUpdate("hash-a")).thenReturn(Optional.empty());

        ReservaResultado resultado = service.reservarChave("hash-a", "fp-a", "req-1");

        assertEquals(TipoReserva.RESERVADO, resultado.tipo());
        assertEquals(CadastroIdempotenciaStatus.PROCESSING, resultado.registro().getStatus());
        verify(repository, times(1)).saveAndFlush(any(CadastroIdempotenciaEntity.class));
    }

    @Test
    void chaveCompletadaDevolveReplaySemGravarNovoRegistro() {
        when(repository.findByKeyHashForUpdate("hash-a")).thenReturn(Optional.of(registro(CadastroIdempotenciaStatus.COMPLETED)));

        ReservaResultado resultado = service.reservarChave("hash-a", "fp-a", "req-2");

        assertEquals(TipoReserva.COMPLETADO, resultado.tipo());
        verify(repository, never()).saveAndFlush(any(CadastroIdempotenciaEntity.class));
    }

    @Test
    void chaveProcessandoNaoExecutaDeNovo() {
        when(repository.findByKeyHashForUpdate("hash-a")).thenReturn(Optional.of(registro(CadastroIdempotenciaStatus.PROCESSING)));

        ReservaResultado resultado = service.reservarChave("hash-a", "fp-a", "req-2");

        assertEquals(TipoReserva.EM_PROCESSAMENTO, resultado.tipo());
        verify(repository, never()).saveAndFlush(any(CadastroIdempotenciaEntity.class));
    }

    @Test
    void chaveFalhaReclamadaComoNovoProcessamento() {
        CadastroIdempotenciaEntity falha = registro(CadastroIdempotenciaStatus.FAILED);
        when(repository.findByKeyHashForUpdate("hash-a")).thenReturn(Optional.of(falha));

        ReservaResultado resultado = service.reservarChave("hash-a", "fp-a", "req-2");

        assertEquals(TipoReserva.RESERVADO, resultado.tipo());
        assertEquals(CadastroIdempotenciaStatus.PROCESSING, falha.getStatus());
        verify(repository).save(falha);
        verify(repository, never()).saveAndFlush(any(CadastroIdempotenciaEntity.class));
    }

    @Test
    void corridaConcorrenteApenasUmaReserva() {
        when(repository.findByKeyHashForUpdate("hash-a"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(registro(CadastroIdempotenciaStatus.PROCESSING)));
        when(repository.saveAndFlush(any(CadastroIdempotenciaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_cadastro_idempotencia_key_hash", new RuntimeException()));

        ReservaResultado resultado = service.reservarChave("hash-a", "fp-a", "req-1");

        assertEquals(TipoReserva.EM_PROCESSAMENTO, resultado.tipo());
        verify(repository, times(1)).saveAndFlush(any(CadastroIdempotenciaEntity.class));
        verify(repository, times(2)).findByKeyHashForUpdate("hash-a");
    }

    @Test
    void mesmaChaveFingerprintDiferenteLancaKeyReused() {
        when(repository.findByKeyHashForUpdate("hash-a")).thenReturn(Optional.of(registro(CadastroIdempotenciaStatus.COMPLETED)));

        IdempotenciaException ex = assertThrows(IdempotenciaException.class,
                () -> service.reservarChave("hash-a", "fp-b", "req-2"));

        assertEquals("IDEMPOTENCY_KEY_REUSED", ex.getCodigo());
        verify(repository, never()).saveAndFlush(any(CadastroIdempotenciaEntity.class));
    }

    @Test
    void calcularKeyHashEhDeterministicoEIgnoraEspacos() {
        String h1 = service.calcularKeyHash("  chave-a  ");
        String h2 = service.calcularKeyHash("chave-a");
        assertEquals(64, h1.length());
        assertEquals(h1, h2);
        assertNotEquals(h1, service.calcularKeyHash("chave-b"));
    }

    @Test
    void calcularFingerprintNormalizaEIgnoraCaixa() {
        String a = service.calcularFingerprint("User@Teste.com", "+5511999999999", "Clinica Beta", "Ana Maria", "basico", true);
        String b = service.calcularFingerprint(" USER@TESTE.COM ", " +5511999999999 ", " clinica beta ", " ana maria ", "BASICO ", true);
        assertEquals(64, a.length());
        assertEquals(a, b);
    }

    @Test
    void marcarCompletadoGravaResultado() {
        CadastroIdempotenciaEntity processando = registro(CadastroIdempotenciaStatus.PROCESSING);
        when(repository.findByKeyHash("hash-a")).thenReturn(Optional.of(processando));

        service.marcarCompletado("hash-a", 1L, 3L, 5L, null, "ACTIVE");

        assertEquals(CadastroIdempotenciaStatus.COMPLETED, processando.getStatus());
        assertEquals(1L, processando.getEmpresaId());
        assertEquals(3L, processando.getUsuarioId());
        assertEquals(5L, processando.getAssinaturaId());
        assertEquals("ACTIVE", processando.getStatusConta());
        verify(repository).save(processando);
    }

    @Test
    void marcarCompletadoNaoSobrescreveCompletado() {
        CadastroIdempotenciaEntity completo = registro(CadastroIdempotenciaStatus.COMPLETED);
        when(repository.findByKeyHash("hash-a")).thenReturn(Optional.of(completo));

        service.marcarCompletado("hash-a", 1L, 3L, 5L, null, "ACTIVE");

        verify(repository, never()).save(completo);
    }

    @Test
    void marcarFalhaNaoSobrescreveCompletado() {
        CadastroIdempotenciaEntity completo = registro(CadastroIdempotenciaStatus.COMPLETED);
        when(repository.findByKeyHash("hash-a")).thenReturn(Optional.of(completo));

        service.marcarFalha("hash-a");

        assertEquals(CadastroIdempotenciaStatus.COMPLETED, completo.getStatus());
        verify(repository, never()).save(completo);
    }

    @Test
    void recuperarResultadoReconstroiRespostaSemEfeitosColaterais() {
        CadastroIdempotenciaEntity completo = registro(CadastroIdempotenciaStatus.COMPLETED);
        completo.setEmpresaId(1L);
        completo.setUsuarioId(3L);
        completo.setStatusConta("ACTIVE");

        UsuarioEntity usuario = UsuarioEntity.builder()
                .id(3L)
                .nome("Ana Maria")
                .email("ana@gendaz.com.br")
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .aceitouTermos(true)
                .dataAceiteTermos(LocalDateTime.now())
                .versaoTermos("2026-06-22")
                .dataAceitePolitica(LocalDateTime.now())
                .versaoPolitica("2026-06-22")
                .dataCriacao(LocalDateTime.now())
                .dataAtualizacao(LocalDateTime.now())
                .build();
        when(usuarioRepository.findByIdComEmpresa(3L)).thenReturn(Optional.of(usuario));
        when(usuarioSessionService.renovarSessao(usuario)).thenReturn("sessão-nova");

        var resposta = service.recuperarResultado(completo);

        assertEquals("ACTIVE", resposta.statusConta());
        assertEquals("sessão-nova", resposta.sessionToken());
        assertEquals("ana@gendaz.com.br", resposta.usuario().email());
        verify(usuarioSessionService).renovarSessao(usuario);
        verify(usuarioSessionService, never()).encerrarSessao(anyString());
    }
}
