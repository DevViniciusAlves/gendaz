package com.minhaempresa.gendaz.usuario.repository;

import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {
    Optional<UsuarioEntity> findByEmail(String email);
    Optional<UsuarioEntity> findByEmailIgnoreCase(String email);
    List<UsuarioEntity> findAllByEmailIgnoreCase(String email);
    @Query("""
            select distinct u
            from UsuarioEntity u
            left join MembresiaEntity m on m.usuario = u
            where lower(trim(u.email)) = lower(trim(:email))
              and (m.id is not null or u.perfil in :perfisPainel)
            """)
    List<UsuarioEntity> findUsuariosPainelByEmailIgnoreCase(
            @Param("email") String email,
            @Param("perfisPainel") Collection<PerfilUsuario> perfisPainel
    );
    Optional<UsuarioEntity> findByEmpresaIdAndEmail(Long empresaId, String email);
    Optional<UsuarioEntity> findByEmpresaIdAndEmailIgnoreCase(Long empresaId, String email);
    Optional<UsuarioEntity> findByEmpresaIdAndSessaoAtiva(Long empresaId, String sessaoAtiva);
    Optional<UsuarioEntity> findByEmpresaAndEmail(com.minhaempresa.gendaz.empresa.entity.EmpresaEntity empresa, String email);
    Optional<UsuarioEntity> findFirstByPerfil(PerfilUsuario perfil);
    List<UsuarioEntity> findByEmpresaId(Long empresaId);
    List<UsuarioEntity> findByEmpresaIdAndPerfil(Long empresaId, PerfilUsuario perfil);
    @EntityGraph(attributePaths = {"empresa"})
    Optional<UsuarioEntity> findBySessaoAtiva(String sessaoAtiva);
    @EntityGraph(attributePaths = {"empresa"})
    @Query("select u from UsuarioEntity u where u.id = :id")
    Optional<UsuarioEntity> findByIdComEmpresa(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuarioEntity u where u.id = :id")
    Optional<UsuarioEntity> findByIdForUpdate(@Param("id") Long id);
    boolean existsByEmail(String email);
    boolean existsByEmpresaIdAndEmail(Long empresaId, String email);
}

