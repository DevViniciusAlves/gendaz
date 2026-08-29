package com.minhaempresa.gendaz.membresia.repository;

import com.minhaempresa.gendaz.membresia.entity.MembresiaEntity;
import com.minhaempresa.gendaz.membresia.enums.FuncaoMembresia;
import com.minhaempresa.gendaz.membresia.enums.StatusMembresia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembresiaRepository extends JpaRepository<MembresiaEntity, Long> {
    List<MembresiaEntity> findByEmpresaId(Long empresaId);
    Optional<MembresiaEntity> findByUsuarioId(Long usuarioId);
    Optional<MembresiaEntity> findByEmpresaIdAndUsuarioId(Long empresaId, Long usuarioId);
    List<MembresiaEntity> findAllByEmpresaIdAndUsuarioId(Long empresaId, Long usuarioId);
    List<MembresiaEntity> findByEmpresaIdAndStatus(Long empresaId, StatusMembresia status);
    List<MembresiaEntity> findByEmpresaIdAndFuncao(Long empresaId, FuncaoMembresia funcao);
    boolean existsByEmpresaIdAndUsuarioId(Long empresaId, Long usuarioId);
    boolean existsByUsuarioId(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);

    @Modifying
    @Query("update MembresiaEntity m set m.alteradoPor = null where m.alteradoPor.id = :usuarioId")
    void desvincularAlteracoes(@Param("usuarioId") Long usuarioId);
}

