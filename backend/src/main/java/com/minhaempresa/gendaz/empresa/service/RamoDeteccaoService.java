package com.minhaempresa.gendaz.empresa.service;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.RamoEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RamoDeteccaoService {
    private static final Map<RamoEmpresa, List<String>> PALAVRAS_CHAVE = new LinkedHashMap<>();

    static {
        PALAVRAS_CHAVE.put(RamoEmpresa.BARBERSHOP, List.of(
                "corte",
                "barba",
                "degrade",
                "barbershop",
                "corte social",
                "corte masculino",
                "barba + design",
                "corte + barba"
        ));
        PALAVRAS_CHAVE.put(RamoEmpresa.SALAO_CABELO, List.of(
                "cabelo",
                "salao",
                "salÃ£o",
                "coloracao",
                "coloraÃ§Ã£o",
                "escova",
                "hidrataÃ§Ã£o",
                "hidratacao",
                "botox capilar",
                "progressiva",
                "alisamento",
                "penteado",
                "tratamento capilar"
        ));
        PALAVRAS_CHAVE.put(RamoEmpresa.PERSONAL_TRAINER, List.of(
                "personal",
                "musculacao",
                "musculaÃ§Ã£o",
                "treino",
                "treino funcional",
                "pilates",
                "yoga",
                "crossfit",
                "aerobica",
                "aerÃ³bica"
        ));
        PALAVRAS_CHAVE.put(RamoEmpresa.CLINICA_FISIOTERAPIA, List.of(
                "fisioterapia",
                "fisio",
                "reabilitacao",
                "reabilitaÃ§Ã£o",
                "massagem terapeutica",
                "massagem terapÃªutica",
                "sessao fisio",
                "sessÃ£o fisio"
        ));
        PALAVRAS_CHAVE.put(RamoEmpresa.CLINICA_ODONTOLOGIA, List.of(
                "odonto",
                "dentista",
                "limpeza dentaria",
                "limpeza dentÃ¡ria",
                "clareamento",
                "implante",
                "canal"
        ));
    }

    private final EmpresaRepository empresaRepository;
    private final ServicoRepository servicoRepository;

    @Transactional
    public EmpresaEntity sincronizarRamoSeNecessario(EmpresaEntity empresa) {
        if (empresa == null) {
            throw new ResourceNotFoundException("Empresa nao encontrada.");
        }

        Long empresaId = empresa.getId();
        List<ServicoEntity> servicosAtivos = servicoRepository.findByEmpresaIdAndStatusOrderByIdAsc(empresaId, StatusCadastro.ATIVO);
        RamoEmpresa ramoCalculado = servicosAtivos.isEmpty()
                ? null
                : detectar(servicosAtivos.stream().map(ServicoEntity::getNome).toList());
        RamoEmpresa ramoFinal = ramoCalculado != null ? ramoCalculado : (servicosAtivos.isEmpty() ? null : RamoEmpresa.OUTRO);

        if (java.util.Objects.equals(empresa.getRamo(), ramoFinal)) {
            return empresa;
        }

        empresa.setRamo(ramoFinal);
        return empresaRepository.save(empresa);
    }

    @Transactional
    public EmpresaEntity sincronizarRamoDaEmpresa(Long empresaId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));

        List<ServicoEntity> servicosAtivos = servicoRepository.findByEmpresaIdAndStatusOrderByIdAsc(empresaId, StatusCadastro.ATIVO);
        if (servicosAtivos.isEmpty()) {
            empresa.setRamo(null);
            return empresaRepository.save(empresa);
        }

        RamoEmpresa ramo = detectar(servicosAtivos.stream().map(ServicoEntity::getNome).toList());
        empresa.setRamo(ramo != null ? ramo : RamoEmpresa.OUTRO);
        return empresaRepository.save(empresa);
    }

    @Transactional
    public void detectarRamoAposServicoNovo(Long empresaId, String nomeServico) {
        sincronizarRamoDaEmpresa(empresaId);
    }

    @Transactional
    public void limparRamoSeSemServicos(Long empresaId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));

        if (servicoRepository.countByEmpresaId(empresaId) == 0 && empresa.getRamo() != null) {
            empresa.setRamo(null);
            empresaRepository.save(empresa);
        }
    }

    public RamoEmpresa detectar(String nomeServico) {
        return detectar(List.of(nomeServico));
    }

    public RamoEmpresa detectar(Collection<String> nomesServico) {
        if (nomesServico == null || nomesServico.isEmpty()) {
            return null;
        }

        for (String nomeServico : nomesServico) {
            String nomeNormalizado = normalizar(nomeServico);
            if (nomeNormalizado.isBlank()) {
                continue;
            }

            for (Map.Entry<RamoEmpresa, List<String>> entry : PALAVRAS_CHAVE.entrySet()) {
                for (String palavraChave : entry.getValue()) {
                    String chaveNormalizada = normalizar(palavraChave);
                    if (!chaveNormalizada.isBlank() && nomeNormalizado.contains(chaveNormalizada)) {
                        return entry.getKey();
                    }
                }
            }
        }

        return null;
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalizado.replaceAll("\\s+", " ");
    }
}

