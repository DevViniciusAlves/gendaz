package com.minhaempresa.gendaz.auth.repository;

import com.minhaempresa.gendaz.auth.entity.PasswordResetTokenEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {
    Optional<PasswordResetTokenEntity> findByTokenHashAndUsadoFalse(String tokenHash);
    void deleteByUsuarioId(Long usuarioId);
}

