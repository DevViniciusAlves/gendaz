package com.minhaempresa.gendaz.meugendazpromocao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.meugendazpromocao.entity.MeuGendazPromocaoEntity;
import com.minhaempresa.gendaz.meugendazpromocao.repository.MeuGendazPromocaoRepository;
import com.minhaempresa.gendaz.meugendazpromocao.repository.MeuGendazPromocaoUsoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.promocao.entity.PromocaoEntity;
import com.minhaempresa.gendaz.promocao.enums.TipoPromocao;
import com.minhaempresa.gendaz.promocao.repository.PromocaoRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CupomConcorrenciaIntegrationTest {

    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private ProfissionalRepository profissionalRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private MeuGendazPromocaoRepository promocaoRepository;
    @Autowired
    private MeuGendazPromocaoUsoRepository usoRepository;
    @Autowired
    private MeuGendazPromocaoService promocaoService;
    @Autowired
    private PromocaoRepository promocaoAdminRepository;

    @Test
    void ultimoUsoDisponivelSoUmaRequisicaoConcorrenteConsome() throws Exception {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Loja Iron").email("loja@iron.com").status(StatusEmpresa.ATIVA).build());
        ClienteEntity c1 = clienteRepository.save(ClienteEntity.builder()
                .nome("Ana").telefone("65990000001").email("ana@x.com").empresa(empresa).status(StatusCadastro.ATIVO).build());
        ClienteEntity c2 = clienteRepository.save(ClienteEntity.builder()
                .nome("Bia").telefone("65990000002").email("bia@x.com").empresa(empresa).status(StatusCadastro.ATIVO).build());
        ServicoEntity servico = servicoRepository.save(ServicoEntity.builder()
                .nome("Corte").duracaoMinutos(30).valor(new BigDecimal("100.00"))
                .status(StatusCadastro.ATIVO).empresa(empresa).build());
        MeuGendazPromocaoEntity promocao = promocaoRepository.save(MeuGendazPromocaoEntity.builder()
                .empresa(empresa)
                .codigo("TESTE50")
                .descricao("Cupom teste")
                .tipo("VALOR_FIXO")
                .valor(new BigDecimal("50.00"))
                .dataInicio(LocalDateTime.now().minusDays(1))
                .dataFim(LocalDateTime.now().plusDays(1))
                .quantidadeLimite(1)
                .quantidadeUsada(0)
                .status(StatusCadastro.ATIVO)
                .aplicarTodosServicos(true)
                .build());
        promocaoAdminRepository.save(PromocaoEntity.builder()
                .empresa(empresa)
                .codigo("TESTE50")
                .descricao("Cupom admin")
                .tipo(TipoPromocao.VALOR_FIXO)
                .valor(new BigDecimal("50.00"))
                .dataInicio(LocalDateTime.now().minusDays(1))
                .dataFim(LocalDateTime.now().plusDays(1))
                .quantidadeLimite(1)
                .quantidadeUsada(0)
                .status(StatusCadastro.ATIVO)
                .aplicarTodosServicos(true)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger falhas = new AtomicInteger();

        Future<?> f1 = executor.submit(() -> {
            try {
                inicio.await();
                promocaoService.aplicarCupomAoAgendamento(c1, empresa, servico, "TESTE50", 100001L);
                sucessos.incrementAndGet();
            } catch (Throwable t) {
                falhas.incrementAndGet();
            }
        });
        Future<?> f2 = executor.submit(() -> {
            try {
                inicio.await();
                promocaoService.aplicarCupomAoAgendamento(c2, empresa, servico, "TESTE50", 100002L);
                sucessos.incrementAndGet();
            } catch (Throwable t) {
                falhas.incrementAndGet();
            }
        });
        inicio.countDown();
        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals(1, sucessos.get());
        assertEquals(1, falhas.get());
        MeuGendazPromocaoEntity atualizada = promocaoRepository.findById(promocao.getId()).orElseThrow();
        assertEquals(1, atualizada.getQuantidadeUsada());
        long usos = usoRepository.findAll().stream()
                .filter(uso -> uso.getPromocao() != null && uso.getPromocao().getId().equals(promocao.getId()))
                .count();
        assertEquals(1, usos);
    }

    @Test
    void mesmoClienteNaoRepeteCupom() {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Loja Reuso").email("reuso@iron.com").status(StatusEmpresa.ATIVA).build());
        ClienteEntity cliente = clienteRepository.save(ClienteEntity.builder()
                .nome("Carla").telefone("65990000003").email("carla@x.com").empresa(empresa).status(StatusCadastro.ATIVO).build());
        ServicoEntity servico = servicoRepository.save(ServicoEntity.builder()
                .nome("Unha").duracaoMinutos(45).valor(new BigDecimal("80.00"))
                .status(StatusCadastro.ATIVO).empresa(empresa).build());
        promocaoRepository.save(MeuGendazPromocaoEntity.builder()
                .empresa(empresa)
                .codigo("UMAVEZ")
                .descricao("Uso unico")
                .tipo("VALOR_FIXO")
                .valor(new BigDecimal("20.00"))
                .dataInicio(LocalDateTime.now().minusDays(1))
                .dataFim(LocalDateTime.now().plusDays(1))
                .quantidadeLimite(10)
                .quantidadeUsada(0)
                .status(StatusCadastro.ATIVO)
                .aplicarTodosServicos(true)
                .build());
        promocaoAdminRepository.save(PromocaoEntity.builder()
                .empresa(empresa)
                .codigo("UMAVEZ")
                .descricao("Cupom admin")
                .tipo(TipoPromocao.VALOR_FIXO)
                .valor(new BigDecimal("20.00"))
                .dataInicio(LocalDateTime.now().minusDays(1))
                .dataFim(LocalDateTime.now().plusDays(1))
                .quantidadeLimite(10)
                .quantidadeUsada(0)
                .status(StatusCadastro.ATIVO)
                .aplicarTodosServicos(true)
                .build());

        promocaoService.aplicarCupomAoAgendamento(cliente, empresa, servico, "UMAVEZ", 200001L);

        assertThrows(IllegalArgumentException.class,
                () -> promocaoService.aplicarCupomAoAgendamento(cliente, empresa, servico, "UMAVEZ", 200002L));
    }

    @Test
    void resumoServicosUsaValorFinalPersistidoNaoPrecoAtual() {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Loja Valor").email("valor@iron.com").status(StatusEmpresa.ATIVA).build());
        ClienteEntity cliente = clienteRepository.save(ClienteEntity.builder()
                .nome("Duda").telefone("65990000004").email("duda@x.com").empresa(empresa).status(StatusCadastro.ATIVO).build());
        ServicoEntity servico = servicoRepository.save(ServicoEntity.builder()
                .nome("Massagem").duracaoMinutos(60).valor(new BigDecimal("100.00"))
                .status(StatusCadastro.ATIVO).empresa(empresa).build());
        ProfissionalEntity profissional = profissionalRepository.save(ProfissionalEntity.builder()
                .nome("Prof A").status(StatusCadastro.ATIVO).empresa(empresa).build());

        AgendamentoEntity agendamento = AgendamentoEntity.builder()
                .cliente(cliente).servico(servico).profissional(profissional).empresa(empresa)
                .data(LocalDate.now()).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(10, 0))
                .status(StatusAgendamento.FINALIZADO)
                .protocolo("888881")
                .valorOriginal(new BigDecimal("100.00"))
                .valorDesconto(new BigDecimal("60.00"))
                .valorFinal(new BigDecimal("40.00"))
                .build();
        agendamentoRepository.save(agendamento);

        servico.setValor(new BigDecimal("150.00")); // preco mudou depois, snapshot deve valer 40

        List<Object[]> resumo = agendamentoRepository.resumoServicosMaisAgendados(
                empresa.getId(), StatusAgendamento.CANCELADO, PageRequest.of(0, 5));

        assertEquals(1, resumo.size());
        Number valor = (Number) resumo.get(0)[3];
        assertEquals(0, new BigDecimal("40.00").compareTo(BigDecimal.valueOf(valor.doubleValue())));
    }
}