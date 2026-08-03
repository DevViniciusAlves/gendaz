package com.minhaempresa.agendapro.chamado.repository;

import com.minhaempresa.agendapro.chamado.entity.ChamadoEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChamadoRepository extends JpaRepository<ChamadoEntity, Long> {
    List<ChamadoEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);
    List<ChamadoEntity> findByEmpresaIdAndUsuarioIdOrderByDataCriacaoDesc(Long empresaId, Long usuarioId);
    List<ChamadoEntity> findAllByOrderByDataCriacaoDesc();

    @Query(value = """
            SELECT
                c.id AS id,
                c.assunto AS assunto,
                c.mensagem AS mensagem,
                COALESCE(e.nome_fantasia, '') AS empresa,
                COALESCE(u.nome, '') AS usuario,
                COALESCE(c.status, '') AS status,
                c.resposta AS resposta,
                c.data_criacao AS dataCriacao,
                c.data_atualizacao AS dataAtualizacao
            FROM chamados c
            LEFT JOIN empresas e ON e.id = c.empresa_id
            LEFT JOIN usuarios u ON u.id = c.usuario_id
            ORDER BY c.data_criacao DESC
            """, nativeQuery = true)
    List<AdminChamadoProjection> listarParaAdmin();
}
