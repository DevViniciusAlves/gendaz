package com.minhaempresa.agendapro.usuario.repository;

import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import jakarta.persistence.LockModeType;
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
    Optional<UsuarioEntity> findByEmpresaIdAndEmail(Long empresaId, String email);
    Optional<UsuarioEntity> findByEmpresaIdAndEmailIgnoreCase(Long empresaId, String email);
    Optional<UsuarioEntity> findByEmpresaIdAndSessaoAtiva(Long empresaId, String sessaoAtiva);
    Optional<UsuarioEntity> findByEmpresaAndEmail(com.minhaempresa.agendapro.empresa.entity.EmpresaEntity empresa, String email);
    Optional<UsuarioEntity> findFirstByPerfil(PerfilUsuario perfil);
    List<UsuarioEntity> findByEmpresaId(Long empresaId);
    List<UsuarioEntity> findByEmpresaIdAndPerfil(Long empresaId, PerfilUsuario perfil);
    @EntityGraph(attributePaths = {"empresa"})
    Optional<UsuarioEntity> findBySessaoAtiva(String sessaoAtiva);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuarioEntity u where u.id = :id")
    Optional<UsuarioEntity> findByIdForUpdate(@Param("id") Long id);
    boolean existsByEmail(String email);
    boolean existsByEmpresaIdAndEmail(Long empresaId, String email);
}
