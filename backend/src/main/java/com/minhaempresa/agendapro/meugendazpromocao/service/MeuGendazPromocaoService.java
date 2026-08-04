package com.minhaempresa.agendapro.meugendazpromocao.service;

import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.meugendazpromocao.dto.MeuGendazPromocaoDtos.*;
import com.minhaempresa.agendapro.meugendazpromocao.entity.*;
import com.minhaempresa.agendapro.meugendazpromocao.repository.*;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MeuGendazPromocaoService {
    private final MeuGendazPromocaoRepository promocaoRepository;
    private final MeuGendazPromocaoUsoRepository usoRepository;
    private final MeuGendazPromocaoNotificacaoRepository notificacaoRepository;

    public List<PromocaoClienteResponse> listarPromocoes(ClienteEntity cliente) {
        garantirNotificacoes(cliente);
        return promocaoRepository.findByEmpresaIdAndStatusOrderByDataCriacaoDesc(cliente.getEmpresa().getId(), com.minhaempresa.agendapro.shared.enums.StatusCadastro.ATIVO)
                .stream()
                .filter(MeuGendazPromocaoEntity::isValida)
                .map(promocao -> new PromocaoClienteResponse(
                        promocao.getId(),
                        promocao.getCodigo(),
                        promocao.getDescricao(),
                        promocao.getTipo(),
                        promocao.getValor(),
                        promocao.getDataFim(),
                        promocao.getAplicarTodosServicos(),
                        promocao.getServicos().stream().map(this::servicoParaMapa).toList(),
                        usoRepository.existsByPromocaoIdAndClienteId(promocao.getId(), cliente.getId()),
                        promocao.isValida()
                ))
                .toList();
    }

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

    public List<PromocaoNotificacaoResponse> listarNotificacoesNaoLidas(ClienteEntity cliente) {
        garantirNotificacoes(cliente);
        return notificacaoRepository.findByClienteIdAndLidoFalseOrderByDataEnvioDesc(cliente.getId()).stream()
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
        MeuGendazPromocaoEntity promocao = promocaoRepository.findByEmpresaIdAndCodigoIgnoreCase(empresa.getId(), cupomCodigo.trim())
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
