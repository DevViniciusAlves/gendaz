package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insercao e releitura de notificacoes em transacao propria (REQUIRES_NEW).
 *
 * <p>Regra critica: nunca continuar a mesma transacao depois de uma
 * violacao de constraint/flush. Se a insercao viola a UNIQUE
 * (empresa, chave) porque outra transacao venceu a corrida, apenas esta
 * transacao interna sofre rollback; a chamadora permanece utilizavel e
 * rele o registro vencedor em outra transacao propria. Vale para H2 e
 * para PostgreSQL (onde a transacao violada aborta por completo).
 */
@Service
@RequiredArgsConstructor
public class WhatsAppNotificacaoInitializer {

    private final WhatsAppNotificacaoRepository notificacaoRepository;
    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final AgendamentoRepository agendamentoRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WhatsAppNotificacaoEntity inserir(
            Long empresaId,
            WhatsAppTipoNotificacao tipo,
            String idempotencyKey,
            LocalDateTime scheduledAt,
            Long clienteId,
            Long agendamentoId,
            String recipient,
            String messageBody,
            LocalDateTime expiresAt) {
        // Isolamento multiempresa: cliente/agendamento sao validados pelo par
        // (recurso, empresa). Inexistente e de outro tenant falham igual, sem
        // revelar a quem o id pertence.
        ClienteEntity cliente = null;
        if (clienteId != null) {
            cliente = clienteRepository.findByIdAndEmpresaId(clienteId, empresaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado."));
        }
        AgendamentoEntity agendamento = null;
        if (agendamentoId != null) {
            agendamento = agendamentoRepository.findByIdAndEmpresaId(agendamentoId, empresaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agendamento nao encontrado."));
        }
        WhatsAppNotificacaoEntity entidade = WhatsAppNotificacaoEntity.builder()
                .empresa(empresaRepository.getReferenceById(empresaId))
                .tipo(tipo)
                .status(WhatsAppStatusNotificacao.PENDENTE)
                .idempotencyKey(idempotencyKey)
                .scheduledAt(scheduledAt)
                .cliente(cliente)
                .agendamento(agendamento)
                .recipient(recipient)
                .messageBody(messageBody)
                .nextAttemptAt(scheduledAt)
                .expiresAt(expiresAt)
                .build();
        try {
            return notificacaoRepository.saveAndFlush(entidade);
        } catch (DataIntegrityViolationException violacao) {
            throw new WhatsAppNotificacaoDuplicadaException(idempotencyKey);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<WhatsAppNotificacaoEntity> buscar(Long empresaId, String idempotencyKey) {
        return notificacaoRepository.findByEmpresaIdAndIdempotencyKey(empresaId, idempotencyKey);
    }
}
