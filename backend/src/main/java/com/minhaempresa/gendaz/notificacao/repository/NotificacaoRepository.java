package com.minhaempresa.gendaz.notificacao.repository;

import com.minhaempresa.gendaz.notificacao.entity.NotificacaoEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<NotificacaoEntity, Long> {
    List<NotificacaoEntity> findByEmpresaId(Long empresaId);

    void deleteByClienteId(Long clienteId);
}

