package com.minhaempresa.gendaz.profissional.repository;

import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

public interface ProfissionalRepository extends JpaRepository<ProfissionalEntity, Long> {
    interface ProfissionalContextView {
        Long getId();
        String getNome();
        String getEspecialidade();
        com.minhaempresa.gendaz.shared.enums.StatusCadastro getStatus();
    }

    @EntityGraph(attributePaths = {"empresa"})
    List<ProfissionalEntity> findByEmpresaId(Long empresaId);

    @Query("""
            select p.id as id,
                   p.nome as nome,
                   p.especialidade as especialidade,
                   p.status as status
            from ProfissionalEntity p
            where p.empresa.id = :empresaId
            order by p.nome asc
            """)
    List<ProfissionalContextView> findContextByEmpresaId(@org.springframework.data.repository.query.Param("empresaId") Long empresaId);

    @Query("""
            select count(p) from ProfissionalEntity p
            where p.empresa.id = :empresaId
              and p.status = com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO
            """)
    long countAtivosByEmpresaId(Long empresaId);
}

