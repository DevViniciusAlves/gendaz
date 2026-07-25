package com.minhaempresa.agendapro.profissional.service;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.service.EmpresaService;
import com.minhaempresa.agendapro.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.agendapro.profissional.dto.ProfissionalDtos.SalvarProfissionalRequest;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;
import com.minhaempresa.agendapro.profissional.mapper.ProfissionalMapper;
import com.minhaempresa.agendapro.profissional.repository.ProfissionalRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfissionalService {
    private final ProfissionalRepository profissionalRepository;
    private final EmpresaService empresaService;
    private final SanitizacaoService sanitizacaoService;
    private final ProfissionalMapper mapper = new ProfissionalMapper();

    @Transactional
    public ProfissionalResponse salvar(SalvarProfissionalRequest request) {
        Map<String, Object> contextoInicio = new LinkedHashMap<>();
        contextoInicio.put("empresaId", request.empresaId());
        contextoInicio.put("nome", request.nome());
        contextoInicio.put("especialidade", request.especialidade());
        contextoInicio.put("telefone", request.telefone());
        contextoInicio.put("statusPadrao", StatusCadastro.ATIVO);
        contextoInicio.put("sistema", false);
        log.debug("[profissional-debug] inicio criacao profissional {}", contextoInicio);
        try {
            EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
            ProfissionalEntity profissional = ProfissionalEntity.builder()
                    .nome(sanitizacaoService.textoObrigatorio(request.nome()))
                    .especialidade(sanitizacaoService.texto(request.especialidade()))
                    .telefone(sanitizacaoService.telefone(request.telefone()))
                    .status(StatusCadastro.ATIVO)
                    .empresa(empresa)
                    .build();
            ProfissionalEntity salvo = profissionalRepository.save(profissional);
            Map<String, Object> contextoSucesso = new LinkedHashMap<>();
            contextoSucesso.put("profissionalId", salvo.getId());
            contextoSucesso.put("empresaId", empresa.getId());
            contextoSucesso.put("nome", salvo.getNome());
            contextoSucesso.put("especialidade", salvo.getEspecialidade());
            contextoSucesso.put("telefone", salvo.getTelefone());
            contextoSucesso.put("sistema", salvo.isSistema());
            log.info("[profissional-debug] profissional criado com sucesso {}", contextoSucesso);
            return mapper.toResponse(salvo);
        } catch (Exception e) {
            Map<String, Object> contextoErro = new LinkedHashMap<>();
            contextoErro.put("empresaId", request.empresaId());
            contextoErro.put("nome", request.nome());
            contextoErro.put("especialidade", request.especialidade());
            contextoErro.put("telefone", request.telefone());
            log.error("[profissional-debug] erro ao criar profissional. mensagem='{}' contexto={}", e.getMessage(), contextoErro, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<ProfissionalResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return profissionalRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public ProfissionalResponse atualizar(Long id, SalvarProfissionalRequest request) {
        ProfissionalEntity profissional = buscarEntidade(id);
        validarNaoSistema(profissional);
        profissional.setNome(sanitizacaoService.textoObrigatorio(request.nome()));
        profissional.setEspecialidade(sanitizacaoService.texto(request.especialidade()));
        profissional.setTelefone(sanitizacaoService.telefone(request.telefone()));
        return mapper.toResponse(profissionalRepository.save(profissional));
    }

    @Transactional
    public ProfissionalResponse alterarStatus(Long id, StatusCadastro status) {
        ProfissionalEntity profissional = buscarEntidade(id);
        validarNaoSistema(profissional);
        profissional.setStatus(status);
        return mapper.toResponse(profissionalRepository.save(profissional));
    }

    @Transactional
    public ProfissionalResponse atualizarAdmin(Long id, SalvarProfissionalRequest request) {
        ProfissionalEntity profissional = buscarEntidade(id);
        profissional.setNome(sanitizacaoService.textoObrigatorio(request.nome()));
        profissional.setEspecialidade(sanitizacaoService.texto(request.especialidade()));
        profissional.setTelefone(sanitizacaoService.telefone(request.telefone()));
        return mapper.toResponse(profissionalRepository.save(profissional));
    }

    @Transactional(readOnly = true)
    public ProfissionalEntity buscarEntidade(Long id) {
        ProfissionalEntity profissional = profissionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional nao encontrado."));
        validarEmpresaAtual(profissional.getEmpresa().getId());
        return profissional;
    }

    @Transactional
    public void excluir(Long id, Long empresaId) {
        ProfissionalEntity profissional = buscarEntidade(id);
        validarNaoSistema(profissional);
        if (empresaId != null && !empresaId.equals(profissional.getEmpresa().getId())) {
            throw new BusinessException("Profissional nao pertence a empresa informada.");
        }
        profissionalRepository.delete(profissional);
    }

    @Transactional
    public void excluirAdmin(Long id) {
        ProfissionalEntity profissional = buscarEntidade(id);
        profissionalRepository.delete(profissional);
    }

    @Transactional
    public ProfissionalEntity buscarOuCriarAtendimentoPrincipal(EmpresaEntity empresa) {
        return profissionalRepository.findByEmpresaId(empresa.getId()).stream()
                .filter(profissional -> "Sem preferência".equalsIgnoreCase(profissional.getNome())
                        || "Atendimento principal".equalsIgnoreCase(profissional.getNome()))
                .findFirst()
                .orElseGet(() -> profissionalRepository.save(ProfissionalEntity.builder()
                        .nome("Sem preferência")
                        .especialidade(null)
                        .status(StatusCadastro.ATIVO)
                        .sistema(true)
                        .empresa(empresa)
                        .build()));
    }

    private void validarNaoSistema(ProfissionalEntity profissional) {
        if (profissional.isSistema()) {
            throw new BusinessException("O profissional padrao \"Sem preferencia\" nao pode ser alterado ou excluido.");
        }
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId != null && empresaId != null && !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Profissional nao encontrado.");
        }
    }
}
