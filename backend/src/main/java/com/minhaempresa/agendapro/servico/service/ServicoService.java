package com.minhaempresa.agendapro.servico.service;

import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.service.EmpresaService;
import com.minhaempresa.agendapro.servico.dto.ServicoDtos.SalvarServicoRequest;
import com.minhaempresa.agendapro.servico.dto.ServicoDtos.ServicoResponse;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import com.minhaempresa.agendapro.servico.mapper.ServicoMapper;
import com.minhaempresa.agendapro.servico.repository.ServicoRepository;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServicoService {
    private final ServicoRepository servicoRepository;
    private final EmpresaService empresaService;
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final SanitizacaoService sanitizacaoService;
    private final ServicoMapper mapper = new ServicoMapper();

    @Transactional
    public ServicoResponse salvar(SalvarServicoRequest request) {
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
        return mapper.toResponse(servicoRepository.save(servico));
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
        return mapper.toResponse(servicoRepository.save(servico));
    }

    @Transactional
    public ServicoResponse alterarStatus(Long id, StatusCadastro status) {
        ServicoEntity servico = buscarEntidade(id);
        servico.setStatus(status);
        return mapper.toResponse(servicoRepository.save(servico));
    }

    @Transactional
    public ServicoResponse excluirOuInativar(Long id, Long empresaId) {
        ServicoEntity servico = buscarEntidade(id);
        validarEmpresa(servico, empresaId);
        agendamentoRepository.findByServicoId(id).forEach(agendamento -> {
            pagamentoRepository.deleteByAgendamentoId(agendamento.getId());
            agendamentoRepository.delete(agendamento);
        });
        servicoRepository.flush();
        servicoRepository.delete(servico);
        servicoRepository.flush();
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
        Long companyId = CompanyContext.getCompanyId();
        if (companyId != null && empresaId != null && !companyId.equals(empresaId)) {
            throw new ResourceNotFoundException("Servico nao encontrado.");
        }
    }
}
