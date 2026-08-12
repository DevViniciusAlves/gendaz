package com.minhaempresa.gendaz.admin.repository;

import com.minhaempresa.gendaz.admin.entity.AdminImpersonationSessionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminImpersonationSessionRepository extends JpaRepository<AdminImpersonationSessionEntity, Long> {
    Optional<AdminImpersonationSessionEntity> findBySessionTokenHashAndStatus(String hash, String status);
    List<AdminImpersonationSessionEntity> findByAdminUsuarioIdAndStatus(Long adminUsuarioId, String status);
    Optional<AdminImpersonationSessionEntity> findByIdAndAdminUsuarioIdAndStatus(Long id, Long adminUsuarioId, String status);
}
