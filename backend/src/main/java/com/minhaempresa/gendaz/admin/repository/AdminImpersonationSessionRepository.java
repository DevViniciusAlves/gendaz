package com.minhaempresa.gendaz.admin.repository;

import com.minhaempresa.gendaz.admin.entity.AdminImpersonationSessionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminImpersonationSessionRepository extends JpaRepository<AdminImpersonationSessionEntity, Long> {
    Optional<AdminImpersonationSessionEntity> findByIdAndAdminIdAndAtivaTrue(Long id, Long adminId);
}

