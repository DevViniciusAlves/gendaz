package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppConfiguracaoEntity;
import com.minhaempresa.gendaz.whatsapp.policy.WhatsAppPlanoPolicy;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppConfiguracaoRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura e escrita tenant-safe da configuracao WhatsApp da empresa.
 * Ausencia de linha equivale a desabilitado. Uma configuracao por empresa
 * (UNIQUE); corridas convergem para uma unica linha.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppConfiguracaoService {

    private final WhatsAppConfiguracaoRepository configuracaoRepository;
    private final EmpresaRepository empresaRepository;
    private final AssinaturaService assinaturaService;

    private WhatsAppConfiguracaoService self;

    /**
     * Auto-referencia via proxy: leitura/insercao/atualizacao precisam de
     * transacoes proprias para a convergencia sob concorrencia.
     */
    @Autowired
    public void setSelf(@Lazy WhatsAppConfiguracaoService self) {
        this.self = self;
    }

    @Transactional(readOnly = true)
    public boolean lembretesAtivos(Long empresaId) {
        return configuracaoRepository.findByEmpresaId(empresaId)
                .map(c -> c.isLembretesAtivos())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public String obterLembreteTemplate(Long empresaId) {
        return configuracaoRepository.findByEmpresaId(empresaId)
                .map(WhatsAppConfiguracaoEntity::getLembreteTemplate)
                .filter(t -> !t.isBlank())
                .orElse("Olá, {cliente}! Lembrete: seu atendimento na {empresa} está marcado para {data} às {hora}.");
    }

    /**
     * Define lembretesAtivos. Ativar exige plano com WhatsApp (o backend e
     * autoritativo; o frontend apenas reflete). Desativar e sempre permitido.
     * Convergencia em ate 3 rodadas, cada passo de escrita/leitura isolada
     * em transacao propria via self: leitura, insercao ou atualizacao, com
     * a UNIQUE como barreira final em corrida.
     */
    @Transactional
    public boolean definirLembretesAtivos(Long empresaId, boolean ativo) {
        if (ativo) {
            String plano = assinaturaService.buscarAtualPorEmpresa(empresaId)
                    .map(a -> a.getPlano().getNome())
                    .orElse(null);
            if (!WhatsAppPlanoPolicy.possuiWhatsApp(plano)) {
                throw new BusinessException("WhatsApp nao disponivel no plano atual.");
            }
        }
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            Optional<WhatsAppConfiguracaoEntity> atual = self.buscarNova(empresaId);
            if (atual.isPresent()) {
                return self.salvarValorNovo(atual.get().getId(), ativo);
            }
            try {
                return self.criarNova(empresaId, ativo).isLembretesAtivos();
            } catch (DataIntegrityViolationException duplicada) {
                // Outra transacao criou primeiro: rele na proxima rodada.
            }
        }
        throw new BusinessException("Nao foi possivel salvar a configuracao. Tente novamente.");
    }

    @Transactional
    public void salvarTemplate(Long empresaId, String template) {
        // Validar template aqui... (regra 22)
        WhatsAppConfiguracaoEntity config = configuracaoRepository.findByEmpresaId(empresaId)
                .orElseThrow(() -> new BusinessException("Configuracao nao encontrada."));
        config.setLembreteTemplate(template);
        configuracaoRepository.save(config);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<WhatsAppConfiguracaoEntity> buscarNova(Long empresaId) {
        return configuracaoRepository.findByEmpresaId(empresaId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WhatsAppConfiguracaoEntity criarNova(Long empresaId, boolean ativo) {
        return configuracaoRepository.saveAndFlush(WhatsAppConfiguracaoEntity.builder()
                .empresa(empresaRepository.getReferenceById(empresaId))
                .lembretesAtivos(ativo)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean salvarValorNovo(Long configuracaoId, boolean ativo) {
        WhatsAppConfiguracaoEntity entidade = configuracaoRepository.findById(configuracaoId)
                .orElseThrow(() -> new BusinessException("Configuracao nao encontrada."));
        entidade.setLembretesAtivos(ativo);
        return configuracaoRepository.save(entidade).isLembretesAtivos();
    }
}
