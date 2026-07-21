package com.minhaempresa.agendapro.cliente.service;

import com.minhaempresa.agendapro.admin.service.AdminAuditService;
import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.AcaoEmMassaClienteRequest;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.AcaoEmMassaResponse;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.FalhaAcaoItem;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.conversa.entity.ConversaEntity;
import com.minhaempresa.agendapro.conversa.repository.ConversaRepository;
import com.minhaempresa.agendapro.entrega.repository.EntregaRepository;
import com.minhaempresa.agendapro.mensagem.repository.MensagemRepository;
import com.minhaempresa.agendapro.notafiscal.repository.NotaFiscalRepository;
import com.minhaempresa.agendapro.notificacao.repository.NotificacaoRepository;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
//  DESATIVADO — import com.minhaempresa.agendapro.whatsapp.repository.WhatsappLembretePagamentoRepository;
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
    private final MensagemRepository mensagemRepository;
    private final EntregaRepository entregaRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final NotaFiscalRepository notaFiscalRepository;
    //  DESATIVADO — private final WhatsappLembretePagamentoRepository lembretePagamentoRepository;
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

                for (AgendamentoEntity agendamento : agendamentoRepository.findByClienteId(id)) {
                    //  DESATIVADO — lembretePagamentoRepository.deleteByAgendamento_Id(agendamento.getId());
                    pagamentoRepository.deleteByAgendamentoId(agendamento.getId());
                    agendamentoRepository.delete(agendamento);
                }

                for (ConversaEntity conversa : conversaRepository.findByClienteId(id)) {
                    mensagemRepository.deleteByConversaId(conversa.getId());
                    conversaRepository.delete(conversa);
                }

                entregaRepository.deleteByClienteId(id);
                notificacaoRepository.deleteByClienteId(id);
                notaFiscalRepository.deleteByClienteId(id);
                pagamentoRepository.deleteByClienteId(id);
                clienteRepository.delete(cliente);
                auditService.registrar("CLIENTE_EXCLUIDO", "WARN", null, null, cliente.getEmpresa(), "Cliente excluido em massa", cliente.getNome(), null, null);
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
            throw new BusinessException("Você pode selecionar no máximo 10 itens por vez.");
        }
    }
}
