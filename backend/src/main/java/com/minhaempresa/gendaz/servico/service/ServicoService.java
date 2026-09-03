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
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
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
    private final LogAtividadeService logAtividadeService;
    private final ServicoMapper mapper = new ServicoMapper();

    @Transactional
    public ServicoResponse salvar(SalvarServicoRequest request) {
        Long empresaContexto = CompanyContext.requireCompanyId();
        // Força o empresaId da sessao, ignorando o do request para evitar cross-tenant
        Long empresaIdFinal = empresaContexto;
        
        Map<String, Object> contextoInicio = new LinkedHashMap<>();
        contextoInicio.put("empresaId", empresaIdFinal);
        contextoInicio.put("duracaoMinutos", request.duracaoMinutos());
        contextoInicio.put("valor", request.valor());
        contextoInicio.put("statusPadrao", StatusCadastro.ATIVO);
        log.debug("[servico-debug] inicio criacao servico {}", contextoInicio);
        try {
            EmpresaEntity empresa = empresaService.buscarEntidade(empresaIdFinal);
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
            logAtividadeService.registrar("SERVICO", salvo.getId(), "Criou serviço " + salvo.getNome());
            Map<String, Object> contextoSucesso = new LinkedHashMap<>();
            contextoSucesso.put("servicoId", salvo.getId());
            contextoSucesso.put("empresaId", empresa.getId());
            contextoSucesso.put("duracaoMinutos", salvo.getDuracaoMinutos());
            contextoSucesso.put("valor", salvo.getValor());
            log.info("[servico-debug] servico criado com sucesso {}", contextoSucesso);
            return mapper.toResponse(salvo);
        } catch (Exception e) {
            Map<String, Object> contextoErro = new LinkedHashMap<>();
            contextoErro.put("empresaId", CompanyContext.requireCompanyId());
            contextoErro.put("duracaoMinutos", request.duracaoMinutos());
            contextoErro.put("valor", request.valor());
            log.error("[servico-debug] erro ao criar servico. erroTipo={} contexto={}", e.getClass().getSimpleName(), contextoErro);
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
        validarEmpresa(servico, CompanyContext.requireCompanyId());
        Integer duracao = request.duracaoMinutos() != null ? request.duracaoMinutos() : 30;
        java.math.BigDecimal val = request.valor() != null ? request.valor() : java.math.BigDecimal.ZERO;
        servico.setNome(sanitizacaoService.textoObrigatorio(request.nome()));
        servico.setDescricao(sanitizacaoService.texto(request.descricao()));
        servico.setDuracaoMinutos(duracao);
        servico.setValor(val);
        ServicoResponse response = mapper.toResponse(servicoRepository.save(servico));
        ramoDeteccaoService.sincronizarRamoDaEmpresa(servico.getEmpresa().getId());
        logAtividadeService.registrar("SERVICO", servico.getId(), "Editou serviço " + servico.getNome());
        return response;
    }

    @Transactional
    public ServicoResponse alterarStatus(Long id, StatusCadastro status) {
        ServicoEntity servico = buscarEntidade(id);
        servico.setStatus(status);
        ServicoResponse response = mapper.toResponse(servicoRepository.save(servico));
        ramoDeteccaoService.sincronizarRamoDaEmpresa(servico.getEmpresa().getId());
        if (status == StatusCadastro.ATIVO) {
            logAtividadeService.registrar("SERVICO", servico.getId(), "Ativou serviço " + servico.getNome());
        } else {
            logAtividadeService.registrar("SERVICO", servico.getId(), "Desativou serviço " + servico.getNome());
        }
        return response;
    }

    /**
     * Exclusao de servico sem destruir historico financeiro.
     * - Sem nenhum agendamento vinculado: remocao fisica segura.
     * - Com agendamento/historico: INATIVACAO. Os agendamentos e pagamentos
     *   permanecem intactos (receita, caixa e relatorios preservados); o
     *   servico apenas deixa de estar disponivel para novos agendamentos
     *   (a busca operacional exige ATIVO).
     */
    @Transactional
    public ServicoResponse excluirOuInativar(Long id, Long empresaId) {
        ServicoEntity servico = buscarEntidade(id);
        validarEmpresa(servico, empresaId);
        Long empresaIdResolvido = servico.getEmpresa().getId();
        if (agendamentoRepository.existsByServicoId(id)) {
            servico.setStatus(StatusCadastro.INATIVO);
            ServicoResponse response = mapper.toResponse(servicoRepository.save(servico));
            ramoDeteccaoService.sincronizarRamoDaEmpresa(empresaIdResolvido);
            logAtividadeService.registrar("SERVICO", id, "Inativou serviço " + servico.getNome() + " (possui historico de agendamentos)");
            return response;
        }
        servicoRepository.delete(servico);
        servicoRepository.flush();
        ramoDeteccaoService.sincronizarRamoDaEmpresa(empresaIdResolvido);
        logAtividadeService.registrar("SERVICO", id, "Removeu serviço " + servico.getNome());
        return mapper.toResponse(servico);
    }

    @Transactional(readOnly = true)
    public ServicoEntity buscarEntidade(Long id) {
        ServicoEntity servico = servicoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servico nao encontrado."));
        validarEmpresaAtual(servico.getEmpresa().getId());
        return servico;
    }

    /**
     * Busca operacional para NOVOS agendamentos/alteracoes de servico.
     * Exige servico ATIVO alem de existencia + tenant. Frontend esconder
     * inativo nao e protecao: a regra e server-side.
     * A busca historica (buscarEntidade) continua aceitando INATIVO para
     * nao quebrar a leitura de atendimentos antigos.
     */
    @Transactional(readOnly = true)
    public ServicoEntity buscarEntidadeOperacional(Long id) {
        ServicoEntity servico = buscarEntidade(id);
        if (servico.getStatus() != StatusCadastro.ATIVO) {
            throw new com.minhaempresa.gendaz.shared.BusinessException("Servico indisponivel para novos agendamentos.");
        }
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

