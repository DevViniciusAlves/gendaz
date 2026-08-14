package com.minhaempresa.gendaz.mensagem.repository;

import com.minhaempresa.gendaz.mensagem.entity.MensagemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface MensagemRepository extends JpaRepository<MensagemEntity, Long> {
    @EntityGraph(attributePaths = {"conversa"})
    List<MensagemEntity> findByConversaIdAndConversaEmpresaIdOrderByDataEnvioAsc(Long conversaId, Long empresaId);

    @EntityGraph(attributePaths = {"conversa"})
    List<MensagemEntity> findByConversaIdOrderByDataEnvioAsc(Long conversaId);

    @Transactional
    @Modifying
    void deleteByConversaId(Long conversaId);
}

