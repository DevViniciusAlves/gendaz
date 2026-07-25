package com.minhaempresa.agendapro.empresa.service;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.enums.RamoEmpresa;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import com.minhaempresa.agendapro.servico.repository.ServicoRepository;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                "salão",
                "coloracao",
                "coloração",
                "escova",
                "hidratação",
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
                "musculação",
                "treino",
                "treino funcional",
                "pilates",
                "yoga",
                "crossfit",
                "aerobica",
                "aeróbica"
        ));
        PALAVRAS_CHAVE.put(RamoEmpresa.CLINICA_FISIOTERAPIA, List.of(
                "fisioterapia",
                "fisio",
                "reabilitacao",
                "reabilitação",
                "massagem terapeutica",
                "massagem terapêutica",
                "sessao fisio",
                "sessão fisio"
        ));
        PALAVRAS_CHAVE.put(RamoEmpresa.CLINICA_ODONTOLOGIA, List.of(
                "odonto",
                "dentista",
                "limpeza dentaria",
                "limpeza dentária",
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

        if (empresa.getRamo() != null) {
            return empresa;
        }

        long quantidadeServicos = servicoRepository.countByEmpresaId(empresaId);
        if (quantidadeServicos <= 0) {
            return empresa;
        }

        ServicoEntity primeiroServico = servicoRepository.findFirstByEmpresaIdOrderByIdAsc(empresaId).orElse(null);
        RamoEmpresa ramo = detectar(primeiroServico != null ? primeiroServico.getNome() : null);
        empresa.setRamo(ramo != null ? ramo : RamoEmpresa.OUTRO);
        return empresaRepository.save(empresa);
    }

    @Transactional
    public void detectarRamoAposServicoNovo(Long empresaId, String nomeServico) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));

        if (empresa.getRamo() != null) {
            return;
        }

        RamoEmpresa ramo = detectar(nomeServico);
        empresa.setRamo(ramo != null ? ramo : RamoEmpresa.OUTRO);
        empresaRepository.save(empresa);
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
        String nomeNormalizado = normalizar(nomeServico);
        if (nomeNormalizado.isBlank()) {
            return null;
        }

        for (Map.Entry<RamoEmpresa, List<String>> entry : PALAVRAS_CHAVE.entrySet()) {
            for (String palavraChave : entry.getValue()) {
                String chaveNormalizada = normalizar(palavraChave);
                if (!chaveNormalizada.isBlank() && nomeNormalizado.contains(chaveNormalizada)) {
                    return entry.getKey();
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
