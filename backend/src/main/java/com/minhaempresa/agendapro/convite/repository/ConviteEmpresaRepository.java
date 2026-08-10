package com.minhaempresa.agendapro.convite.repository;

import com.minhaempresa.agendapro.convite.entity.ConviteEmpresaEntity;
import com.minhaempresa.agendapro.convite.enums.StatusConviteEmpresa;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConviteEmpresaRepository extends JpaRepository<ConviteEmpresaEntity, Long> {
    List<ConviteEmpresaEntity> findByEmpresaId(Long empresaId);
    List<ConviteEmpresaEntity> findByEmpresaIdAndStatus(Long empresaId, StatusConviteEmpresa status);
    Optional<ConviteEmpresaEntity> findByEmpresaIdAndEmailAndStatus(Long empresaId, String email, StatusConviteEmpresa status);
    Optional<ConviteEmpresaEntity> findByTokenHash(String tokenHash);
    List<ConviteEmpresaEntity> findByStatusAndDataExpiracaoBefore(StatusConviteEmpresa status, LocalDateTime agora);
    void deleteByEmpresaIdAndEmailAndStatus(Long empresaId, String email, StatusConviteEmpresa status);

    @Modifying
    @Query("update ConviteEmpresaEntity c set c.criadoPor = :novoCriador where c.criadoPor.id = :usuarioId")
    void reatribuirCriador(@Param("usuarioId") Long usuarioId, @Param("novoCriador") UsuarioEntity novoCriador);
}
