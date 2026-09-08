package com.minhaempresa.gendaz.whatsapp.repository;

import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppNotificacaoRepository extends JpaRepository<WhatsAppNotificacaoEntity, Long> {
    /**
     * Leitura escopada por tenant: nunca buscar/alterar notificacao de outra
     * empresa apenas pelo id.
     */
    Optional<WhatsAppNotificacaoEntity> findByIdAndEmpresaId(Long id, Long empresaId);

    Optional<WhatsAppNotificacaoEntity> findByEmpresaIdAndIdempotencyKey(Long empresaId, String idempotencyKey);

    List<WhatsAppNotificacaoEntity> findByEmpresaIdAndStatus(Long empresaId, WhatsAppStatusNotificacao status);
}
