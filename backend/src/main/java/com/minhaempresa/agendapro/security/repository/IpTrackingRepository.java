package com.minhaempresa.agendapro.security.repository;

import com.minhaempresa.agendapro.security.entity.IpTrackingEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IpTrackingRepository extends JpaRepository<IpTrackingEntity, Long> {

    Optional<IpTrackingEntity> findByIpAddress(String ipAddress);

    @Query("SELECT i FROM IpTrackingEntity i WHERE i.bloqueado = true AND i.bloqueadoAte > :agora")
    List<IpTrackingEntity> findIpsBloqueados(@Param("agora") LocalDateTime agora);

    @Query("SELECT i FROM IpTrackingEntity i WHERE i.tentativasFalhadas >= 10 AND i.ultimoAcesso > :desde")
    List<IpTrackingEntity> findIpsSuspeitos(@Param("desde") LocalDateTime desde);

    @Modifying
    @Query("DELETE FROM IpTrackingEntity i WHERE i.ultimoAcesso < :data")
    int deleteByUltimoAcessoBefore(@Param("data") LocalDateTime data);
}
