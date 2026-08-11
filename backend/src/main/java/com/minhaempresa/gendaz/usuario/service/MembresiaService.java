package com.minhaempresa.gendaz.usuario.service;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.admin.repository.AuditLogRepository;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.convite.entity.ConviteEmpresaEntity;
import com.minhaempresa.gendaz.convite.enums.StatusConviteEmpresa;
import com.minhaempresa.gendaz.convite.repository.ConviteEmpresaRepository;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.auth.repository.PasswordResetTokenRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.chamado.repository.ChamadoRepository;
import com.minhaempresa.gendaz.membresia.entity.MembresiaEntity;
import com.minhaempresa.gendaz.membresia.enums.FuncaoMembresia;
import com.minhaempresa.gendaz.membresia.enums.StatusMembresia;
import com.minhaempresa.gendaz.membresia.repository.MembresiaRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.auth.service.PasswordService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.usuario.dto.MembresiaDtos.AceitarConviteRequest;
import com.minhaempresa.gendaz.usuario.dto.MembresiaDtos.ConviteEmpresaResponse;
import com.minhaempresa.gendaz.usuario.dto.MembresiaDtos.ConvitePublicoResponse;
import com.minhaempresa.gendaz.usuario.dto.MembresiaDtos.MembroEmpresaResponse;
import com.minhaempresa.gendaz.usuario.dto.MembresiaDtos.RecusarConviteRequest;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembresiaService {
    private static final int LIMITE_REENVIO = 3;
    private static final java.time.Duration DURACAO_CONVITE = java.time.Duration.ofHours(1);
    private static final List<PerfilUsuario> PERFIS_PAINEL_DIRETOS = List.of(PerfilUsuario.SUPER_ADMIN, PerfilUsuario.DONO);
    private final MembresiaRepository membresiaRepository;
    private final ConviteEmpresaRepository conviteRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final ChamadoRepository chamadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AssinaturaService assinaturaService;
    private final PlanoService planoService;
    private final SanitizacaoService sanitizacaoService;
    private final ResendEmailService resendEmailService;
    private final PasswordService passwordService;
    private final AdminAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();
    @Value("${app.frontend-url:${FRONTEND_URL:https://gendaz.site}}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<MembroEmpresaResponse> listarMembros(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return membresiaRepository.findByEmpresaId(empresaId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ConviteEmpresaResponse> listarConvites(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return conviteRepository.findByEmpresaIdAndStatus(empresaId, StatusConviteEmpresa.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ConviteEmpresaResponse criarConvite(Long empresaId, Long usuarioAtualId, String nomeBruto, String telefoneBruto, String emailBruto) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        String nome = sanitizacaoService.textoObrigatorio(nomeBruto);
        String telefone = sanitizacaoService.telefone(telefoneBruto);
        String email = sanitizacaoService.email(emailBruto);
        if (email == null || !email.contains("@")) {
            throw new BusinessException("Email invalido.");
        }
        List<UsuarioEntity> usuariosPainel = buscarUsuariosPainelPorEmail(email);
        if (usuariosPainel.size() > 1) {
            throw new ConflictException("Dados de usuario duplicados. Contate o suporte para regularizacao.");
        }
        if (usuariosPainel.stream().anyMatch(usuario -> membresiaRepository.existsByEmpresaIdAndUsuarioId(empresaId, usuario.getId()))) {
            throw new ConflictException("Email ja vinculado a empresa.");
        }
        conviteRepository.findByEmpresaIdAndEmailAndStatus(empresaId, email, StatusConviteEmpresa.PENDING)
                .ifPresent(i -> { throw new ConflictException("Convite pendente ja existente."); });
        if (!usuariosPainel.isEmpty()) {
            throw new ConflictException("Email ja vinculado a empresa.");
        }
        String token = gerarToken();
        ConviteEmpresaEntity convite = ConviteEmpresaEntity.builder()
                .empresa(executor.getEmpresa())
                .nomeConvidado(nome)
                .telefoneConvidado(telefone)
                .email(email)
                .criadoPor(executor)
                .status(StatusConviteEmpresa.PENDING)
                .dataExpiracao(LocalDateTime.now().plus(DURACAO_CONVITE))
                .tokenHash(hash(token))
                .reenvios(0)
                .build();
        conviteRepository.save(convite);
        boolean enviado = resendEmailService.enviarConviteEmpresa(
                email,
                nome,
                executor.getEmpresa().getNomeFantasia(),
                montarUrlConvite(token),
                montarUrlConvite(token) + "&acao=recusar"
        );
        if (!enviado) {
            throw new BusinessException("Falha no envio do convite.");
        }
        registrarAudit("INVITE_CREATED", executor, executor.getEmpresa(), "Convite criado", convite.getId(), "SUCCESS");
        return toResponse(convite);
    }

    @Transactional
    public ConviteEmpresaResponse reenviarConvite(Long empresaId, Long usuarioAtualId, Long conviteId) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        ConviteEmpresaEntity convite = buscarConvite(empresaId, conviteId);
        if (convite.getStatus() != StatusConviteEmpresa.PENDING) {
            throw new BusinessException("Convite nao esta pendente.");
        }
        if (convite.getReenvios() != null && convite.getReenvios() >= LIMITE_REENVIO) {
            throw new BusinessException("Limite de reenvios atingido.");
        }
        String nome = convite.getNomeConvidado();
        String telefone = convite.getTelefoneConvidado();
        String email = convite.getEmail();
        conviteRepository.delete(convite);
        String tokenNovo = gerarToken();
        ConviteEmpresaEntity novoConvite = ConviteEmpresaEntity.builder()
                .empresa(executor.getEmpresa())
                .nomeConvidado(nome)
                .telefoneConvidado(telefone)
                .email(email)
                .criadoPor(executor)
                .status(StatusConviteEmpresa.PENDING)
                .dataExpiracao(LocalDateTime.now().plus(DURACAO_CONVITE))
                .tokenHash(hash(tokenNovo))
                .reenvios((convite.getReenvios() == null ? 0 : convite.getReenvios()) + 1)
                .build();
        conviteRepository.save(novoConvite);
        resendEmailService.enviarConviteEmpresa(
                novoConvite.getEmail(),
                novoConvite.getNomeConvidado(),
                convite.getEmpresa().getNomeFantasia(),
                montarUrlConvite(tokenNovo),
                montarUrlConvite(tokenNovo) + "&acao=recusar"
        );
        registrarAudit("INVITE_RESENT", executor, executor.getEmpresa(), "Convite reenviado", novoConvite.getId(), "SUCCESS");
        return toResponse(novoConvite);
    }

    @Transactional
    public ConviteEmpresaResponse cancelarConvite(Long empresaId, Long usuarioAtualId, Long conviteId) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        ConviteEmpresaEntity convite = buscarConvite(empresaId, conviteId);
        ConviteEmpresaResponse resposta = toResponse(convite);
        conviteRepository.delete(convite);
        registrarAudit("INVITE_CANCELLED", executor, executor.getEmpresa(), "Convite cancelado", convite.getId(), "SUCCESS");
        return resposta;
    }

    @Transactional
    public ConviteEmpresaResponse recusarConvite(String token) {
        String hash = hash(token);
        ConviteEmpresaEntity convite = conviteRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResourceNotFoundException("Convite nao encontrado."));
        if (convite.getStatus() != StatusConviteEmpresa.PENDING) {
            throw new BusinessException("Convite invalido.");
        }
        convite.setStatus(StatusConviteEmpresa.CANCELLED);
        convite.setCanceladoEm(LocalDateTime.now());
        conviteRepository.save(convite);
        registrarAudit("INVITE_REJECTED", convite.getCriadoPor(), convite.getEmpresa(), "Convite recusado", convite.getId(), "SUCCESS");
        return toResponse(convite);
    }

    @Transactional(readOnly = true)
    public ConvitePublicoResponse convitePublico(String token) {
        ConviteEmpresaEntity convite = buscarConvitePorToken(token);
        boolean valido = convite.getStatus() == StatusConviteEmpresa.PENDING && convite.getDataExpiracao().isAfter(LocalDateTime.now());
        return new ConvitePublicoResponse(
                convite.getNomeConvidado(),
                convite.getEmail(),
                convite.getEmpresa() == null ? null : convite.getEmpresa().getNomeFantasia(),
                valido
        );
    }

    @Transactional
    public MembroEmpresaResponse removerMembro(Long empresaId, Long usuarioAtualId, Long usuarioId) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        MembresiaEntity membresia = buscarMembresiaUnica(empresaId, usuarioId);
        if (Boolean.TRUE.equals(membresia.getOwner())) {
            throw new BusinessException("O dono nao pode ser removido diretamente.");
        }
        UsuarioEntity usuario = membresia.getUsuario();
        Long membroId = membroId(membresia);
        desalocarDadosUsuario(usuario, executor, empresaId);
        chamadoRepository.desvincularUsuario(usuarioId);
        passwordResetTokenRepository.deleteByUsuarioId(usuarioId);
        conviteRepository.reatribuirCriador(usuarioId, executor);
        auditLogRepository.desvincularUsuario(usuarioId);
        auditLogRepository.desvincularAdmin(usuarioId);
        membresiaRepository.desvincularAlteracoes(usuarioId);
        membresiaRepository.delete(membresia);
        usuarioRepository.delete(usuario);
        registrarAudit("MEMBER_REMOVED", executor, executor.getEmpresa(), "Conta excluida", membroId, "SUCCESS");
        return new MembroEmpresaResponse(membroId, usuarioId, usuario.getNome(), usuario.getEmail(), StatusMembresia.REMOVED, FuncaoMembresia.MEMBER, false, membresia.getDataEntrada(), LocalDateTime.now(), membresia.getDataCriacao(), LocalDateTime.now());
    }

    @Transactional
    public MembroEmpresaResponse desativarMembro(Long empresaId, Long usuarioAtualId, Long usuarioId) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        MembresiaEntity membresia = buscarMembresiaUnica(empresaId, usuarioId);
        if (Boolean.TRUE.equals(membresia.getOwner())) {
            throw new BusinessException("O dono nao pode ser desativado.");
        }
        membresia.setStatus(StatusMembresia.INACTIVE);
        membresia.setDataRemocao(LocalDateTime.now());
        membresiaRepository.save(membresia);
        membresia.getUsuario().setStatus(StatusUsuario.INATIVO);
        usuarioRepository.save(membresia.getUsuario());
        registrarAudit("MEMBER_DISABLED", executor, executor.getEmpresa(), "Membro desativado", membroId(membresia), "SUCCESS");
        return toResponse(membresia);
    }

    @Transactional
    public MembroEmpresaResponse reativarMembro(Long empresaId, Long usuarioAtualId, Long usuarioId) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        validarLimite(empresaId);
        MembresiaEntity membresia = buscarMembresiaUnica(empresaId, usuarioId);
        membresia.setStatus(StatusMembresia.ACTIVE);
        membresia.setDataRemocao(null);
        membresiaRepository.save(membresia);
        membresia.getUsuario().setStatus(StatusUsuario.ATIVO);
        usuarioRepository.save(membresia.getUsuario());
        registrarAudit("MEMBER_REACTIVATED", executor, executor.getEmpresa(), "Membro reativado", membroId(membresia), "SUCCESS");
        return toResponse(membresia);
    }

    @Transactional
    public MembroEmpresaResponse transferirPropriedade(Long empresaId, Long usuarioAtualId, Long novoOwnerId) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        MembresiaEntity atual = buscarMembresiaUnica(empresaId, usuarioAtualId);
        MembresiaEntity novo = buscarMembresiaUnica(empresaId, novoOwnerId);
        if (novo.getStatus() != StatusMembresia.ACTIVE) {
            throw new BusinessException("Novo dono precisa estar ativo.");
        }
        atual.setOwner(false);
        atual.setFuncao(FuncaoMembresia.MEMBER);
        novo.setOwner(true);
        novo.setFuncao(FuncaoMembresia.OWNER);
        membresiaRepository.save(atual);
        membresiaRepository.save(novo);
        registrarAudit("OWNERSHIP_TRANSFERRED", executor, executor.getEmpresa(), "Transferencia de proprietario", novo.getId(), "SUCCESS");
        return toResponse(novo);
    }

    @Transactional
    public MembroEmpresaResponse aceitarConvite(String token, AceitarConviteRequest request) {
        ConviteEmpresaEntity convite = buscarConvitePorToken(token);
        if (convite.getStatus() != StatusConviteEmpresa.PENDING) throw new BusinessException("Convite invalido.");
        if (convite.getDataExpiracao().isBefore(LocalDateTime.now())) throw new BusinessException("Convite expirado.");
        String email = sanitizacaoService.email(request.email());
        if (!convite.getEmail().equalsIgnoreCase(email)) throw new BusinessException("Convite invalido para este email.");
        validarLimite(convite.getEmpresa().getId());
        List<UsuarioEntity> usuariosExistentes = usuarioRepository.findAllByEmailIgnoreCase(email);
        List<UsuarioEntity> usuariosPainel = buscarUsuariosPainelPorEmail(email);
        if (usuariosPainel.size() > 1) {
            throw new ConflictException("Dados de usuario duplicados. Contate o suporte para regularizacao.");
        }
        Optional<UsuarioEntity> legadoParaConverter = usuariosPainel.isEmpty()
                ? selecionarUsuarioLegadoParaConverter(usuariosExistentes, usuariosPainel, convite.getEmpresa().getId())
                : Optional.empty();
        UsuarioEntity usuario = legadoParaConverter.orElseGet(() -> usuariosPainel.isEmpty() ? null : usuariosPainel.get(0));
        if (usuario == null) {
            passwordService.validarSenha(request.senha());
            usuario = usuarioRepository.save(UsuarioEntity.builder()
                    .nome(sanitizacaoService.textoObrigatorio(request.nome()))
                    .email(email)
                    .senha(passwordService.hash(request.senha()))
                    .perfil(PerfilUsuario.ATENDENTE)
                    .status(StatusUsuario.ATIVO)
                    .empresa(convite.getEmpresa())
                    .aceitouTermos(false)
                    .build());
        } else if (usuario.getEmpresa() == null || !usuario.getEmpresa().getId().equals(convite.getEmpresa().getId())) {
            throw new ConflictException("Email ja vinculado a empresa.");
        } else if (legadoParaConverter.isPresent()) {
            passwordService.validarSenha(request.senha());
            usuario.setNome(sanitizacaoService.textoObrigatorio(request.nome()));
            usuario.setSenha(passwordService.hash(request.senha()));
            usuario.setPerfil(PerfilUsuario.ATENDENTE);
            usuario.setStatus(StatusUsuario.ATIVO);
            usuario.setEmpresa(convite.getEmpresa());
            usuarioRepository.save(usuario);
        }
        final Long usuarioId = usuario.getId();
        List<MembresiaEntity> membresiasUsuario = membresiaRepository.findByEmpresaId(convite.getEmpresa().getId()).stream()
                .filter(m -> m.getUsuario() != null && m.getUsuario().getId().equals(usuarioId))
                .toList();
        if (membresiasUsuario.size() > 1) {
            throw new ConflictException("Dados de membresia duplicados. Contate o suporte para regularizacao.");
        }
        MembresiaEntity membresia = membresiasUsuario.isEmpty()
                ? MembresiaEntity.builder().usuario(usuario).empresa(convite.getEmpresa()).build()
                : membresiasUsuario.get(0);
        membresia.setEmpresa(convite.getEmpresa());
        membresia.setStatus(StatusMembresia.ACTIVE);
        membresia.setOwner(false);
        membresia.setFuncao(FuncaoMembresia.MEMBER);
        membresiaRepository.save(membresia);
        convite.setStatus(StatusConviteEmpresa.ACCEPTED);
        convite.setDataAceite(LocalDateTime.now());
        convite.setAceitoPorUsuarioId(usuario.getId());
        conviteRepository.save(convite);
        registrarAudit("INVITE_ACCEPTED", usuario, convite.getEmpresa(), "Convite aceito", convite.getId(), "SUCCESS");
        return new MembroEmpresaResponse(membresia.getId(), usuario.getId(), usuario.getNome(), usuario.getEmail(), membresia.getStatus(), membresia.getFuncao(), membresia.getOwner(), membresia.getDataEntrada(), membresia.getDataRemocao(), membresia.getDataCriacao(), membresia.getDataAtualizacao());
    }

    @Transactional(readOnly = true)
    public int contarUsados(Long empresaId) {
        return (int) membresiaRepository.findByEmpresaId(empresaId).stream()
                .filter(m -> m.getStatus() == StatusMembresia.ACTIVE)
                .count();
    }

    @Transactional(readOnly = true)
    public int limiteEmpresa(Long empresaId) {
        AssinaturaEntity assinatura = assinaturaService.buscarAtualPorEmpresa(empresaId).orElse(null);
        if (assinatura == null || assinatura.getStatus() == StatusAssinatura.EXPIRADA) return 1;
        PlanoEntity plano = assinatura.getPlano();
        return "PRO".equalsIgnoreCase(plano.getNome()) ? 3 : 1;
    }

    private void validarLimite(Long empresaId) {
        if (contarUsados(empresaId) >= limiteEmpresa(empresaId)) {
            throw new BusinessException("Seu plano atingiu o limite de usuarios.");
        }
    }

    private List<UsuarioEntity> buscarUsuariosPainelPorEmail(String email) {
        return usuarioRepository.findUsuariosPainelByEmailIgnoreCase(email, PERFIS_PAINEL_DIRETOS);
    }

    private Optional<UsuarioEntity> selecionarUsuarioLegadoParaConverter(List<UsuarioEntity> usuariosExistentes, List<UsuarioEntity> usuariosPainel, Long empresaId) {
        if (usuariosExistentes.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> idsPainel = usuariosPainel.stream().map(UsuarioEntity::getId).collect(java.util.stream.Collectors.toSet());
        List<UsuarioEntity> legados = usuariosExistentes.stream()
                .filter(usuario -> !idsPainel.contains(usuario.getId()))
                .filter(usuario -> !membresiaRepository.existsByUsuarioId(usuario.getId()))
                .toList();
        if (legados.isEmpty()) {
            return Optional.empty();
        }
        List<UsuarioEntity> legadosDaEmpresa = legados.stream()
                .filter(usuario -> usuario.getEmpresa() != null && empresaId.equals(usuario.getEmpresa().getId()))
                .toList();
        if (legadosDaEmpresa.size() == 1) {
            return Optional.of(legadosDaEmpresa.get(0));
        }
        if (legados.size() == 1) {
            return Optional.of(legados.get(0));
        }
        throw new ConflictException("Este email possui acessos legados duplicados. Regularize o cadastro antes de aceitar o convite.");
    }

    private UsuarioEntity validarDono(Long empresaId, Long usuarioAtualId) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioAtualId).orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado."));
        if (usuario.getEmpresa() == null || !empresaId.equals(usuario.getEmpresa().getId())) {
            throw new ResourceNotFoundException("Usuario nao encontrado.");
        }
        List<MembresiaEntity> membros = membresiaRepository.findAllByEmpresaIdAndUsuarioId(empresaId, usuarioAtualId);
        if (membros.isEmpty()) {
            throw new BusinessException("Usuario sem membresia.");
        }
        if (membros.size() > 1) {
            throw new ConflictException("Dados de membresia duplicados. Contate o suporte para regularizacao.");
        }
        List<MembresiaEntity> membrosAtivos = membros.stream().filter(m -> m.getStatus() == StatusMembresia.ACTIVE).toList();
        if (membrosAtivos.isEmpty()) {
            throw new BusinessException("Usuario sem permissao.");
        }
        if (membrosAtivos.size() > 1) {
            throw new ConflictException("Dados de membresia duplicados. Contate o suporte para regularizacao.");
        }
        MembresiaEntity membresia = membrosAtivos.get(0);
        if (!Boolean.TRUE.equals(membresia.getOwner())) {
            throw new BusinessException("Usuario sem permissao.");
        }
        return usuario;
    }

    private ConviteEmpresaEntity buscarConvite(Long empresaId, Long conviteId) {
        ConviteEmpresaEntity convite = conviteRepository.findById(conviteId).orElseThrow(() -> new ResourceNotFoundException("Convite nao encontrado."));
        if (!convite.getEmpresa().getId().equals(empresaId)) throw new ResourceNotFoundException("Convite nao encontrado.");
        return convite;
    }

    private ConviteEmpresaEntity buscarConvitePorToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("Convite nao encontrado.");
        }
        String hash = hash(token);
        return conviteRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResourceNotFoundException("Convite nao encontrado."));
    }

    private MembresiaEntity buscarMembresiaUnica(Long empresaId, Long usuarioId) {
        List<MembresiaEntity> membros = membresiaRepository.findAllByEmpresaIdAndUsuarioId(empresaId, usuarioId);
        if (membros.isEmpty()) {
            throw new ResourceNotFoundException("Membro nao encontrado.");
        }
        if (membros.size() > 1) {
            throw new ConflictException("Dados de membresia duplicados. Contate o suporte para regularizacao.");
        }
        return membros.get(0);
    }

    private MembroEmpresaResponse toResponse(MembresiaEntity membresia) {
        UsuarioEntity usuario = membresia.getUsuario();
        return new MembroEmpresaResponse(membresia.getId(), usuario.getId(), usuario.getNome(), usuario.getEmail(), membresia.getStatus(), membresia.getFuncao(), membresia.getOwner(), membresia.getDataEntrada(), membresia.getDataRemocao(), membresia.getDataCriacao(), membresia.getDataAtualizacao());
    }

    private ConviteEmpresaResponse toResponse(ConviteEmpresaEntity convite) {
        return new ConviteEmpresaResponse(convite.getId(), convite.getEmpresa().getId(), convite.getEmail(), convite.getStatus(), convite.getDataCriacao(), convite.getDataExpiracao(), convite.getDataAceite(), convite.getReenvios(), false);
    }

    private long membroId(MembresiaEntity m) { return m.getId(); }

    private void validarEmpresaAtual(Long empresaId) {
        Long empresaContexto = CompanyContext.getCompanyId();
        if (empresaContexto != null && empresaId != null && !empresaContexto.equals(empresaId)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }
    }

    private String gerarToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String montarUrlConvite(String token) {
        String base = frontendUrl == null || frontendUrl.isBlank() ? "https://gendaz.site" : frontendUrl.trim().replaceAll("/+$", "");
        return base + "/convite?token=" + token;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException("Nao foi possivel gerar token.");
        }
    }

    private void registrarAudit(String tipo, UsuarioEntity usuario, EmpresaEntity empresa, String descricao, Long recursoId, String resultado) {
        try { auditService.registrar(tipo, resultado, null, usuario, empresa, descricao, null, null, null); } catch (Exception ignored) {}
    }

    private void desalocarDadosUsuario(UsuarioEntity usuario, UsuarioEntity executor, Long empresaId) {
        if (usuario == null) {
            return;
        }
        if (usuario.getEmpresa() == null || !empresaId.equals(usuario.getEmpresa().getId())) {
            throw new BusinessException("Usuario sem permissao.");
        }
        usuario.setSessaoAtiva(null);
        usuario.setSessaoAtivaMeuGendaz(null);
        usuario.setStatus(StatusUsuario.REMOVIDO);
        usuarioRepository.save(usuario);
    }
}

