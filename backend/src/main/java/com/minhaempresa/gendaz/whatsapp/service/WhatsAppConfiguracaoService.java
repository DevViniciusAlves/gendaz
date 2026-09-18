package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.whatsapp.WhatsAppProvider;
import com.minhaempresa.gendaz.whatsapp.WhatsAppSessionStatus;
import com.minhaempresa.gendaz.whatsapp.WhatsAppResult;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppConfiguracaoEntity;
import com.minhaempresa.gendaz.whatsapp.policy.WhatsAppPlanoPolicy;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppConfiguracaoRepository;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
    public static final String DEFAULT_LEMBRETE_TEMPLATE =
            "Olá, {cliente}! Lembrete: seu atendimento na {empresa} está marcado para {data} às {hora}.";
    private static final int TEMPLATE_MAX_LENGTH = 500;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]+)}");
    private static final Set<String> PLACEHOLDERS_SUPORTADOS =
            Set.of("cliente", "empresa", "data", "hora");

    private final WhatsAppConfiguracaoRepository configuracaoRepository;
    private final EmpresaRepository empresaRepository;
    private final AssinaturaService assinaturaService;
    private final WhatsAppProvider provider;
    private final ApplicationEventPublisher eventPublisher;

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
        return obterLembreteTemplatePersonalizado(empresaId).orElse(DEFAULT_LEMBRETE_TEMPLATE);
    }

    @Transactional(readOnly = true)
    public Optional<String> obterLembreteTemplatePersonalizado(Long empresaId) {
        return configuracaoRepository.findByEmpresaId(empresaId)
                .map(WhatsAppConfiguracaoEntity::getLembreteTemplate)
                .filter(t -> !t.isBlank())
                .map(String::trim);
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
            
            WhatsAppResult<WhatsAppSessionStatus> status = provider.consultarStatus(String.valueOf(empresaId));
            if (!status.isSuccess() || !"CONNECTED".equals(status.getData().getState())) {
                throw new BusinessException("WHATSAPP_NOT_CONNECTED");
            }
        }
        boolean resultado = false;
        boolean persistido = false;
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            Optional<WhatsAppConfiguracaoEntity> atual = self.buscarNova(empresaId);
            if (atual.isPresent()) {
                resultado = self.salvarValorNovo(atual.get().getId(), ativo);
                persistido = true;
                break;
            }
            try {
                resultado = self.criarNova(empresaId, ativo).isLembretesAtivos();
                persistido = true;
                break;
            } catch (DataIntegrityViolationException duplicada) {
                // Outra transacao criou primeiro: rele na proxima rodada.
            }
        }
        if (!persistido) {
            throw new BusinessException("Nao foi possivel salvar a configuracao. Tente novamente.");
        }
        if (resultado && ativo) {
            eventPublisher.publishEvent(new LembretesAtivadosEvent(empresaId));
        }
        return resultado;
    }

    /**
     * Atualizacao agregada do PATCH, REALMENTE atomica: valida TODO o payload
     * antes da primeira escrita e persiste todos os campos em UMA unica
     * transacao (sem REQUIRES_NEW por campo). A convergencia sob concorrencia
     * na criacao da primeira linha e preservada via retry da operacao inteira,
     * com a UNIQUE como barreira. Eventos sao publicados somente depois que o
     * commit da tentativa vencedora terminou; se a transacao falhar, ZERO
     * eventos. Os listeners usam fallbackExecution para entregar mesmo quando
     * publicados fora de transacao ambiente.
     */
    public void atualizarConfiguracao(Long empresaId, Boolean lembretesAtivos, String lembreteTemplate) {
        String templateNormalizado = null;
        if (lembreteTemplate != null) {
            templateNormalizado = normalizarTemplate(lembreteTemplate);
        }
        if (lembretesAtivos != null && lembretesAtivos) {
            // Leitura em transacao propria: a facade agregada nao e
            // transacional (o nucleo de escrita tem a sua), e Plano e lazy.
            self.validarAtivacao(empresaId);
        }
        ResultadoAplicacao aplicado = null;
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            try {
                aplicado = self.aplicarConfiguracao(empresaId, lembretesAtivos, templateNormalizado);
                break;
            } catch (DataIntegrityViolationException corrida) {
                // Outra transacao criou a linha primeiro: rele na proxima rodada.
            }
        }
        if (aplicado == null) {
            throw new BusinessException("Nao foi possivel salvar a configuracao. Tente novamente.");
        }
        if (aplicado.ativouLembretes()) {
            eventPublisher.publishEvent(new LembretesAtivadosEvent(empresaId));
        }
        if (aplicado.templateAlterado()) {
            eventPublisher.publishEvent(new TemplateAlteradoEvent(empresaId, aplicado.templateFinal()));
        }
    }

    /**
     * Nucleo da escrita agregada em UMA transacao: carrega ou cria a linha e
     * aplica todos os campos na mesma entity, com um unico flush. Nunca
     * chamado com REQUIRES_NEW por campo.
     */
    @Transactional
    public ResultadoAplicacao aplicarConfiguracao(
            Long empresaId, Boolean lembretesAtivos, String templateNormalizado) {
        WhatsAppConfiguracaoEntity entidade = configuracaoRepository.findByEmpresaId(empresaId).orElse(null);
        boolean ativoAntes = entidade != null && entidade.isLembretesAtivos();
        String templateAntes = entidade != null ? entidade.getLembreteTemplate() : null;
        if (entidade == null) {
            entidade = WhatsAppConfiguracaoEntity.builder()
                    .empresa(empresaRepository.getReferenceById(empresaId))
                    .lembretesAtivos(lembretesAtivos != null ? lembretesAtivos : false)
                    .lembreteTemplate(templateNormalizado)
                    .build();
        } else {
            if (lembretesAtivos != null) {
                entidade.setLembretesAtivos(lembretesAtivos);
            }
            if (templateNormalizado != null) {
                entidade.setLembreteTemplate(templateNormalizado);
            }
        }
        configuracaoRepository.saveAndFlush(entidade);
        boolean ativoDepois = entidade.isLembretesAtivos();
        String templateDepois = entidade.getLembreteTemplate();
        return new ResultadoAplicacao(
                !ativoAntes && ativoDepois,
                templateNormalizado != null && !templateNormalizado.equals(templateAntes),
                templateDepois);
    }

    public record ResultadoAplicacao(boolean ativouLembretes, boolean templateAlterado, String templateFinal) {}

    @Transactional(readOnly = true)
    public void validarAtivacao(Long empresaId) {
        exigirConexaoParaAtivacao(empresaId);
    }

    private void exigirConexaoParaAtivacao(Long empresaId) {
        String plano = assinaturaService.buscarAtualPorEmpresa(empresaId)
                .map(a -> a.getPlano().getNome())
                .orElse(null);
        if (!WhatsAppPlanoPolicy.possuiWhatsApp(plano)) {
            throw new BusinessException("WhatsApp nao disponivel no plano atual.");
        }
        WhatsAppResult<WhatsAppSessionStatus> status = provider.consultarStatus(String.valueOf(empresaId));
        if (!status.isSuccess() || !"CONNECTED".equals(status.getData().getState())) {
            throw new BusinessException("WHATSAPP_NOT_CONNECTED");
        }
    }

    @Transactional
    public String definirLembreteTemplate(Long empresaId, String template) {
        String normalizado = normalizarTemplate(template);
        String resultado = null;
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            Optional<WhatsAppConfiguracaoEntity> atual = self.buscarNova(empresaId);
            if (atual.isPresent()) {
                resultado = self.salvarTemplateNovo(atual.get().getId(), normalizado);
                break;
            }
            try {
                resultado = self.criarNovaComTemplate(empresaId, normalizado).getLembreteTemplate();
                break;
            } catch (DataIntegrityViolationException duplicada) {
                // Outra transacao criou primeiro: rele na proxima rodada.
            }
        }
        if (resultado == null) {
            throw new BusinessException("Nao foi possivel salvar a configuracao. Tente novamente.");
        }
        eventPublisher.publishEvent(new TemplateAlteradoEvent(empresaId, resultado));
        return resultado;
    }

    private String normalizarTemplate(String template) {
        if (template == null || template.isBlank()) {
            throw new BusinessException("A mensagem do lembrete e obrigatoria.");
        }
        String normalizado = template.trim();
        if (normalizado.length() > TEMPLATE_MAX_LENGTH) {
            throw new BusinessException("A mensagem do lembrete deve ter no maximo 500 caracteres.");
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(normalizado);
        while (matcher.find()) {
            String variavel = matcher.group(1);
            if (!PLACEHOLDERS_SUPORTADOS.contains(variavel)) {
                throw new BusinessException("A variavel {" + variavel + "} nao e suportada.");
            }
        }
        // Rejeita sintaxe malformada: chaves duplas, espacos internos ou
        // chaves sem fechamento/abertura. Remove os placeholders validos e
        // qualquer chave restante indica template quebrado.
        if (normalizado.contains("{{") || normalizado.contains("}}")) {
            throw new BusinessException("O template contem variavel malformada. Use {cliente}, {empresa}, {data} e {hora}.");
        }
        String semValidos = normalizado
                .replace("{cliente}", "")
                .replace("{empresa}", "")
                .replace("{data}", "")
                .replace("{hora}", "");
        if (semValidos.contains("{") || semValidos.contains("}")) {
            throw new BusinessException("O template contem variavel malformada. Use {cliente}, {empresa}, {data} e {hora}.");
        }
        return normalizado;
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
    public WhatsAppConfiguracaoEntity criarNovaComTemplate(Long empresaId, String template) {
        return configuracaoRepository.saveAndFlush(WhatsAppConfiguracaoEntity.builder()
                .empresa(empresaRepository.getReferenceById(empresaId))
                .lembretesAtivos(false)
                .lembreteTemplate(template)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean salvarValorNovo(Long configuracaoId, boolean ativo) {
        WhatsAppConfiguracaoEntity entidade = configuracaoRepository.findById(configuracaoId)
                .orElseThrow(() -> new BusinessException("Configuracao nao encontrada."));
        entidade.setLembretesAtivos(ativo);
        return configuracaoRepository.save(entidade).isLembretesAtivos();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String salvarTemplateNovo(Long configuracaoId, String template) {
        WhatsAppConfiguracaoEntity entidade = configuracaoRepository.findById(configuracaoId)
                .orElseThrow(() -> new BusinessException("Configuracao nao encontrada."));
        entidade.setLembreteTemplate(template);
        return configuracaoRepository.save(entidade).getLembreteTemplate();
    }

    public record LembretesAtivadosEvent(Long empresaId) {}
    public record TemplateAlteradoEvent(Long empresaId, String template) {}
}
