package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criacao idempotente de notificacoes WhatsApp. A unicidade de
 * (empresa, idempotency_key) e garantida pela constraint do banco, nao por
 * verificacao previa em memoria: sob concorrencia, a segunda transacao
 * viola a constraint e recupera o registro existente em nova transacao
 * (seguro no PostgreSQL, onde a transacao violada nao pode continuar).
 */
@Service
@RequiredArgsConstructor
public class WhatsAppNotificacaoService {

    private final WhatsAppNotificacaoRepository notificacaoRepository;
    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final AgendamentoRepository agendamentoRepository;

    /**
     * Cria ou recupera a notificacao para (empresa, chave). Nunca cria
     * duplicata; nunca falha em caso de corrida: a vencedora insere e a
     * perdedora recebe o registro existente.
     */
    public WhatsAppNotificacaoEntity criarIdempotente(
            Long empresaId,
            WhatsAppTipoNotificacao tipo,
            String idempotencyKey,
            LocalDateTime scheduledAt,
            Long clienteId,
            Long agendamentoId) {
        Optional<WhatsAppNotificacaoEntity> existente =
                notificacaoRepository.findByEmpresaIdAndIdempotencyKey(empresaId, idempotencyKey);
        if (existente.isPresent()) {
            return existente.get();
        }
        try {
            return criar(empresaId, tipo, idempotencyKey, scheduledAt, clienteId, agendamentoId);
        } catch (WhatsAppNotificacaoDuplicadaException duplicada) {
            return notificacaoRepository.findByEmpresaIdAndIdempotencyKey(empresaId, idempotencyKey)
                    .orElseThrow(() -> duplicada);
        }
    }

    @Transactional
    public WhatsAppNotificacaoEntity criar(
            Long empresaId,
            WhatsAppTipoNotificacao tipo,
            String idempotencyKey,
            LocalDateTime scheduledAt,
            Long clienteId,
            Long agendamentoId) {
        WhatsAppNotificacaoEntity entidade = WhatsAppNotificacaoEntity.builder()
                .empresa(empresaRepository.getReferenceById(empresaId))
                .tipo(tipo)
                .status(WhatsAppStatusNotificacao.PENDENTE)
                .idempotencyKey(idempotencyKey)
                .scheduledAt(scheduledAt)
                .cliente(clienteId == null ? null : clienteRepository.getReferenceById(clienteId))
                .agendamento(agendamentoId == null ? null : agendamentoRepository.getReferenceById(agendamentoId))
                .build();
        try {
            return notificacaoRepository.saveAndFlush(entidade);
        } catch (DataIntegrityViolationException violacao) {
            throw new WhatsAppNotificacaoDuplicadaException(idempotencyKey);
        }
    }

    @Transactional(readOnly = true)
    public Optional<WhatsAppNotificacaoEntity> buscarPorId(Long empresaId, Long notificacaoId) {
        return notificacaoRepository.findByIdAndEmpresaId(notificacaoId, empresaId);
    }
}
