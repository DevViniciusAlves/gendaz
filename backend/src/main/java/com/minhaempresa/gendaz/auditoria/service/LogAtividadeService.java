package com.minhaempresa.gendaz.auditoria.service;

import com.minhaempresa.gendaz.auditoria.dto.LogAtividadeDtos.LogAtividadeResponse;
import com.minhaempresa.gendaz.auditoria.entity.LogAtividadeEntity;
import com.minhaempresa.gendaz.auditoria.repository.LogAtividadeRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.security.ClientIpResolver;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogAtividadeService {

    private final LogAtividadeRepository repository;
    private final UsuarioAutenticadoProvider usuarioProvider;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClientIpResolver ipResolver;

    /**
     * Registra uma ação de negocio de forma assincrona e resiliente.
     * O tenant (empresa) e o usuario responsavel sao SEMPRE resolvidos no servidor,
     * jamais confiando em dados enviados pelo frontend.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void registrar(String entidade, Long entidadeId, String ação) {
        registrar(entidade, entidadeId, ação, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void registrar(String entidade, Long entidadeId, String ação, String detalhes) {
        try {
            Long empresaId = CompanyContext.getCompanyId();
            UsuarioEntity usuario = null;
            try {
                usuario = usuarioProvider.exigirUsuario();
            } catch (Exception ignored) {
            }
            if (empresaId == null && usuario != null && usuario.getEmpresa() != null) {
                empresaId = usuario.getEmpresa().getId();
            }
            if (empresaId == null) {
                return;
            }
            EmpresaEntity empresa = empresaRepository.findById(empresaId).orElse(null);
            if (empresa == null) {
                return;
            }
            UsuarioEntity usuarioGerenciado = usuario != null ? usuarioRepository.findById(usuario.getId()).orElse(null) : null;
            String nomeUsuario = usuarioGerenciado != null ? usuarioGerenciado.getNome() : "Sistema";

            String ip = null;
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    ip = request != null ? ipResolver.resolve(request) : null;
                }
            } catch (Exception ignored) {
            }

            repository.save(LogAtividadeEntity.builder()
                    .empresa(empresa)
                    .usuario(usuarioGerenciado)
                    .nomeUsuario(nomeUsuario)
                    .entidade(entidade)
                    .entidadeId(entidadeId)
                    .ação(ação)
                    .detalhes(detalhes)
                    .ip(ip)
                    .dataHora(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[log-atividade] falha ao registrar atividade. entidade={} ação={} erroTipo={}",
                    entidade, ação, e.getClass().getSimpleName());
        }
    }

    @Transactional(readOnly = true)
    public Page<LogAtividadeResponse> listar(Long empresaId, String entidade, String termo, Pageable pageable) {
        String termoLike = (termo != null && !termo.isBlank()) ? "%" + termo.trim() + "%" : null;
        Page<LogAtividadeEntity> pagina = repository.pesquisar(empresaId, entidade, termoLike, pageable);
        return pagina.map(this::toResponse);
    }

    private LogAtividadeResponse toResponse(LogAtividadeEntity log) {
        return new LogAtividadeResponse(
                log.getId(),
                log.getNomeUsuario(),
                log.getAcao(),
                log.getEntidade(),
                log.getEntidadeId(),
                log.getDetalhes(),
                log.getDataHora()
        );
    }
}
