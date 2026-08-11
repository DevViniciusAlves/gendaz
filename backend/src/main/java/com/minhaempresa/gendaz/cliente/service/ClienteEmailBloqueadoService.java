package com.minhaempresa.gendaz.cliente.service;

import com.minhaempresa.gendaz.cliente.entity.ClienteEmailBloqueadoEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteEmailBloqueadoRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.shared.BusinessException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClienteEmailBloqueadoService {
    private static final String MENSAGEM_BLOQUEIO = "Este email nao Ã© permitido entrar no meu gendaz";

    private final ClienteEmailBloqueadoRepository repository;

    @Transactional
    public void bloquear(EmpresaEntity empresa, String email, String motivo) {
        if (empresa == null || empresa.getId() == null) {
            return;
        }
        String normalizado = normalizar(email);
        if (normalizado.isBlank()) {
            return;
        }
        if (repository.existsByEmpresaIdAndEmailIgnoreCase(empresa.getId(), normalizado)) {
            return;
        }
        repository.save(ClienteEmailBloqueadoEntity.builder()
                .empresa(empresa)
                .email(normalizado)
                .motivo(motivo)
                .build());
    }

    @Transactional(readOnly = true)
    public void validarAcesso(Long empresaId, String email) {
        String normalizado = normalizar(email);
        if (empresaId == null || normalizado.isBlank()) {
            return;
        }
        if (repository.existsByEmpresaIdAndEmailIgnoreCase(empresaId, normalizado)) {
            throw new BusinessException(MENSAGEM_BLOQUEIO);
        }
    }

    @Transactional
    public void desbloquear(Long empresaId, String email) {
        String normalizado = normalizar(email);
        if (empresaId == null || normalizado.isBlank()) {
            return;
        }
        repository.deleteByEmpresaIdAndEmailIgnoreCase(empresaId, normalizado);
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

