package com.minhaempresa.agendapro.plano.service;

import com.minhaempresa.agendapro.plano.entity.PlanoEntity;
import com.minhaempresa.agendapro.plano.enums.StatusPlano;
import com.minhaempresa.agendapro.plano.repository.PlanoRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanoBootstrap implements CommandLineRunner {
    public static final BigDecimal VALOR_BASICO_MENSAL = new BigDecimal("39.00");
    public static final BigDecimal VALOR_PRO_MENSAL = new BigDecimal("89.00");

    private final PlanoRepository planoRepository;

    @Override
    public void run(String... args) {
        garantirPlano("BASICO", "Agenda, clientes e servicos.", VALOR_BASICO_MENSAL);
        garantirPlano("PRO", "Agenda com financeiro, pagamentos e relatorios.", VALOR_PRO_MENSAL);
    }

    private void garantirPlano(String nome, String descricao, BigDecimal valorMensal) {
        PlanoEntity plano = planoRepository.findByNome(nome).orElseGet(() -> PlanoEntity.builder()
                .nome(nome)
                .build());
        plano.setDescricao(descricao);
        plano.setValorMensal(valorMensal);
        plano.setStatus(StatusPlano.ATIVO);
        planoRepository.save(plano);
    }
}
