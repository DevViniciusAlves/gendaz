package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppUsoCicloEntity;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppUsoCicloRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criacao sob demanda do registro de uso do ciclo em transacao propria
 * (REQUIRES_NEW). Se a insercao violar a UNIQUE (empresa, ciclo) porque
 * outra transacao venceu a corrida, apenas esta transacao interna sofre
 * rollback: a transacao chamadora permanece utilizavel e rele o registro
 * vencedor com lock pessimista. Nunca continuar a mesma transacao depois
 * de uma violacao de constraint/flush (no PostgreSQL ela nao pode
 * continuar).
 */
@Service
@RequiredArgsConstructor
public class WhatsAppUsoCicloInitializer {

    private final WhatsAppUsoCicloRepository usoRepository;
    private final EmpresaRepository empresaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inicializar(Long empresaId, LocalDate cicloInicio) {
        usoRepository.saveAndFlush(WhatsAppUsoCicloEntity.builder()
                .empresa(empresaRepository.getReferenceById(empresaId))
                .cicloInicio(cicloInicio)
                .build());
    }
}
