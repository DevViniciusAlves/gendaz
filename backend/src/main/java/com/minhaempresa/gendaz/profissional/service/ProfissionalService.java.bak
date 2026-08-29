package com.minhaempresa.gendaz.profissional.service;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.gendaz.profissional.dto.ProfissionalDtos.SalvarProfissionalRequest;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.profissional.mapper.ProfissionalMapper;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final LogAtividadeService logAtividadeService;
    private final ProfissionalMapper mapper = new ProfissionalMapper();

    @Transactional
    public ProfissionalResponse salvar(SalvarProfissionalRequest request) {
        Map<String, Object> contextoInicio = new LinkedHashMap<>();
        contextoInicio.put("empresaId", request.empresaId());
        contextoInicio.put("statusPadrao", StatusCadastro.ATIVO);
        contextoInicio.put("sistema", false);
        contextoInicio.put("diasTrabalho", request.diasTrabalho());

        log.debug("[profissional-debug] inicio criacao profissional {}", contextoInicio);
        try {
            EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
            Set<DiaSemana> diasTrabalho = normalizarDiasTrabalho(request.diasTrabalho(), StatusCadastro.ATIVO);
            ProfissionalEntity profissional = ProfissionalEntity.builder()
                    .nome(sanitizacaoService.textoObrigatorio(request.nome()))
                    .especialidade(sanitizacaoService.texto(request.especialidade()))
                    .telefone(sanitizacaoService.telefone(request.telefone()))
                    .status(StatusCadastro.ATIVO)
                    .diasTrabalho(diasTrabalho)
                    .empresa(empresa)
                    .build();
            ProfissionalEntity salvo = profissionalRepository.save(profissional);
            logAtividadeService.registrar("PROFISSIONAL", salvo.getId(), "Criou profissional " + salvo.getNome());
            Map<String, Object> contextoSucesso = new LinkedHashMap<>();
            contextoSucesso.put("profissionalId", salvo.getId());
            contextoSucesso.put("empresaId", empresa.getId());
            contextoSucesso.put("sistema", salvo.isSistema());
            log.info("[profissional-debug] profissional criado com sucesso {}", contextoSucesso);
            return mapper.toResponse(salvo);
        } catch (Exception e) {
            Map<String, Object> contextoErro = new LinkedHashMap<>();
            contextoErro.put("empresaId", request.empresaId());
            log.error("[profissional-debug] erro ao criar profissional. erroTipo={} contexto={}", e.getClass().getSimpleName(), contextoErro);
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
        profissional.setDiasTrabalho(normalizarDiasTrabalho(request.diasTrabalho(), profissional.getStatus()));
        ProfissionalResponse response = mapper.toResponse(profissionalRepository.save(profissional));
        logAtividadeService.registrar("PROFISSIONAL", profissional.getId(), "Editou profissional " + profissional.getNome());
        return response;
    }

    @Transactional
    public ProfissionalResponse alterarStatus(Long id, StatusCadastro status) {
        ProfissionalEntity profissional = buscarEntidade(id);
        validarNaoSistema(profissional);
        if (status == StatusCadastro.ATIVO && diasEfetivos(profissional).isEmpty()) {
            throw new BusinessException("Selecione pelo menos um dia de trabalho.");
        }
        profissional.setStatus(status);
        ProfissionalResponse response = mapper.toResponse(profissionalRepository.save(profissional));
        if (status == StatusCadastro.ATIVO) {
            logAtividadeService.registrar("PROFISSIONAL", profissional.getId(), "Ativou profissional " + profissional.getNome());
        } else {
            logAtividadeService.registrar("PROFISSIONAL", profissional.getId(), "Desativou profissional " + profissional.getNome());
        }
        return response;
    }

    @Transactional
    public ProfissionalResponse atualizarAdmin(Long id, SalvarProfissionalRequest request) {
        ProfissionalEntity profissional = buscarEntidade(id);
        profissional.setNome(sanitizacaoService.textoObrigatorio(request.nome()));
        profissional.setEspecialidade(sanitizacaoService.texto(request.especialidade()));
        profissional.setTelefone(sanitizacaoService.telefone(request.telefone()));
        profissional.setDiasTrabalho(normalizarDiasTrabalho(request.diasTrabalho(), profissional.getStatus()));
        ProfissionalResponse response = mapper.toResponse(profissionalRepository.save(profissional));
        logAtividadeService.registrar("PROFISSIONAL", profissional.getId(), "Editou profissional " + profissional.getNome());
        return response;
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
        logAtividadeService.registrar("PROFISSIONAL", id, "Removeu profissional " + profissional.getNome());
    }

    @Transactional
    public void excluirAdmin(Long id) {
        ProfissionalEntity profissional = buscarEntidade(id);
        profissionalRepository.delete(profissional);
        logAtividadeService.registrar("PROFISSIONAL", id, "Removeu profissional " + profissional.getNome());
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
                        .diasTrabalho(todosDias())
                        .empresa(empresa)
                        .build()));
    }

    public boolean trabalhaNoDia(ProfissionalEntity profissional, LocalDate data) {
        if (profissional == null || data == null) {
            return false;
        }
        return diasEfetivos(profissional).contains(DiaSemana.from(data.getDayOfWeek()));
    }

    public void validarTrabalhoNoDia(ProfissionalEntity profissional, LocalDate data) {
        if (!trabalhaNoDia(profissional, data)) {
            throw new BusinessException("Este profissional nao atende no dia selecionado.");
        }
    }

    private Set<DiaSemana> normalizarDiasTrabalho(Set<DiaSemana> dias, StatusCadastro status) {
        Set<DiaSemana> normalizados = dias == null ? todosDias() : new LinkedHashSet<>(dias);
        if (status == StatusCadastro.ATIVO && normalizados.isEmpty()) {
            throw new BusinessException("Selecione pelo menos um dia de trabalho.");
        }
        return normalizados;
    }

    private Set<DiaSemana> diasEfetivos(ProfissionalEntity profissional) {
        if (profissional.getDiasTrabalho() == null || profissional.getDiasTrabalho().isEmpty()) {
            return profissional.isSistema() ? todosDias() : Set.of();
        }
        return profissional.getDiasTrabalho();
    }

    private Set<DiaSemana> todosDias() {
        return new LinkedHashSet<>(EnumSet.allOf(DiaSemana.class));
    }

    private void validarNaoSistema(ProfissionalEntity profissional) {
        if (profissional.isSistema()) {
            throw new BusinessException("O profissional padrao \"Sem preferencia\" nao pode ser alterado ou excluido.");
        }
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.requireCompanyId();
        if (empresaId == null || !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Profissional nao encontrado.");
        }
    }
}

