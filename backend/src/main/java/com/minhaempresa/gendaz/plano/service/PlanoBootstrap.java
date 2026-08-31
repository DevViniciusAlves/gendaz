package com.minhaempresa.gendaz.plano.service;

import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.enums.StatusPlano;
import com.minhaempresa.gendaz.plano.repository.PlanoRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanoBootstrap implements CommandLineRunner {
    public static final BigDecimal VALOR_BASICO_MENSAL = new BigDecimal("29.90");
    public static final BigDecimal VALOR_PRO_MENSAL = new BigDecimal("79.90");
    public static final BigDecimal VALOR_PLUS_MENSAL = new BigDecimal("109.90");
    public static final BigDecimal VALOR_ENTERPRISE_MENSAL = new BigDecimal("149.90");

    private final PlanoRepository planoRepository;

    @Override
    public void run(String... args) {
        garantirPlano("BASICO", "Financeiro - Pagamentos automatizados - Relatórios | Histórico ilimitado | Agendamentos ilimitados | Confirmação de agendamentos | Não inclui: CRM integrado, Insights, Até 3 usuários, Financeiro completo", VALOR_BASICO_MENSAL);
        garantirPlano("PRO", "Tudo do Plano básico + Até 3 usuários na conta | CRM integrado | Insights com GendazIA no controle | Financeiro completo: caixa, despesas pagamentos automatizados", VALOR_PRO_MENSAL);
        garantirPlano("PLUS", "Tudo do Plano Pro + Até 7 usuários na conta | CRM integrado | Insights com GendazIA no controle | Financeiro completo: caixa, despesas pagamentos automatizados", VALOR_PLUS_MENSAL);
        garantirPlano("ENTERPRISE", "Tudo do Plano Plus + Até 15 usuários na conta | CRM integrado | Insights com GendazIA no controle | Financeiro completo: caixa, despesas pagamentos automatizados", VALOR_ENTERPRISE_MENSAL);
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

