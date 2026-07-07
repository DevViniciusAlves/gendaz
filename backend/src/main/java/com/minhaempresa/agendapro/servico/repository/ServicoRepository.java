package com.minhaempresa.agendapro.servico.repository;

import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

public interface ServicoRepository extends JpaRepository<ServicoEntity, Long> {
    interface ServicoContextView {
        Long getId();
        String getNome();
        java.math.BigDecimal getValor();
        Integer getDuracaoMinutos();
        com.minhaempresa.agendapro.shared.enums.StatusCadastro getStatus();
    }

    @EntityGraph(attributePaths = {"empresa"})
    List<ServicoEntity> findByEmpresaId(Long empresaId);

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

    long countByEmpresaIdAndStatus(Long empresaId, com.minhaempresa.agendapro.shared.enums.StatusCadastro status);

    @Query("""
            select count(s) from ServicoEntity s
            where s.empresa.id = :empresaId
              and s.status = com.minhaempresa.agendapro.shared.enums.StatusCadastro.ATIVO
            """)
    long countAtivosByEmpresaId(Long empresaId);
}
