package com.minhaempresa.gendaz.auth.idempotencia.service;

import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.LoginResponse;
import com.minhaempresa.gendaz.auth.idempotencia.entity.CadastroIdempotenciaEntity;
import com.minhaempresa.gendaz.auth.idempotencia.enums.CadastroIdempotenciaStatus;
import com.minhaempresa.gendaz.auth.idempotencia.exception.IdempotenciaException;
import com.minhaempresa.gendaz.auth.idempotencia.repository.CadastroIdempotenciaRepository;
import com.minhaempresa.gendaz.auth.service.UsuarioSessionService;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.pagamento.mapper.PagamentoMapper;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.mapper.UsuarioMapper;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class CadastroIdempotenciaService {

    public static final int TTL_MINUTOS = 15;
    private static final String STATUS_CONTA_PENDENTE_PAGAMENTO = "ACCOUNT_PENDING_PAYMENT";

    private final CadastroIdempotenciaRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final AssinaturaService assinaturaService;
    private final PagamentoMapper pagamentoMapper;
    private final UsuarioSessionService usuarioSessionService;
    private final TransactionTemplate novaTransacao;
    private final UsuarioMapper usuarioMapper = new UsuarioMapper();

    public CadastroIdempotenciaService(
            CadastroIdempotenciaRepository repository,
            UsuarioRepository usuarioRepository,
            AssinaturaRepository assinaturaRepository,
            PagamentoPlanoRepository pagamentoPlanoRepository,
            AssinaturaService assinaturaService,
            PagamentoMapper pagamentoMapper,
            UsuarioSessionService usuarioSessionService,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.pagamentoPlanoRepository = pagamentoPlanoRepository;
        this.assinaturaService = assinaturaService;
        this.pagamentoMapper = pagamentoMapper;
        this.usuarioSessionService = usuarioSessionService;
        this.novaTransacao = new TransactionTemplate(transactionManager);
        this.novaTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String calcularKeyHash(String idempotencyKey) {
        return sha256(idempotencyKey == null ? "" : idempotencyKey.trim());
    }

    /**
     * Fingerprint de dados normalizados, nunca inclui senha/token/segredo.
     */
    public String calcularFingerprint(
            String email,
            String telefone,
            String nomeEmpresa,
            String nomeProprietario,
            String plano,
            boolean aceiteTermos
    ) {
        String material = String.join("|",
                normalizar(email),
                normalizar(telefone),
                normalizar(nomeEmpresa),
                normalizar(nomeProprietario),
                normalizar(plano),
                String.valueOf(aceiteTermos));
        return sha256(material);
    }

    /**
     * Reserva ou avalia a chave. A garantia final de unicidade e o UNIQUE do banco.
     *
     * - Chave nova: insere PROCESSING e devolve RESERVADO.
     * - PROCESSING ativa: devolve EM_PROCESSAMENTO (nunca executa de novo).
     * - COMPLETED com mesmo fingerprint: devolve COMPLETADO (replay, sem efeitos colaterais).
     * - FAILED ou PROCESSING expirada: reclama a chave e devolve RESERVADO.
     * - Mesma chave com fingerprint diferente: lanca IdempotenciaException (409 IDEMPOTENCY_KEY_REUSED).
     */
    public ReservaResultado reservarChave(String keyHash, String fingerprint, String requestId) {
        ReservaResultado avaliacao = novaTransacao.execute(status -> avaliarRegistroTx(keyHash, fingerprint, requestId));
        if (avaliacao != null) {
            return avaliacao;
        }

        try {
            return novaTransacao.execute(status -> tentarReservarTx(keyHash, fingerprint, requestId));
        } catch (DataIntegrityViolationException ex) {
            // Corrida: outra request reservou a mesma chave primeiro. Reavalia em transacao nova.
            ReservaResultado corrida = novaTransacao.execute(status -> avaliarRegistroTx(keyHash, fingerprint, requestId));
            if (corrida == null) {
                throw new IllegalStateException("Idempotencia inconsistente para keyHash=" + prefixo(keyHash));
            }
            log.info("[IDEMPOTENCIA] corrida detectada na reserva keyHash={} resultado={}",
                    prefixo(keyHash), corrida.tipo());
            return corrida;
        }
    }

    /**
     * Deve rodar dentro da MESMA transacao que confirma os dados do cadastro.
     * Assim, se a criacao der commit, o COMPLETED tambem comita; se rolar back,
     * o COMPLETED nao e gravado (a falha real e registrada depois via marcarFalha).
     */
    @Transactional
    public void marcarCompletado(String keyHash, Long empresaId, Long usuarioId, Long assinaturaId,
                                 Long pagamentoPlanoId, String statusConta) {
        repository.findByKeyHash(keyHash).ifPresent(registro -> {
            if (registro.getStatus() == CadastroIdempotenciaStatus.COMPLETED) {
                return;
            }
            registro.setStatus(CadastroIdempotenciaStatus.COMPLETED);
            registro.setEmpresaId(empresaId);
            registro.setUsuarioId(usuarioId);
            registro.setAssinaturaId(assinaturaId);
            registro.setPagamentoPlanoId(pagamentoPlanoId);
            registro.setStatusConta(statusConta);
            registro.setAtualizadoEm(LocalDateTime.now());
            repository.save(registro);
        });
    }

    /**
     * Marca FAILED em transacao propria (REQUIRES_NEW) para persistir mesmo quando
     * a transacao do cadastro ja esta marcada para rollback.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void marcarFalha(String keyHash) {
        repository.findByKeyHash(keyHash).ifPresent(registro -> {
            if (registro.getStatus() == CadastroIdempotenciaStatus.COMPLETED) {
                return;
            }
            registro.setStatus(CadastroIdempotenciaStatus.FAILED);
            registro.setAtualizadoEm(LocalDateTime.now());
            repository.save(registro);
        });
    }

    /**
     * Reconstrói a resposta de um cadastro ja concluido (replay) SEM executar
     * novamente criarContaBase, checkout, e-mails ou trial. Nao guarda sessao na
     * tabela de idempotencia; para BASIC gera uma nova sessao segura no fluxo de
     * cadastro (sem tocar em /auth/login).
     */
    @Transactional
    public LoginResponse recuperarResultado(CadastroIdempotenciaEntity registro) {
        Long usuarioId = registro.getUsuarioId();
        Long assinaturaId = registro.getAssinaturaId();
        Long pagamentoPlanoId = registro.getPagamentoPlanoId();
        String statusConta = registro.getStatusConta();

        UsuarioEntity usuario = usuarioRepository.findByIdComEmpresa(usuarioId)
                .orElseThrow(() -> new IllegalStateException("Cadastro concluido sem usuario registrado na idempotencia."));
        AssinaturaEntity assinatura = assinaturaId == null ? null : assinaturaRepository.findById(assinaturaId).orElse(null);
        AssinaturaResponse assinaturaRes = assinatura == null ? null : assinaturaService.toResponse(assinatura);

        boolean pendentePagamento = STATUS_CONTA_PENDENTE_PAGAMENTO.equals(statusConta) || pagamentoPlanoId != null;
        PagamentoPlanoResponse pagamento = null;
        if (pagamentoPlanoId != null) {
            PagamentoPlanoEntity pagamentoEntity = pagamentoPlanoRepository.findById(pagamentoPlanoId).orElse(null);
            pagamento = pagamentoEntity == null ? null : pagamentoMapper.toPlanoResponse(pagamentoEntity);
        }

        if (pendentePagamento) {
            return new LoginResponse(
                    "Cadastro criado. A conta Pro aguarda confirmacao de pagamento.",
                    usuarioMapper.toResponse(usuario),
                    assinaturaRes,
                    pagamento,
                    STATUS_CONTA_PENDENTE_PAGAMENTO,
                    null,
                    "PAGAMENTO_PENDENTE"
            );
        }

        String sessionToken = usuarioSessionService.renovarSessao(usuario);
        return new LoginResponse(
                "Conta criada com sucesso. Seu teste gratis de 7 dias comecou.",
                usuarioMapper.toResponse(usuario),
                assinaturaRes,
                null,
                "ACTIVE",
                sessionToken,
                null
        );
    }

    private ReservaResultado avaliarRegistroTx(String keyHash, String fingerprint, String requestId) {
        Optional<CadastroIdempotenciaEntity> registroOpt = repository.findByKeyHashForUpdate(keyHash);
        if (registroOpt.isEmpty()) {
            return null;
        }
        CadastroIdempotenciaEntity registro = registroOpt.get();

        if (!fingerprint.equals(registro.getRequestFingerprint())) {
            throw new IdempotenciaException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Esta chave de idempotencia ja foi utilizada com dados diferentes."
            );
        }

        LocalDateTime agora = LocalDateTime.now();
        switch (registro.getStatus()) {
            case COMPLETED -> {
                registro.setUltimoRequestId(limitar(requestId, 64));
                registro.setAtualizadoEm(agora);
                repository.save(registro);
                return ReservaResultado.completado(registro);
            }
            case PROCESSING -> {
                if (registro.getExpiraEm() != null && registro.getExpiraEm().isBefore(agora)) {
                    log.warn("[IDEMPOTENCIA] chave expirada durante PROCESSING, recuperando keyHash={}", prefixo(keyHash));
                    registro.setStatus(CadastroIdempotenciaStatus.PROCESSING);
                    registro.setExpiraEm(agora.plusMinutes(TTL_MINUTOS));
                    registro.setUltimoRequestId(limitar(requestId, 64));
                    registro.setAtualizadoEm(agora);
                    repository.save(registro);
                    return ReservaResultado.reservado(registro);
                }
                registro.setUltimoRequestId(limitar(requestId, 64));
                repository.save(registro);
                return ReservaResultado.emProcessamento(registro);
            }
            case FAILED -> {
                log.info("[IDEMPOTENCIA] reutilizando chave com status FAILED keyHash={}", prefixo(keyHash));
                registro.setStatus(CadastroIdempotenciaStatus.PROCESSING);
                registro.setExpiraEm(agora.plusMinutes(TTL_MINUTOS));
                registro.setUltimoRequestId(limitar(requestId, 64));
                registro.setAtualizadoEm(agora);
                repository.save(registro);
                return ReservaResultado.reservado(registro);
            }
            default -> throw new IllegalStateException("Status de idempotencia desconhecido: " + registro.getStatus());
        }
    }

    private ReservaResultado tentarReservarTx(String keyHash, String fingerprint, String requestId) {
        LocalDateTime agora = LocalDateTime.now();
        CadastroIdempotenciaEntity novo = CadastroIdempotenciaEntity.builder()
                .keyHash(keyHash)
                .requestFingerprint(fingerprint)
                .status(CadastroIdempotenciaStatus.PROCESSING)
                .criadoEm(agora)
                .atualizadoEm(agora)
                .expiraEm(agora.plusMinutes(TTL_MINUTOS))
                .ultimoRequestId(limitar(requestId, 64))
                .build();
        repository.saveAndFlush(novo);
        return ReservaResultado.reservado(novo);
    }

    private String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel no runtime.", ex);
        }
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase();
    }

    private String limitar(String valor, int max) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.length() <= max ? valor : valor.substring(0, max);
    }

    private String prefixo(String valor) {
        if (valor == null) {
            return "null";
        }
        return valor.length() <= 12 ? valor : valor.substring(0, 12) + "...";
    }
}
