package com.minhaempresa.agendapro.convite.repository;

import com.minhaempresa.agendapro.convite.entity.ConviteEmpresaEntity;
import com.minhaempresa.agendapro.convite.enums.StatusConviteEmpresa;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConviteEmpresaRepository extends JpaRepository<ConviteEmpresaEntity, Long> {
    List<ConviteEmpresaEntity> findByEmpresaId(Long empresaId);
    List<ConviteEmpresaEntity> findByEmpresaIdAndStatus(Long empresaId, StatusConviteEmpresa status);
    Optional<ConviteEmpresaEntity> findByEmpresaIdAndEmailAndStatus(Long empresaId, String email, StatusConviteEmpresa status);
    Optional<ConviteEmpresaEntity> findByTokenHash(String tokenHash);
    List<ConviteEmpresaEntity> findByStatusAndDataExpiracaoBefore(StatusConviteEmpresa status, LocalDateTime agora);
}
