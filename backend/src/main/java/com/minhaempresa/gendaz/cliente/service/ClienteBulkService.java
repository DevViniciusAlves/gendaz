package com.minhaempresa.gendaz.cliente.service;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.AcaoEmMassaClienteRequest;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.FalhaAcaoItem;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.conversa.entity.ConversaEntity;
import com.minhaempresa.gendaz.conversa.repository.ConversaRepository;
import com.minhaempresa.gendaz.crm.repository.CrmContatoRepository;
import com.minhaempresa.gendaz.entrega.repository.EntregaRepository;
import com.minhaempresa.gendaz.mensagem.repository.MensagemRepository;
import com.minhaempresa.gendaz.notafiscal.repository.NotaFiscalRepository;
import com.minhaempresa.gendaz.notificacao.repository.NotificacaoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClienteBulkService {
    private final ClienteRepository clienteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ConversaRepository conversaRepository;
    private final CrmContatoRepository crmContatoRepository;
    private final MensagemRepository mensagemRepository;
    private final EntregaRepository entregaRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final NotaFiscalRepository notaFiscalRepository;
    private final AdminAuditService auditService;

    @Transactional
    public AcaoEmMassaResponse excluir(AcaoEmMassaClienteRequest request) {
        validarQuantidade(request.ids());
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            companyId = request.empresaId();
        }
        if (companyId == null) {
            throw new BusinessException("Empresa logada nao encontrada.");
        }
        Set<Long> idsUnicos = new HashSet<>(request.ids());
        List<FalhaAcaoItem> falhas = new ArrayList<>();
        int processados = 0;
        for (Long id : idsUnicos) {
            try {
                ClienteEntity cliente = clienteRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado."));
                if (!cliente.getEmpresa().getId().equals(companyId)) {
                    throw new ResourceNotFoundException("Cliente nao encontrado.");
                }

                cliente.setStatus(StatusCadastro.INATIVO);
                clienteRepository.save(cliente);
                auditService.registrar("CLIENTE_STATUS_ALTERADO", "INFO", null, null, cliente.getEmpresa(), "Cliente desativado em massa", cliente.getNome(), null, null);
                processados++;
            } catch (RuntimeException ex) {
                falhas.add(new FalhaAcaoItem(id, ex.getMessage()));
            }
        }
        return new AcaoEmMassaResponse(request.ids().size(), processados, falhas);
    }

    private void validarQuantidade(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("Selecione pelo menos um item.");
        }
        if (ids.size() > 10) {
            throw new BusinessException("VocÃª pode selecionar no mÃ¡ximo 10 itens por vez.");
        }
    }
}

