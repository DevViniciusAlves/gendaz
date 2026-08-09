package com.minhaempresa.agendapro.usuario.service;

import com.minhaempresa.agendapro.admin.service.AdminAuditService;
import com.minhaempresa.agendapro.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.agendapro.assinatura.enums.StatusAssinatura;
import com.minhaempresa.agendapro.assinatura.service.AssinaturaService;
import com.minhaempresa.agendapro.convite.entity.ConviteEmpresaEntity;
import com.minhaempresa.agendapro.convite.enums.StatusConviteEmpresa;
import com.minhaempresa.agendapro.convite.repository.ConviteEmpresaRepository;
import com.minhaempresa.agendapro.email.ResendEmailService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.membresia.entity.MembresiaEntity;
import com.minhaempresa.agendapro.membresia.enums.FuncaoMembresia;
import com.minhaempresa.agendapro.membresia.enums.StatusMembresia;
import com.minhaempresa.agendapro.membresia.repository.MembresiaRepository;
import com.minhaempresa.agendapro.plano.entity.PlanoEntity;
import com.minhaempresa.agendapro.plano.service.PlanoService;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ConflictException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
import com.minhaempresa.agendapro.usuario.dto.MembresiaDtos.AceitarConviteRequest;
import com.minhaempresa.agendapro.usuario.dto.MembresiaDtos.ConviteEmpresaResponse;
import com.minhaempresa.agendapro.usuario.dto.MembresiaDtos.MembroEmpresaResponse;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.enums.StatusUsuario;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembresiaService {
    private static final int LIMITE_REENVIO = 3;
    private final MembresiaRepository membresiaRepository;
    private final ConviteEmpresaRepository conviteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AssinaturaService assinaturaService;
    private final PlanoService planoService;
    private final SanitizacaoService sanitizacaoService;
    private final ResendEmailService resendEmailService;
    private final AdminAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<MembroEmpresaResponse> listarMembros(Long empresaId) {
        return membresiaRepository.findByEmpresaId(empresaId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ConviteEmpresaResponse> listarConvites(Long empresaId) {
        return conviteRepository.findByEmpresaId(empresaId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ConviteEmpresaResponse criarConvite(Long empresaId, Long usuarioAtualId, String emailBruto) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        String email = sanitizacaoService.email(emailBruto);
        if (email == null || !email.contains("@")) {
            throw new BusinessException("Email invalido.");
        }
        validarLimite(empresaId);
        if (usuarioRepository.findByEmailIgnoreCase(email)
                .map(usuario -> membresiaRepository.existsByEmpresaIdAndUsuarioId(empresaId, usuario.getId()))
                .orElse(false)) {
            throw new ConflictException("Email ja vinculado a empresa.");
        }
        conviteRepository.findByEmpresaIdAndEmailAndStatus(empresaId, email, StatusConviteEmpresa.PENDING)
                .ifPresent(i -> { throw new ConflictException("Convite pendente ja existente."); });
        UsuarioEntity existente = usuarioRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existente != null && existente.getEmpresa() != null && !empresaId.equals(existente.getEmpresa().getId())) {
            throw new ConflictException("Email ja vinculado a empresa.");
        }
        ConviteEmpresaEntity convite = ConviteEmpresaEntity.builder()
                .empresa(executor.getEmpresa())
                .email(email)
                .criadoPor(executor)
                .status(StatusConviteEmpresa.PENDING)
                .dataExpiracao(LocalDateTime.now().plusDays(7))
                .tokenHash(hash(gerarToken()))
                .reenvios(0)
                .build();
        conviteRepository.save(convite);
        boolean enviado = resendEmailService.enviarComTemplate(
                email,
                "Convite para acessar a conta Gendaz",
                "Voce foi convidado para acessar a conta",
                "Acesse o painel com seu email para concluir o cadastro.",
                "<p style='margin:0;'>Seu acesso foi liberado pela empresa.</p>",
                "https://gendaz.site/login",
                "Entrar"
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
        convite.setReenvios(convite.getReenvios() + 1);
        convite.setDataExpiracao(LocalDateTime.now().plusDays(7));
        convite.setTokenHash(hash(gerarToken()));
        conviteRepository.save(convite);
        String ctaUrl = "https://gendaz.site/login";
        String ctaTexto = "Entrar";
        resendEmailService.enviarComTemplate(
                convite.getEmail(),
                "Convite re-enviado",
                "Seu convite foi renovado",
                "Seu acesso foi renovado e o link anterior foi invalidado.",
                "<p>Use o novo acesso enviado.</p>",
                ctaUrl,
                ctaTexto
        );
        registrarAudit("INVITE_RESENT", executor, executor.getEmpresa(), "Convite reenviado", convite.getId(), "SUCCESS");
        return toResponse(convite);
    }

    @Transactional
    public ConviteEmpresaResponse cancelarConvite(Long empresaId, Long usuarioAtualId, Long conviteId) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        ConviteEmpresaEntity convite = buscarConvite(empresaId, conviteId);
        convite.setStatus(StatusConviteEmpresa.CANCELLED);
        convite.setCanceladoEm(LocalDateTime.now());
        conviteRepository.save(convite);
        registrarAudit("INVITE_CANCELLED", executor, executor.getEmpresa(), "Convite cancelado", convite.getId(), "SUCCESS");
        return toResponse(convite);
    }

    @Transactional
    public MembroEmpresaResponse removerMembro(Long empresaId, Long usuarioAtualId, Long usuarioId) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        MembresiaEntity membresia = membresiaRepository.findByEmpresaIdAndUsuarioId(empresaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro nao encontrado."));
        if (Boolean.TRUE.equals(membresia.getOwner())) {
            throw new BusinessException("O dono nao pode ser removido diretamente.");
        }
        membresia.setStatus(StatusMembresia.REMOVED);
        membresia.setDataRemocao(LocalDateTime.now());
        membresiaRepository.save(membresia);
        UsuarioEntity usuario = membresia.getUsuario();
        usuario.setStatus(StatusUsuario.REMOVIDO);
        usuarioRepository.save(usuario);
        registrarAudit("MEMBER_REMOVED", executor, executor.getEmpresa(), "Membro removido", membroId(membresia), "SUCCESS");
        return toResponse(membresia);
    }

    @Transactional
    public MembroEmpresaResponse desativarMembro(Long empresaId, Long usuarioAtualId, Long usuarioId) {
        UsuarioEntity executor = validarDono(empresaId, usuarioAtualId);
        MembresiaEntity membresia = membresiaRepository.findByEmpresaIdAndUsuarioId(empresaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro nao encontrado."));
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
        MembresiaEntity membresia = membresiaRepository.findByEmpresaIdAndUsuarioId(empresaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro nao encontrado."));
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
        MembresiaEntity atual = membresiaRepository.findByEmpresaIdAndUsuarioId(empresaId, usuarioAtualId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro nao encontrado."));
        MembresiaEntity novo = membresiaRepository.findByEmpresaIdAndUsuarioId(empresaId, novoOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro nao encontrado."));
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
        String hash = hash(token);
        ConviteEmpresaEntity convite = conviteRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResourceNotFoundException("Convite nao encontrado."));
        if (convite.getStatus() != StatusConviteEmpresa.PENDING) throw new BusinessException("Convite invalido.");
        if (convite.getDataExpiracao().isBefore(LocalDateTime.now())) throw new BusinessException("Convite expirado.");
        String email = sanitizacaoService.email(request.email());
        if (!convite.getEmail().equalsIgnoreCase(email)) throw new BusinessException("Convite invalido para este email.");
        validarLimite(convite.getEmpresa().getId());
        UsuarioEntity usuario = usuarioRepository.findByEmailIgnoreCase(email).orElse(null);
        if (usuario == null) {
            usuario = usuarioRepository.save(UsuarioEntity.builder()
                    .nome(sanitizacaoService.textoObrigatorio(request.nome()))
                    .email(email)
                    .senha(request.senha())
                    .perfil(PerfilUsuario.ATENDENTE)
                    .status(StatusUsuario.ATIVO)
                    .empresa(convite.getEmpresa())
                    .aceitouTermos(false)
                    .build());
        } else if (usuario.getEmpresa() != null && !usuario.getEmpresa().getId().equals(convite.getEmpresa().getId())) {
            throw new ConflictException("Email ja vinculado a empresa.");
        }
        MembresiaEntity membresia = membresiaRepository.findByUsuarioId(usuario.getId())
                .orElse(MembresiaEntity.builder().usuario(usuario).empresa(convite.getEmpresa()).build());
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
        return (int) (membresiaRepository.findByEmpresaId(empresaId).stream().filter(m -> m.getStatus() == StatusMembresia.ACTIVE).count()
                + conviteRepository.findByEmpresaIdAndStatus(empresaId, StatusConviteEmpresa.PENDING).size());
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
        MembresiaEntity membresia = membros.get(0);
        if (!Boolean.TRUE.equals(membresia.getOwner()) || membresia.getStatus() != StatusMembresia.ACTIVE) {
            throw new BusinessException("Usuario sem permissao.");
        }
        return usuario;
    }

    private ConviteEmpresaEntity buscarConvite(Long empresaId, Long conviteId) {
        ConviteEmpresaEntity convite = conviteRepository.findById(conviteId).orElseThrow(() -> new ResourceNotFoundException("Convite nao encontrado."));
        if (!convite.getEmpresa().getId().equals(empresaId)) throw new ResourceNotFoundException("Convite nao encontrado.");
        return convite;
    }

    private MembroEmpresaResponse toResponse(MembresiaEntity membresia) {
        UsuarioEntity usuario = membresia.getUsuario();
        return new MembroEmpresaResponse(membresia.getId(), usuario.getId(), usuario.getNome(), usuario.getEmail(), membresia.getStatus(), membresia.getFuncao(), membresia.getOwner(), membresia.getDataEntrada(), membresia.getDataRemocao(), membresia.getDataCriacao(), membresia.getDataAtualizacao());
    }

    private ConviteEmpresaResponse toResponse(ConviteEmpresaEntity convite) {
        return new ConviteEmpresaResponse(convite.getId(), convite.getEmpresa().getId(), convite.getEmail(), convite.getStatus(), convite.getDataCriacao(), convite.getDataExpiracao(), convite.getDataAceite(), convite.getReenvios(), false);
    }

    private long membroId(MembresiaEntity m) { return m.getId(); }

    private String gerarToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
}
