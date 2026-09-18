package com.minhaempresa.gendaz.whatsapp.repository;

import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppConfiguracaoEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppConfiguracaoRepository extends JpaRepository<WhatsAppConfiguracaoEntity, Long> {
    Optional<WhatsAppConfiguracaoEntity> findByEmpresaId(Long empresaId);
}
