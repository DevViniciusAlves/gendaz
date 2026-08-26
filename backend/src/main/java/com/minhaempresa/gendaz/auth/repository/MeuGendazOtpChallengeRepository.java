package com.minhaempresa.gendaz.auth.repository;

import com.minhaempresa.gendaz.auth.entity.MeuGendazOtpChallengeEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeuGendazOtpChallengeRepository extends JpaRepository<MeuGendazOtpChallengeEntity, Long> {
    Optional<MeuGendazOtpChallengeEntity> findByEmpresaIdAndEmailIgnoreCase(Long empresaId, String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MeuGendazOtpChallengeEntity c where c.empresa.id = :empresaId and lower(c.email) = lower(:email)")
    Optional<MeuGendazOtpChallengeEntity> findByEmpresaIdAndEmailForUpdate(@Param("empresaId") Long empresaId, @Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MeuGendazOtpChallengeEntity c where c.onboardingSessionHash = :hash")
    Optional<MeuGendazOtpChallengeEntity> findByOnboardingSessionHashForUpdate(@Param("hash") String hash);

    Optional<MeuGendazOtpChallengeEntity> findByOnboardingSessionHash(String hash);

    @Modifying
    @Query("delete from MeuGendazOtpChallengeEntity c where (c.otpExpiraEm is not null and c.otpExpiraEm < :limite) and (c.onboardingSessionExpiraEm is null or c.onboardingSessionExpiraEm < :limite)")
    int deleteExpiredBefore(@Param("limite") LocalDateTime limite);
}
