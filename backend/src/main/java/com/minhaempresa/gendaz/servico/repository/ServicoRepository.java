package com.minhaempresa.gendaz.servico.repository;

import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;

public interface ServicoRepository extends JpaRepository<ServicoEntity, Long> {
    interface ServicoContextView {
        Long getId();
        String getNome();
        java.math.BigDecimal getValor();
        Integer getDuracaoMinutos();
        com.minhaempresa.gendaz.shared.enums.StatusCadastro getStatus();
    }

    @EntityGraph(attributePaths = {"empresa"})
    List<ServicoEntity> findByEmpresaId(Long empresaId);

    @EntityGraph(attributePaths = {"empresa"})
    List<ServicoEntity> findByEmpresaIdAndStatusOrderByIdAsc(Long empresaId, StatusCadastro status);

    long countByEmpresaId(Long empresaId);

    java.util.Optional<ServicoEntity> findFirstByEmpresaIdOrderByIdAsc(Long empresaId);

    @Query("""
            select s.id as id,
                   s.nome as nome,
                   s.valor as valor,
                   s.duracaoMinutos as duracaoMinutos,
                   s.status as status
            from ServicoEntity s
            where s.empresa.id = :empresaId
            order by s.nome asc
            """)
    List<ServicoContextView> findContextByEmpresaId(@org.springframework.data.repository.query.Param("empresaId") Long empresaId);

    long countByEmpresaIdAndStatus(Long empresaId, com.minhaempresa.gendaz.shared.enums.StatusCadastro status);

    @Query("""
            select count(s) from ServicoEntity s
            where s.empresa.id = :empresaId
              and s.status = com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO
            """)
    long countAtivosByEmpresaId(Long empresaId);
}

