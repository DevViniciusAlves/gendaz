package com.minhaempresa.gendaz.crm.service;

import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.crm.entity.CrmContatoEntity;
import com.minhaempresa.gendaz.crm.repository.CrmContatoRepository;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro do historico CRM vinculado a notificacao WhatsApp, em
 * transacao propria (REQUIRES_NEW).
 *
 * <p>A UNIQUE (whatsapp_notificacao_id) garante no banco: uma notificacao,
 * no maximo um registro de historico. Em corrida, a perdedora viola a
 * constraint somente nesta transacao interna (rollback isolado); a
 * chamadora verifica em outra transacao propria se o registro ja existe:
 * se sim, a duplicata era benigna e segue; se nao, o erro e real e
 * propaga. Nunca continua a mesma transacao apos DataIntegrityViolation.
 * E-mail e historico antigo (vinculo nulo) nao sao afetados.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrmContatoHistoricoService {

    private final CrmContatoRepository crmContatoRepository;
    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final WhatsAppNotificacaoRepository notificacaoRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CrmContatoEntity registrarWhatsapp(
            Long empresaId,
            Long clienteId,
            String template,
            String texto,
            Long notificacaoId) {
        CrmContatoEntity contato = CrmContatoEntity.builder()
                .empresa(empresaRepository.getReferenceById(empresaId))
                .cliente(clienteRepository.getReferenceById(clienteId))
                .tipo("whatsapp")
                .template(template)
                .mensagem(texto)
                .status("solicitado")
                .whatsappNotificacao(notificacaoRepository.getReferenceById(notificacaoId))
                .build();
        return crmContatoRepository.saveAndFlush(contato);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<CrmContatoEntity> buscarPorNotificacao(Long notificacaoId) {
        return crmContatoRepository.findByWhatsappNotificacaoId(notificacaoId);
    }
}
