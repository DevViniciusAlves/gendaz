package com.minhaempresa.agendapro.meugendazpromocao.repository;

import com.minhaempresa.agendapro.meugendazpromocao.entity.MeuGendazPromocaoNotificacaoEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeuGendazPromocaoNotificacaoRepository extends JpaRepository<MeuGendazPromocaoNotificacaoEntity, Long> {
    List<MeuGendazPromocaoNotificacaoEntity> findByClienteIdAndLidoFalseOrderByDataEnvioDesc(Long clienteId);
    Optional<MeuGendazPromocaoNotificacaoEntity> findByPromocaoIdAndClienteId(Long promocaoId, Long clienteId);
    boolean existsByPromocaoIdAndClienteId(Long promocaoId, Long clienteId);
}
