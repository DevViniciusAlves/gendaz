package com.minhaempresa.gendaz.auth.idempotencia.repository;

import com.minhaempresa.gendaz.auth.idempotencia.entity.CadastroIdempotenciaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CadastroIdempotenciaRepository extends JpaRepository<CadastroIdempotenciaEntity, Long> {

    Optional<CadastroIdempotenciaEntity> findByKeyHash(String keyHash);

    boolean existsByKeyHash(String keyHash);

    /**
     * Leitura com lock de escrita: usada para serializar a avaliacao/reclamacao da
     * mesma chave quando duas requests concorrentes disputam a mesma idempotencia.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CadastroIdempotenciaEntity r where r.keyHash = :keyHash")
    Optional<CadastroIdempotenciaEntity> findByKeyHashForUpdate(@Param("keyHash") String keyHash);
}
