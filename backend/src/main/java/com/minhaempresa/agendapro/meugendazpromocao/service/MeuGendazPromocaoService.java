package com.minhaempresa.agendapro.meugendazpromocao.service;

import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.meugendazpromocao.dto.MeuGendazPromocaoDtos.*;
import com.minhaempresa.agendapro.meugendazpromocao.entity.*;
import com.minhaempresa.agendapro.meugendazpromocao.repository.*;
import com.minhaempresa.agendapro.promocao.entity.PromocaoEntity;
import com.minhaempresa.agendapro.promocao.repository.PromocaoRepository;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeuGendazPromocaoService {
    private final MeuGendazPromocaoRepository promocaoRepository;
    private final MeuGendazPromocaoUsoRepository usoRepository;
    private final MeuGendazPromocaoNotificacaoRepository notificacaoRepository;
    private final MeuGendazPromocaoSyncService syncService;
    private final PromocaoRepository adminPromocaoRepository;

    @Transactional
    public List<PromocaoClienteResponse> listarPromocoes(ClienteEntity cliente) {
        Long empresaId = cliente.getEmpresa().getId();
        try {
            syncService.sincronizarEmpresa(empresaId);
        } catch (Exception e) {
            log.warn("[meu-gendaz] falha ao sincronizar promocoes da empresa {}: {}", empresaId, e.getMessage());
        }
        try {
            garantirNotificacoes(cliente);
        } catch (Exception e) {
            log.warn("[meu-gendaz] falha ao garantir notificacoes do cliente {}: {}", cliente.getId(), e.getMessage());
        }
        LocalDateTime agora = LocalDateTime.now();
        return adminPromocaoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream()
                .filter(PromocaoEntity::estaAtiva)
                .filter(p -> {
                    boolean dentroPeriodo = p.getDataInicio() == null || p.getDataFim() == null
                            || (!agora.isBefore(p.getDataInicio()) && !agora.isAfter(p.getDataFim()));
                    boolean dentroLimite = p.getQuantidadeLimite() == null || p.getQuantidadeUsada() == null
                            || p.getQuantidadeUsada() < p.getQuantidadeLimite();
                    return dentroPeriodo && dentroLimite;
                })
                .map(p -> toPromocaoClienteResponse(cliente, p))
                .toList();
    }

    private PromocaoClienteResponse toPromocaoClienteResponse(ClienteEntity cliente, PromocaoEntity p) {
        Long mirrorId = promocaoRepository.findByEmpresaIdAndPromocaoOrigemId(cliente.getEmpresa().getId(), p.getId())
                .map(MeuGendazPromocaoEntity::getId)
                .orElseGet(() -> promocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(cliente.getEmpresa().getId(), p.getCodigo().trim())
                        .map(MeuGendazPromocaoEntity::getId)
                        .orElse(null));
        boolean jaUsou = mirrorId != null && usoRepository.existsByPromocaoIdAndClienteId(mirrorId, cliente.getId());
        Set<ServicoEntity> servicos = p.getServicos() == null ? Set.of() : p.getServicos();
        return new PromocaoClienteResponse(
                p.getId(),
                p.getCodigo(),
                p.getDescricao(),
                p.getTipo() == null ? null : p.getTipo().name(),
                p.getValor(),
                p.getDataFim(),
                p.getAplicarTodosServicos(),
                servicos.stream().map(this::servicoParaMapa).toList(),
                jaUsou,
                true
        );
    }

    @Transactional(readOnly = true)
    public List<PromocaoUsadaResponse> listarUsadas(ClienteEntity cliente) {
        return usoRepository.findByClienteIdOrderByDataUsoDesc(cliente.getId()).stream()
                .map(uso -> new PromocaoUsadaResponse(
                        uso.getPromocao().getCodigo(),
                        uso.getPromocao().getDescricao(),
                        uso.getValorDesconto(),
                        uso.getDataUso(),
                        uso.getAgendamentoId()
                ))
                .toList();
    }

    @Transactional
    public List<PromocaoNotificacaoResponse> listarNotificacoesNaoLidas(ClienteEntity cliente) {
        syncService.sincronizarEmpresa(cliente.getEmpresa().getId());
        garantirNotificacoes(cliente);
        return notificacaoRepository.findByClienteIdAndLidoFalseOrderByDataEnvioDesc(cliente.getId()).stream()
                .filter(notif -> notif.getPromocao() != null && notif.getPromocao().isValida())
                .map(notif -> new PromocaoNotificacaoResponse(
                        notif.getPromocao().getId(),
                        notif.getPromocao().getCodigo(),
                        notif.getPromocao().getDescricao(),
                        notif.getDataEnvio()
                ))
                .toList();
    }

    public void marcarNotificacaoComoLida(ClienteEntity cliente, Long promocaoId) {
        notificacaoRepository.findByPromocaoIdAndClienteId(promocaoId, cliente.getId()).ifPresent(notif -> {
            notif.setLido(true);
            notif.setDataLeitura(LocalDateTime.now());
            notificacaoRepository.save(notif);
        });
    }

    public BigDecimal validarERegistrarUso(ClienteEntity cliente, EmpresaEntity empresa, ServicoEntity servico, String cupomCodigo, Long agendamentoId) {
        MeuGendazPromocaoEntity promocao = validarCupom(cliente, empresa, servico, cupomCodigo);
        BigDecimal desconto = calcularDesconto(servico, promocao);
        if (agendamentoId != null) {
            registrarUso(cliente, promocao, agendamentoId, desconto);
        }
        return desconto;
    }

    public MeuGendazPromocaoEntity validarCupom(ClienteEntity cliente, EmpresaEntity empresa, ServicoEntity servico, String cupomCodigo) {
        if (cupomCodigo == null || cupomCodigo.isBlank()) {
            return null;
        }
        String codigoNormalizado = cupomCodigo.trim();
        try {
            syncService.sincronizarEmpresa(empresa.getId());
        } catch (Exception e) {
            log.warn("[meu-gendaz] falha ao sincronizar empresa {} para validar cupom: {}", empresa.getId(), e.getMessage());
        }
        Optional<MeuGendazPromocaoEntity> opt = promocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(empresa.getId(), codigoNormalizado);
        if (opt.isEmpty()) {
            Long mirrorId = syncERegistrarMirror(empresa.getId(), codigoNormalizado);
            opt = mirrorId != null ? promocaoRepository.findById(mirrorId) : Optional.empty();
        }
        MeuGendazPromocaoEntity promocao = opt
                .orElseThrow(() -> new IllegalArgumentException("Cupom invalido."));
        if (!promocao.isValida()) {
            throw new IllegalArgumentException("Cupom expirado ou invalido.");
        }
        if (usoRepository.existsByPromocaoIdAndClienteId(promocao.getId(), cliente.getId())) {
            throw new IllegalArgumentException("Voce ja usou este cupom.");
        }
        if (!Boolean.TRUE.equals(promocao.getAplicarTodosServicos())
                && promocao.getServicos().stream().noneMatch(s -> s.getId().equals(servico.getId()))) {
            throw new IllegalArgumentException("Este cupom nao e valido para este servico.");
        }
        return promocao;
    }

    private Long syncERegistrarMirror(Long empresaId, String codigoNormalizado) {
        Optional<PromocaoEntity> admin = adminPromocaoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream()
                .filter(p -> p.getCodigo() != null && p.getCodigo().trim().equalsIgnoreCase(codigoNormalizado))
                .findFirst();
        if (admin.isPresent()) {
            try {
                syncService.sincronizarPromocao(empresaId, admin.get().getId());
            } catch (Exception e) {
                log.warn("[meu-gendaz] falha ao sincronizar cupom {}: {}", codigoNormalizado, e.getMessage());
            }
        }
        return promocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(empresaId, codigoNormalizado)
                .map(MeuGendazPromocaoEntity::getId)
                .orElse(null);
    }

    public BigDecimal calcularDesconto(ServicoEntity servico, MeuGendazPromocaoEntity promocao) {
        if (promocao == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal desconto = "PERCENTUAL".equalsIgnoreCase(promocao.getTipo())
                ? servico.getValor().multiply(promocao.getValor()).divide(new BigDecimal("100"))
                : promocao.getValor();
        if (desconto.compareTo(servico.getValor()) > 0) {
            desconto = servico.getValor();
        }
        return desconto;
    }

    public void registrarUso(ClienteEntity cliente, MeuGendazPromocaoEntity promocao, Long agendamentoId, BigDecimal desconto) {
        if (promocao == null) {
            return;
        }
        usoRepository.save(MeuGendazPromocaoUsoEntity.builder()
                .promocao(promocao)
                .cliente(cliente)
                .agendamentoId(agendamentoId)
                .valorDesconto(desconto)
                .dataUso(LocalDateTime.now())
                .build());
        promocao.setQuantidadeUsada((promocao.getQuantidadeUsada() == null ? 0 : promocao.getQuantidadeUsada()) + 1);
        promocaoRepository.save(promocao);
    }

    private Map<String, Object> servicoParaMapa(ServicoEntity servico) {
        return Map.of(
                "id", servico.getId(),
                "nome", servico.getNome()
        );
    }

    private void garantirNotificacoes(ClienteEntity cliente) {
        List<MeuGendazPromocaoEntity> promocoesAtivas = promocaoRepository.findByEmpresaIdAndStatusOrderByDataCriacaoDesc(
                cliente.getEmpresa().getId(),
                com.minhaempresa.agendapro.shared.enums.StatusCadastro.ATIVO
        ).stream().filter(MeuGendazPromocaoEntity::isValida).toList();
        for (MeuGendazPromocaoEntity promocao : promocoesAtivas) {
            if (!notificacaoRepository.existsByPromocaoIdAndClienteId(promocao.getId(), cliente.getId())) {
                notificacaoRepository.save(MeuGendazPromocaoNotificacaoEntity.builder()
                        .promocao(promocao)
                        .cliente(cliente)
                        .lido(false)
                        .dataEnvio(LocalDateTime.now())
                        .build());
            }
        }
    }
}
