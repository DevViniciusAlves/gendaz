package com.minhaempresa.gendaz.shared.security;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecurityRateLimitEntryRepository extends JpaRepository<SecurityRateLimitEntryEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SecurityRateLimitEntryEntity e where e.scopeKey = :scopeKey")
    Optional<SecurityRateLimitEntryEntity> findByScopeKeyForUpdate(@Param("scopeKey") String scopeKey);

    @Modifying
    @Query("delete from SecurityRateLimitEntryEntity e where e.expiraEm < :limite")
    int deleteExpiredBefore(@Param("limite") LocalDateTime limite);
}
