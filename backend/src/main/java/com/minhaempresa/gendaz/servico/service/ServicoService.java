package com.minhaempresa.gendaz.servico.service;

import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.empresa.service.RamoDeteccaoService;
import com.minhaempresa.gendaz.servico.dto.ServicoDtos.SalvarServicoRequest;
import com.minhaempresa.gendaz.servico.dto.ServicoDtos.ServicoResponse;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.mapper.ServicoMapper;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
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
public class ServicoService {
    private final ServicoRepository servicoRepository;
    private final EmpresaService empresaService;
    private final RamoDeteccaoService ramoDeteccaoService;
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final SanitizacaoService sanitizacaoService;
    private final ServicoMapper mapper = new ServicoMapper();

    @Transactional
    public ServicoResponse salvar(SalvarServicoRequest request) {
        Map<String, Object> contextoInicio = new LinkedHashMap<>();
        contextoInicio.put("empresaId", request.empresaId());
        contextoInicio.put("nome", request.nome());
        contextoInicio.put("duracaoMinutos", request.duracaoMinutos());
        contextoInicio.put("valor", request.valor());
        contextoInicio.put("statusPadrao", StatusCadastro.ATIVO);
        log.debug("[servico-debug] inicio criacao servico {}", contextoInicio);
        try {
            EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
            Integer duracao = request.duracaoMinutos() != null ? request.duracaoMinutos() : 30;
            java.math.BigDecimal val = request.valor() != null ? request.valor() : java.math.BigDecimal.ZERO;
            ServicoEntity servico = ServicoEntity.builder()
                    .nome(sanitizacaoService.textoObrigatorio(request.nome()))
                    .descricao(sanitizacaoService.texto(request.descricao()))
                    .duracaoMinutos(duracao)
                    .valor(val)
                    .status(StatusCadastro.ATIVO)
                    .empresa(empresa)
                    .build();
            ServicoEntity salvo = servicoRepository.save(servico);
            ramoDeteccaoService.sincronizarRamoDaEmpresa(empresa.getId());
            Map<String, Object> contextoSucesso = new LinkedHashMap<>();
            contextoSucesso.put("servicoId", salvo.getId());
            contextoSucesso.put("empresaId", empresa.getId());
            contextoSucesso.put("nome", salvo.getNome());
            contextoSucesso.put("duracaoMinutos", salvo.getDuracaoMinutos());
            contextoSucesso.put("valor", salvo.getValor());
            log.info("[servico-debug] servico criado com sucesso {}", contextoSucesso);
            return mapper.toResponse(salvo);
        } catch (Exception e) {
            Map<String, Object> contextoErro = new LinkedHashMap<>();
            contextoErro.put("empresaId", request.empresaId());
            contextoErro.put("nome", request.nome());
            contextoErro.put("duracaoMinutos", request.duracaoMinutos());
            contextoErro.put("valor", request.valor());
            log.error("[servico-debug] erro ao criar servico. mensagem='{}' contexto={}", e.getMessage(), contextoErro, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<ServicoResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return servicoRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public ServicoResponse atualizar(Long id, SalvarServicoRequest request) {
        ServicoEntity servico = buscarEntidade(id);
        validarEmpresa(servico, request.empresaId());
        Integer duracao = request.duracaoMinutos() != null ? request.duracaoMinutos() : 30;
        java.math.BigDecimal val = request.valor() != null ? request.valor() : java.math.BigDecimal.ZERO;
        servico.setNome(sanitizacaoService.textoObrigatorio(request.nome()));
        servico.setDescricao(sanitizacaoService.texto(request.descricao()));
        servico.setDuracaoMinutos(duracao);
        servico.setValor(val);
        ServicoResponse response = mapper.toResponse(servicoRepository.save(servico));
        ramoDeteccaoService.sincronizarRamoDaEmpresa(servico.getEmpresa().getId());
        return response;
    }

    @Transactional
    public ServicoResponse alterarStatus(Long id, StatusCadastro status) {
        ServicoEntity servico = buscarEntidade(id);
        servico.setStatus(status);
        ServicoResponse response = mapper.toResponse(servicoRepository.save(servico));
        ramoDeteccaoService.sincronizarRamoDaEmpresa(servico.getEmpresa().getId());
        return response;
    }

    @Transactional
    public ServicoResponse excluirOuInativar(Long id, Long empresaId) {
        ServicoEntity servico = buscarEntidade(id);
        validarEmpresa(servico, empresaId);
        Long empresaIdResolvido = servico.getEmpresa().getId();
        agendamentoRepository.findByServicoId(id).forEach(agendamento -> {
            pagamentoRepository.deleteByAgendamentoId(agendamento.getId());
            agendamentoRepository.delete(agendamento);
        });
        servicoRepository.flush();
        servicoRepository.delete(servico);
        servicoRepository.flush();
        ramoDeteccaoService.sincronizarRamoDaEmpresa(empresaIdResolvido);
        return mapper.toResponse(servico);
    }

    @Transactional(readOnly = true)
    public ServicoEntity buscarEntidade(Long id) {
        ServicoEntity servico = servicoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servico nao encontrado."));
        validarEmpresaAtual(servico.getEmpresa().getId());
        return servico;
    }

    private void validarEmpresa(ServicoEntity servico, Long empresaId) {
        if (empresaId == null || !servico.getEmpresa().getId().equals(empresaId)) {
            throw new ResourceNotFoundException("Servico nao encontrado.");
        }
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long companyId = CompanyContext.requireCompanyId();
        if (empresaId == null || !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Servico nao encontrado.");
        }
    }
}

