package com.minhaempresa.agendapro.meugendazpromocao.service;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.meugendazpromocao.entity.MeuGendazPromocaoEntity;
import com.minhaempresa.agendapro.meugendazpromocao.repository.MeuGendazPromocaoRepository;
import com.minhaempresa.agendapro.promocao.entity.PromocaoEntity;
import com.minhaempresa.agendapro.promocao.repository.PromocaoRepository;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeuGendazPromocaoSyncService {
    private final PromocaoRepository promocaoRepository;
    private final MeuGendazPromocaoRepository meuGendazPromocaoRepository;
    private final EmpresaRepository empresaRepository;

    @Transactional
    public void sincronizarEmpresa(Long empresaId) {
        if (empresaId == null) {
            return;
        }

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));

        Map<Long, PromocaoEntity> promocoesAtuais = new LinkedHashMap<>();
        for (PromocaoEntity promocao : promocaoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId)) {
            promocoesAtuais.put(promocao.getId(), promocao);
        }
        Set<String> codigosAtuais = promocoesAtuais.values().stream()
                .map(PromocaoEntity::getCodigo)
                .filter(codigo -> codigo != null && !codigo.isBlank())
                .map(codigo -> codigo.trim().toUpperCase())
                .collect(Collectors.toSet());

        Map<Long, MeuGendazPromocaoEntity> mirrorsPorOrigem = new LinkedHashMap<>();
        for (MeuGendazPromocaoEntity mirror : meuGendazPromocaoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId)) {
            if (mirror.getPromocaoOrigemId() != null) {
                mirrorsPorOrigem.put(mirror.getPromocaoOrigemId(), mirror);
            }
        }

        Set<Long> origensProcessadas = new HashSet<>();
        for (PromocaoEntity promocao : promocoesAtuais.values()) {
            sincronizarPromocao(empresa, promocao, mirrorsPorOrigem.get(promocao.getId()));
            origensProcessadas.add(promocao.getId());
        }

        for (MeuGendazPromocaoEntity mirror : meuGendazPromocaoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId)) {
            boolean origemRemovida = mirror.getPromocaoOrigemId() != null && !origensProcessadas.contains(mirror.getPromocaoOrigemId());
            boolean codigoNaoExisteMais = mirror.getPromocaoOrigemId() == null
                    && (mirror.getCodigo() == null || !codigosAtuais.contains(mirror.getCodigo().trim().toUpperCase()));
            if (origemRemovida || codigoNaoExisteMais) {
                if (!StatusCadastro.INATIVO.equals(mirror.getStatus())) {
                    mirror.setStatus(StatusCadastro.INATIVO);
                    meuGendazPromocaoRepository.save(mirror);
                }
            }
        }
    }

    @Transactional
    public void sincronizarPromocao(Long empresaId, Long promocaoId) {
        if (empresaId == null || promocaoId == null) {
            return;
        }
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
        PromocaoEntity promocao = promocaoRepository.findByIdAndEmpresaId(promocaoId, empresaId)
                .orElse(null);
        if (promocao == null) {
            sincronizarEmpresa(empresaId);
            return;
        }
        MeuGendazPromocaoEntity mirror = meuGendazPromocaoRepository.findByEmpresaIdAndPromocaoOrigemId(empresaId, promocaoId)
                .orElseGet(() -> meuGendazPromocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(empresaId, promocao.getCodigo().trim())
                        .orElse(null));
        sincronizarPromocao(empresa, promocao, mirror);
    }

    @Transactional
    public void inativarPromocao(Long empresaId, Long promocaoId) {
        if (empresaId == null || promocaoId == null) {
            return;
        }
        meuGendazPromocaoRepository.findByEmpresaIdAndPromocaoOrigemId(empresaId, promocaoId)
                .or(() -> promocaoRepository.findByIdAndEmpresaId(promocaoId, empresaId)
                        .flatMap(promocao -> meuGendazPromocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(empresaId, promocao.getCodigo())))
                .ifPresent(mirror -> {
                    mirror.setStatus(StatusCadastro.INATIVO);
                    meuGendazPromocaoRepository.save(mirror);
                });
    }

    private void sincronizarPromocao(EmpresaEntity empresa, PromocaoEntity promocao, MeuGendazPromocaoEntity mirror) {
        if (mirror == null) {
            mirror = meuGendazPromocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(empresa.getId(), promocao.getCodigo())
                    .orElse(null);
        }

        if (mirror == null) {
            mirror = MeuGendazPromocaoEntity.builder()
                    .empresa(empresa)
                    .promocaoOrigemId(promocao.getId())
                    .codigo(promocao.getCodigo())
                    .descricao(promocao.getDescricao())
                    .tipo(promocao.getTipo() == null ? null : promocao.getTipo().name())
                    .valor(promocao.getValor())
                    .dataInicio(promocao.getDataInicio())
                    .dataFim(promocao.getDataFim())
                    .quantidadeLimite(promocao.getQuantidadeLimite())
                    .quantidadeUsada(promocao.getQuantidadeUsada())
                    .status(promocao.getStatus())
                    .aplicarTodosServicos(promocao.getAplicarTodosServicos())
                    .servicos(new HashSet<>(promocao.getServicos()))
                    .dataCriacao(promocao.getDataCriacao())
                    .dataNotificacao(promocao.getDataNotificacao())
                    .build();
        } else {
            mirror.setEmpresa(empresa);
            mirror.setPromocaoOrigemId(promocao.getId());
            mirror.setCodigo(promocao.getCodigo());
            mirror.setDescricao(promocao.getDescricao());
            mirror.setTipo(promocao.getTipo() == null ? null : promocao.getTipo().name());
            mirror.setValor(promocao.getValor());
            mirror.setDataInicio(promocao.getDataInicio());
            mirror.setDataFim(promocao.getDataFim());
            mirror.setQuantidadeLimite(promocao.getQuantidadeLimite());
            if (mirror.getQuantidadeUsada() == null) {
                mirror.setQuantidadeUsada(promocao.getQuantidadeUsada());
            }
            mirror.setStatus(promocao.getStatus());
            mirror.setAplicarTodosServicos(promocao.getAplicarTodosServicos());
            mirror.setServicos(new HashSet<>(promocao.getServicos()));
            if (mirror.getDataCriacao() == null) {
                mirror.setDataCriacao(promocao.getDataCriacao());
            }
            mirror.setDataNotificacao(promocao.getDataNotificacao());
        }

        meuGendazPromocaoRepository.save(mirror);
    }
}
