package com.minhaempresa.agendapro.membresia.repository;

import com.minhaempresa.agendapro.membresia.entity.MembresiaEntity;
import com.minhaempresa.agendapro.membresia.enums.FuncaoMembresia;
import com.minhaempresa.agendapro.membresia.enums.StatusMembresia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembresiaRepository extends JpaRepository<MembresiaEntity, Long> {
    List<MembresiaEntity> findByEmpresaId(Long empresaId);
    Optional<MembresiaEntity> findByUsuarioId(Long usuarioId);
    Optional<MembresiaEntity> findByEmpresaIdAndUsuarioId(Long empresaId, Long usuarioId);
    List<MembresiaEntity> findAllByEmpresaIdAndUsuarioId(Long empresaId, Long usuarioId);
    List<MembresiaEntity> findByEmpresaIdAndStatus(Long empresaId, StatusMembresia status);
    List<MembresiaEntity> findByEmpresaIdAndFuncao(Long empresaId, FuncaoMembresia funcao);
    boolean existsByEmpresaIdAndUsuarioId(Long empresaId, Long usuarioId);
    boolean existsByUsuarioId(Long usuarioId);
}
