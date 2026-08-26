package com.minhaempresa.gendaz.admin.repository;

import com.minhaempresa.gendaz.admin.entity.AdminSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminSessionRepository extends JpaRepository<AdminSessionEntity, Long> {
    Optional<AdminSessionEntity> findByTokenHash(String tokenHash);
}
