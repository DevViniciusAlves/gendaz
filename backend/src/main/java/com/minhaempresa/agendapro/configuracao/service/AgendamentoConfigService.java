package com.minhaempresa.agendapro.configuracao.service;

import com.minhaempresa.agendapro.configuracao.dto.AgendamentoConfigDtos.AgendamentoLinkResponse;
import com.minhaempresa.agendapro.configuracao.dto.AgendamentoConfigDtos.AtualizarAgendamentoSlugRequest;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgendamentoConfigService {
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;

    @Value("${app.frontend-url:https://gendaz.site}")
    private String frontendUrl;

    @Transactional
    public AgendamentoLinkResponse obterLink(Long usuarioId) {
        EmpresaEntity empresa = buscarEmpresaDoUsuario(usuarioId);
        if (empresa.getAgendamentoSlug() == null || empresa.getAgendamentoSlug().isBlank()) {
            empresa.setAgendamentoSlug(gerarSlugUnico(empresa));
            empresa = empresaRepository.save(empresa);
        }
        return toResponse(empresa);
    }

    @Transactional
    public AgendamentoLinkResponse atualizarSlug(Long usuarioId, AtualizarAgendamentoSlugRequest request) {
        EmpresaEntity empresa = buscarEmpresaDoUsuario(usuarioId);
        String slug = normalizarSlug(request.slug());
        if (slug.isBlank()) {
            throw new BusinessException("Informe um slug valido.");
        }
        empresaRepository.findByAgendamentoSlug(slug)
                .filter(existente -> !existente.getId().equals(empresa.getId()))
                .ifPresent(existente -> {
                    throw new BusinessException("Este link de agendamento ja esta em uso.");
                });
        empresa.setAgendamentoSlug(slug);
        return toResponse(empresaRepository.save(empresa));
    }

    private EmpresaEntity buscarEmpresaDoUsuario(Long usuarioId) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado."));
        if (usuario.getEmpresa() == null) {
            throw new BusinessException("Usuario sem empresa nao possui link de agendamento.");
        }
        return usuario.getEmpresa();
    }

    private AgendamentoLinkResponse toResponse(EmpresaEntity empresa) {
        return new AgendamentoLinkResponse(
                empresa.getId(),
                empresa.getAgendamentoSlug(),
                baseFrontendUrl() + "/agendar/" + empresa.getAgendamentoSlug()
        );
    }

    private String gerarSlugUnico(EmpresaEntity empresa) {
        String base = normalizarSlug(empresa.getNomeFantasia());
        if (base.isBlank()) {
            base = "empresa-" + empresa.getId();
        }
        String slug = base;
        int tentativa = 1;
        while (empresaRepository.existsByAgendamentoSlug(slug)) {
            slug = base + "-" + empresa.getId() + (tentativa > 1 ? "-" + tentativa : "");
            tentativa++;
        }
        return slug;
    }

    private String normalizarSlug(String valor) {
        if (valor == null) return "";
        String normalized = java.text.Normalizer.normalize(valor.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.length() > 80 ? normalized.substring(0, 80).replaceAll("-$", "") : normalized;
    }

    private String baseFrontendUrl() {
        String base = frontendUrl == null || frontendUrl.isBlank() ? "https://gendaz.site" : frontendUrl.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
