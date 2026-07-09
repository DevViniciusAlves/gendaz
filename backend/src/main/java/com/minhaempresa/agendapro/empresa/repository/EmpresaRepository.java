package com.minhaempresa.agendapro.empresa.repository;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpresaRepository extends JpaRepository<EmpresaEntity, Long> {
    // ⚠️ DESATIVADO - WhatsApp
    // interface WhatsappConfigView {
    //     Long getId();
    //     String getNomeFantasia();
    //     String getAgendamentoSlug();
    //     String getWhatsappDescricaoEmpresa();
    //     Boolean getWhatsappConnected();
    //     String getWhatsappPhone();
    //     Boolean getWhatsappNotificationsEnabled();
    //     Boolean getWhatsappSecretariaIaEnabled();
    //     String getTimezone();
    //     String getWhatsappMensagemBoasVindas();
    //     String getWhatsappRespostaHorarios();
    //     String getWhatsappRespostaServicos();
    //     String getWhatsappRespostaNaoEntende();
    //     String getWhatsappMensagemHumano();
    // }

    boolean existsByDocumento(String documento);

    boolean existsByTelefone(String telefone);

    @Query("select e from EmpresaEntity e where lower(trim(e.nomeFantasia)) = lower(trim(:nomeFantasia))")
    Optional<EmpresaEntity> findByNomeFantasiaNormalizado(@Param("nomeFantasia") String nomeFantasia);

    Optional<EmpresaEntity> findByAgendamentoSlug(String agendamentoSlug);

    // ⚠️ DESATIVADO - WhatsApp
    // @Query("""
    //         select e.id as id,
    //                e.nomeFantasia as nomeFantasia,
    //                e.agendamentoSlug as agendamentoSlug,
    //                e.whatsappDescricaoEmpresa as whatsappDescricaoEmpresa,
    //                e.whatsappConnected as whatsappConnected,
    //                e.whatsappPhone as whatsappPhone,
    //                e.whatsappNotificationsEnabled as whatsappNotificationsEnabled,
    //                e.whatsappSecretariaIaEnabled as whatsappSecretariaIaEnabled,
    //                e.timezone as timezone,
    //                e.whatsappMensagemBoasVindas as whatsappMensagemBoasVindas,
    //                e.whatsappRespostaHorarios as whatsappRespostaHorarios,
    //                e.whatsappRespostaServicos as whatsappRespostaServicos,
    //                e.whatsappRespostaNaoEntende as whatsappRespostaNaoEntende,
    //                e.whatsappMensagemHumano as whatsappMensagemHumano
    //         from EmpresaEntity e
    //         where e.id = :empresaId
    //         """)
    // Optional<WhatsappConfigView> findWhatsappConfigViewById(@Param("empresaId") Long empresaId);

    boolean existsByAgendamentoSlug(String agendamentoSlug);

    // ⚠️ DESATIVADO - WhatsApp
    // java.util.List<EmpresaEntity> findByWhatsappConnectedTrue();
}
