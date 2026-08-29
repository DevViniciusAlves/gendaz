package com.minhaempresa.gendaz.auditoria.repository;

import com.minhaempresa.gendaz.auditoria.entity.LogAtividadeEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LogAtividadeRepository extends JpaRepository<LogAtividadeEntity, Long> {

    Page<LogAtividadeEntity> findByEmpresaIdOrderByDataHoraDesc(Long empresaId, Pageable pageable);

    @Query("""
            select l from LogAtividadeEntity l
            where l.empresa.id = :empresaId
              and (:entidade is null or l.entidade = :entidade)
              and (:termo is null or lower(l.acao) like lower(:termo) or lower(l.nomeUsuario) like lower(:termo))
            order by l.dataHora desc
            """)
    Page<LogAtividadeEntity> pesquisar(
            @Param("empresaId") Long empresaId,
            @Param("entidade") String entidade,
            @Param("termo") String termo,
            Pageable pageable
    );

    List<LogAtividadeEntity> findByEmpresaIdAndEntidadeAndEntidadeId(Long empresaId, String entidade, Long entidadeId);

    List<LogAtividadeEntity> findTop1000ByOrderByDataHoraDesc();
}
