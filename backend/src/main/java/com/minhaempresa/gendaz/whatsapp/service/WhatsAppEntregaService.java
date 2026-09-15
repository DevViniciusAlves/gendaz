package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppEntregaReceiptEntity;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppEntregaReceiptRepository;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import jakarta.persistence.OptimisticLockException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmacao de entrega WhatsApp (DELIVERY_ACK do Baileys).
 *
 * <p>Regra de negocio: a mensagem CRM/lembrete somente consome a cota
 * (reservado -&gt; enviado) quando houver prova de entrega. {@code sendMessage}
 * + {@code messageId} significa apenas "aceito pelo provider / aguardando
 * confirmacao" (AGUARDANDO_ENTREGA, reserva mantida).
 *
 * <p>Idempotencia: DELIVERY_ACK/READ/PLAYED resultam em NO MAXIMO uma
 * confirmacao por mensagem (par empresa + providerMessageId, lock pessimista
 * na notificacao). Notificacao ja ENVIADO + callback repetido = no-op.
 *
 * <p>Corrida callback-antes-do-providerMessageId: o receipt e persistido
 * ANTES de tentar localizar a notificacao, e o worker reconcilia receipts
 * pendentes ao mover para AGUARDANDO_ENTREGA. Nada critico vive so na
 * memoria do Node (o Render pode reiniciar).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppEntregaService {

    public enum ResultadoEntrega {
        /** AGUARDANDO_ENTREGA -&gt; ENVIADO, cota convertida exatamente uma vez. */
        CONFIRMADO,
        /** Ja estava ENVIADO (callback duplicado/replay apos reconexao). */
        JA_CONFIRMADO,
        /** Receipt guardado, notificacao ainda sem providerMessageId (corrida). */
        AGUARDANDO_NOTIFICACAO,
        /** Sem notificacao nem condicao de confirmar (ex.: FALHOU/CANCELADO). */
        IGNORADO,
        /** Empresa desconhecida ou payload invalido. */
        INVALIDO
    }

    private final WhatsAppNotificacaoRepository notificacaoRepository;
    private final WhatsAppEntregaReceiptRepository receiptRepository;
    private final WhatsAppQuotaService quotaService;
    private final EmpresaRepository empresaRepository;

    private WhatsAppEntregaService self;

    @Autowired
    public void setSelf(@Lazy WhatsAppEntregaService self) {
        this.self = self;
    }

    /**
     * Entrada do callback interno Node -&gt; Spring. Nunca lanca por
     * corrida: persiste o receipt primeiro (durable) e confirma em seguida;
     * derrota em lock vira verificacao do estado vencedor.
     */
    public ResultadoEntrega registrarEntrega(Long empresaId, String providerMessageId) {
        if (empresaId == null || providerMessageId == null || providerMessageId.isBlank()) {
            return ResultadoEntrega.INVALIDO;
        }
        if (!empresaRepository.existsById(empresaId)) {
            log.warn("[whatsapp-entrega] empresa desconhecida");
            return ResultadoEntrega.INVALIDO;
        }
        try {
            self.persistirReceipt(empresaId, providerMessageId);
        } catch (Exception e) {
            log.error("[whatsapp-entrega] falha ao persistir receipt. erroTipo={}",
                    e.getClass().getSimpleName());
            return ResultadoEntrega.IGNORADO;
        }
        try {
            return self.confirmarEntrega(empresaId, providerMessageId);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException corrida) {
            return self.verificarAposConflito(empresaId, providerMessageId);
        } catch (Exception e) {
            log.error("[whatsapp-entrega] falha ao confirmar. erroTipo={}", e.getClass().getSimpleName());
            return ResultadoEntrega.IGNORADO;
        }
    }

    /**
     * Reconciliacao chamada pelo worker DENTRO da mesma transacao que moveu
     * para AGUARDANDO_ENTREGA (propagation padrao = junta-se a chamadora):
     * se o ACK chegou antes (corrida) e o receipt ja esta commitado, confirma
     * de imediato em vez de esperar um callback que nunca sera repetido.
     *
     * <p>Precisa rodar na mesma transacao porque a linha ainda nao commitou o
     * providerMessageId — um REQUIRES_NEW aqui nao enxergaria a mudanca.
     */
    @Transactional
    public void reconciliarNaMesmaTransacao(WhatsAppNotificacaoEntity entidade) {
        if (entidade == null || entidade.getEmpresa() == null
                || entidade.getProviderMessageId() == null
                || entidade.getProviderMessageId().isBlank()) {
            return;
        }
        Long empresaId = entidade.getEmpresa().getId();
        String providerMessageId = entidade.getProviderMessageId();
        Optional<WhatsAppEntregaReceiptEntity> receipt =
                receiptRepository.findByEmpresaIdAndProviderMessageIdForUpdate(empresaId, providerMessageId);
        if (receipt.isEmpty() || receipt.get().isConsumed()) {
            return;
        }
        if (entidade.getStatus() != WhatsAppStatusNotificacao.AGUARDANDO_ENTREGA) {
            return;
        }
        entidade.setStatus(WhatsAppStatusNotificacao.ENVIADO);
        entidade.setSentAt(java.time.LocalDateTime.now());
        entidade.setLastError(null);
        if (entidade.isQuotaReserved()) {
            quotaService.confirmarEnvioNoCiclo(
                    empresaId,
                    entidade.getTipo().categoria(),
                    entidade.getQuotaCycleStart());
            entidade.setQuotaReserved(false);
        }
        notificacaoRepository.save(entidade);
        receipt.get().setConsumed(true);
        receiptRepository.save(receipt.get());
        log.info("[whatsapp-entrega] corrida reconciliada notificacao={} transition=AGUARDANDO_ENTREGA->ENVIADO",
                entidade.getId());
    }

    /**
     * Reconciliacao externa (fora do worker): tenta confirmar um receipt
     * pendente apos a notificacao ter recebido o providerMessageId.
     */
    public void reconciliarAposEnvio(Long empresaId, String providerMessageId) {
        if (empresaId == null || providerMessageId == null || providerMessageId.isBlank()) {
            return;
        }
        try {
            self.confirmarEntrega(empresaId, providerMessageId);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException corrida) {
            self.verificarAposConflito(empresaId, providerMessageId);
        } catch (Exception e) {
            // Receipt pendente continua valido: um callback futuro ou uma
            // reconciliacao posterior ainda pode confirmar. Nunca derruba o envio.
            log.warn("[whatsapp-entrega] reconciliacao adiada. erroTipo={}", e.getClass().getSimpleName());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistirReceipt(Long empresaId, String providerMessageId) {
        Optional<WhatsAppEntregaReceiptEntity> atual =
                receiptRepository.findByEmpresaIdAndProviderMessageIdForUpdate(empresaId, providerMessageId);
        if (atual.isPresent()) {
            return; // upsert idempotente: replay de ACK nao duplica receipt.
        }
        try {
            receiptRepository.save(WhatsAppEntregaReceiptEntity.builder()
                    .empresa(empresaRepository.getReferenceById(empresaId))
                    .providerMessageId(providerMessageId)
                    .status("DELIVERED")
                    .consumed(false)
                    .build());
            receiptRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException corrida) {
            // Callback simultaneo venceu e inseriu primeiro: sem duplicata.
            log.info("[whatsapp-entrega] receipt em corrida ja existente duplicate=true");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoEntrega confirmarEntrega(Long empresaId, String providerMessageId) {
        Optional<WhatsAppNotificacaoEntity> atual =
                notificacaoRepository.findByEmpresaIdAndProviderMessageIdForUpdate(empresaId, providerMessageId);
        if (atual.isEmpty()) {
            // Corrida: worker ainda nao persistiu o providerMessageId. O
            // receipt ja esta gravado; a reconciliacao pos-envio confirma.
            log.info("[whatsapp-entrega] notificacao ainda sem providerMessageId messageIdPresent=true");
            return ResultadoEntrega.AGUARDANDO_NOTIFICACAO;
        }
        WhatsAppNotificacaoEntity entidade = atual.get();
        if (entidade.getStatus() == WhatsAppStatusNotificacao.ENVIADO) {
            marcarConsumido(empresaId, providerMessageId);
            log.info("[whatsapp-entrega] callback duplicado no-op notificacao={} duplicate=true",
                    entidade.getId());
            return ResultadoEntrega.JA_CONFIRMADO;
        }
        if (entidade.getStatus() != WhatsAppStatusNotificacao.AGUARDANDO_ENTREGA) {
            // PENDENTE/ENVIANDO/FALHOU/CANCELADO: nunca consumir cota por ACK.
            log.info("[whatsapp-entrega] ACK sem efeito estado={} notificacao={}",
                    entidade.getStatus(), entidade.getId());
            return ResultadoEntrega.IGNORADO;
        }
        entidade.setStatus(WhatsAppStatusNotificacao.ENVIADO);
        entidade.setSentAt(java.time.LocalDateTime.now());
        entidade.setLastError(null);
        if (entidade.isQuotaReserved()) {
            quotaService.confirmarEnvioNoCiclo(
                    entidade.getEmpresa().getId(),
                    entidade.getTipo().categoria(),
                    entidade.getQuotaCycleStart());
            entidade.setQuotaReserved(false);
        }
        notificacaoRepository.save(entidade);
        marcarConsumido(empresaId, providerMessageId);
        log.info("[whatsapp-entrega] entrega confirmada notificacao={} transition=AGUARDANDO_ENTREGA->ENVIADO",
                entidade.getId());
        return ResultadoEntrega.CONFIRMADO;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoEntrega verificarAposConflito(Long empresaId, String providerMessageId) {
        Optional<WhatsAppNotificacaoEntity> atual =
                notificacaoRepository.findByEmpresaIdAndProviderMessageId(empresaId, providerMessageId);
        if (atual.isEmpty()) {
            return ResultadoEntrega.AGUARDANDO_NOTIFICACAO;
        }
        WhatsAppStatusNotificacao estado = atual.get().getStatus();
        if (estado == WhatsAppStatusNotificacao.ENVIADO) {
            return ResultadoEntrega.JA_CONFIRMADO;
        }
        if (estado == WhatsAppStatusNotificacao.AGUARDANDO_ENTREGA) {
            try {
                return self.confirmarEntrega(empresaId, providerMessageId);
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException segunda) {
                log.error("[whatsapp-entrega] confirmacao apos conflito abortada messageIdPresent=true");
                return ResultadoEntrega.IGNORADO;
            }
        }
        return ResultadoEntrega.IGNORADO;
    }

    private void marcarConsumido(Long empresaId, String providerMessageId) {
        receiptRepository.findByEmpresaIdAndProviderMessageId(empresaId, providerMessageId)
                .ifPresent(receipt -> {
                    if (!receipt.isConsumed()) {
                        receipt.setConsumed(true);
                        receiptRepository.save(receipt);
                    }
                });
    }
}
