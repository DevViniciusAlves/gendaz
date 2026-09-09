package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppConfiguracaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura tenant-safe da configuracao WhatsApp da empresa. Ausencia de
 * linha equivale a desabilitado.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppConfiguracaoService {

    private final WhatsAppConfiguracaoRepository configuracaoRepository;

    @Transactional(readOnly = true)
    public boolean lembretesAtivos(Long empresaId) {
        return configuracaoRepository.findByEmpresaId(empresaId)
                .map(c -> c.isLembretesAtivos())
                .orElse(false);
    }
}
