package com.minhaempresa.gendaz.meugendazacesso.repository;

import com.minhaempresa.gendaz.meugendazacesso.entity.MeuGendazAcessoEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeuGendazAcessoRepository extends JpaRepository<MeuGendazAcessoEntity, Long> {
    List<MeuGendazAcessoEntity> findByEmpresaId(Long empresaId);

    @EntityGraph(attributePaths = {"empresa"})
    Optional<MeuGendazAcessoEntity> findByEmpresaIdAndEmailIgnoreCase(Long empresaId, String email);

    @EntityGraph(attributePaths = {"empresa"})
    Optional<MeuGendazAcessoEntity> findByEmpresaIdAndSessaoAtiva(Long empresaId, String sessaoAtiva);

    @EntityGraph(attributePaths = {"empresa"})
    Optional<MeuGendazAcessoEntity> findBySessaoAtiva(String sessaoAtiva);
}


