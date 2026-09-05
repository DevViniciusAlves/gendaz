package com.minhaempresa.gendaz.meugendazpromocao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.meugendazpromocao.entity.MeuGendazPromocaoEntity;
import com.minhaempresa.gendaz.meugendazpromocao.repository.MeuGendazPromocaoRepository;
import com.minhaempresa.gendaz.meugendazpromocao.repository.MeuGendazPromocaoUsoRepository;
import com.minhaempresa.gendaz.promocao.dto.PromocaoDtos.PromocaoUsoResponse;
import com.minhaempresa.gendaz.promocao.entity.PromocaoEntity;
import com.minhaempresa.gendaz.promocao.enums.TipoPromocao;
import com.minhaempresa.gendaz.promocao.repository.PromocaoRepository;
import com.minhaempresa.gendaz.promocao.service.PromocaoService;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CupomFluxoIntegrationTest {

    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private MeuGendazPromocaoRepository mirrorRepository;
    @Autowired
    private MeuGendazPromocaoUsoRepository mirrorUsoRepository;
    @Autowired
    private MeuGendazPromocaoService cupomService;
    @Autowired
    private MeuGendazPromocaoSyncService syncService;
    @Autowired
    private PromocaoRepository adminRepository;
    @Autowired
    private PromocaoService adminService;

    @AfterEach
    void limparContexto() {
        CompanyContext.clear();
    }

    private EmpresaEntity novaEmpresa(String nome, String email) {
        return empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia(nome).email(email).status(StatusEmpresa.ATIVA).build());
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa, String nome, String email, String telefone) {
        return clienteRepository.save(ClienteEntity.builder()
                .nome(nome).telefone(telefone).email(email).empresa(empresa).status(StatusCadastro.ATIVO).build());
    }

    private ServicoEntity novoServico(EmpresaEntity empresa, String nome) {
        return servicoRepository.save(ServicoEntity.builder()
                .nome(nome).duracaoMinutos(30).valor(new BigDecimal("100.00"))
                .status(StatusCadastro.ATIVO).empresa(empresa).build());
    }

    private PromocaoEntity novaPromocaoAdmin(EmpresaEntity empresa, String codigo, Integer limite) {
        return adminRepository.save(PromocaoEntity.builder()
                .empresa(empresa)
                .codigo(codigo)
                .descricao("Cupom admin")
                .tipo(TipoPromocao.VALOR_FIXO)
                .valor(new BigDecimal("50.00"))
                .dataInicio(LocalDateTime.now().minusDays(1))
                .dataFim(LocalDateTime.now().plusDays(1))
                .quantidadeLimite(limite)
                .quantidadeUsada(0)
                .status(StatusCadastro.ATIVO)
                .aplicarTodosServicos(true)
                .build());
    }

    @Test
    void limiteUmPermiteUmUsoDepoisEsgotaListagemEPainel() {
        EmpresaEntity empresa = novaEmpresa("Loja Fluxo", "fluxo@iron.com");
        ClienteEntity clienteA = novoCliente(empresa, "Cli A", "a@x.com", "65990000101");
        ClienteEntity clienteB = novoCliente(empresa, "Cli B", "b@x.com", "65990000102");
        ServicoEntity servico = novoServico(empresa, "Corte");
        PromocaoEntity admin = novaPromocaoAdmin(empresa, "FLUXO1", 1);
        syncService.sincronizarPromocao(empresa.getId(), admin.getId());
        CompanyContext.setCompanyId(empresa.getId());

        // Listagem do cliente mostra a promocao como disponivel antes do uso.
        assertTrue(cupomService.listarPromocoes(clienteA).stream()
                .anyMatch(p -> "FLUXO1".equals(p.codigo()) && Boolean.TRUE.equals(p.valida())));

        // Primeiro uso: sucesso.
        cupomService.aplicarCupomAoAgendamento(clienteA, empresa, servico, "FLUXO1", 300001L);

        MeuGendazPromocaoEntity mirror = mirrorRepository
                .findByEmpresaIdAndPromocaoOrigemId(empresa.getId(), admin.getId()).orElseThrow();
        assertEquals(1, mirror.getQuantidadeUsada());
        assertEquals(1, mirrorUsoRepository.countByPromocaoId(mirror.getId()));

        // Painel administrativo reflete o uso real: 1/1, historico com 1 uso.
        var listados = adminService.listar(empresa.getId());
        assertEquals(1, listados.stream().filter(p -> "FLUXO1".equals(p.codigo()))
                .findFirst().orElseThrow().quantidadeUsada());
        List<PromocaoUsoResponse> usos = adminService.listarUsos(empresa.getId(), admin.getId());
        assertEquals(1, usos.size());
        assertEquals(admin.getId(), usos.get(0).promocaoId());
        assertEquals(1, adminService.resumo(empresa.getId(), admin.getId()).totalUsos());

        // Listagem do cliente nao apresenta mais como valida/utilizavel.
        assertTrue(cupomService.listarPromocoes(clienteB).stream()
                .noneMatch(p -> "FLUXO1".equals(p.codigo()) && Boolean.TRUE.equals(p.valida())));

        // Segundo uso: bloqueado com erro de negocio 409, sem consumir nada.
        ConflictException ex = assertThrows(ConflictException.class,
                () -> cupomService.aplicarCupomAoAgendamento(clienteB, empresa, servico, "FLUXO1", 300002L));
        assertEquals("Este cupom atingiu o limite de utilizações.", ex.getMessage());
        assertEquals(1, mirrorRepository.findById(mirror.getId()).orElseThrow().getQuantidadeUsada());
        assertEquals(1, mirrorUsoRepository.countByPromocaoId(mirror.getId()));
    }

    @Test
    void edicaoDaPromocaoNaoResetaUsosHistoricos() {
        EmpresaEntity empresa = novaEmpresa("Loja Edicao", "edicao@iron.com");
        ClienteEntity cliente = novoCliente(empresa, "Cli E", "e@x.com", "65990000103");
        ServicoEntity servico = novoServico(empresa, "Barba");
        PromocaoEntity admin = novaPromocaoAdmin(empresa, "EDIT1", 10);
        syncService.sincronizarPromocao(empresa.getId(), admin.getId());

        for (int i = 0; i < 5; i++) {
            ClienteEntity c = novoCliente(empresa, "Cli " + i, "c" + i + "@x.com", "6599000020" + i);
            cupomService.aplicarCupomAoAgendamento(c, empresa, servico, "EDIT1", 400000L + i);
        }
        MeuGendazPromocaoEntity mirror = mirrorRepository
                .findByEmpresaIdAndPromocaoOrigemId(empresa.getId(), admin.getId()).orElseThrow();
        assertEquals(5, mirror.getQuantidadeUsada());

        // Edicao administrativa (descricao + limite menor que o usado) + sync.
        admin.setDescricao("Nova descricao");
        admin.setQuantidadeLimite(3);
        adminRepository.save(admin);
        syncService.sincronizarPromocao(empresa.getId(), admin.getId());

        MeuGendazPromocaoEntity apos = mirrorRepository.findById(mirror.getId()).orElseThrow();
        assertEquals(5, apos.getQuantidadeUsada());
        assertEquals(3, apos.getQuantidadeLimite());
        assertEquals(5, mirrorUsoRepository.countByPromocaoId(mirror.getId()));

        // Promocao fica esgotada (5 >= 3): novo uso rejeitado como conflito.
        ConflictException ex = assertThrows(ConflictException.class,
                () -> cupomService.aplicarCupomAoAgendamento(cliente, empresa, servico, "EDIT1", 400010L));
        assertEquals("Este cupom atingiu o limite de utilizações.", ex.getMessage());

        // Reabrir o limite volta a ter disponibilidade.
        admin.setQuantidadeLimite(10);
        adminRepository.save(admin);
        syncService.sincronizarPromocao(empresa.getId(), admin.getId());
        cupomService.aplicarCupomAoAgendamento(cliente, empresa, servico, "EDIT1", 400011L);
        assertEquals(6, mirrorRepository.findById(mirror.getId()).orElseThrow().getQuantidadeUsada());
    }

    @Test
    void errosDeCupomUsamExcecoesDeNegocio() {
        EmpresaEntity empresa = novaEmpresa("Loja Erros", "erros@iron.com");
        ClienteEntity cliente = novoCliente(empresa, "Cli X", "x@x.com", "65990000104");
        ServicoEntity servico = novoServico(empresa, "Corte");
        novaPromocaoAdmin(empresa, "ERRO1", null);
        syncService.sincronizarEmpresa(empresa.getId());

        // Codigo inexistente: 400.
        BusinessException inexistente = assertThrows(BusinessException.class,
                () -> cupomService.aplicarCupomAoAgendamento(cliente, empresa, servico, "NAOEXISTE", 500001L));
        assertEquals("Cupom inválido.", inexistente.getMessage());

        // Servico nao permitido: 400, contador intacto.
        ServicoEntity outro = novoServico(empresa, "Outro");
        PromocaoEntity restrita = adminRepository.save(PromocaoEntity.builder()
                .empresa(empresa).codigo("RESTRITA").descricao("Restrita")
                .tipo(TipoPromocao.VALOR_FIXO).valor(new BigDecimal("10.00"))
                .dataInicio(LocalDateTime.now().minusDays(1)).dataFim(LocalDateTime.now().plusDays(1))
                .quantidadeLimite(null).quantidadeUsada(0).status(StatusCadastro.ATIVO)
                .aplicarTodosServicos(false)
                .servicos(new java.util.HashSet<>(java.util.Set.of(outro)))
                .build());
        syncService.sincronizarPromocao(empresa.getId(), restrita.getId());
        BusinessException servicoErrado = assertThrows(BusinessException.class,
                () -> cupomService.aplicarCupomAoAgendamento(cliente, empresa, servico, "RESTRITA", 500002L));
        assertEquals("Este cupom não é válido para este serviço.", servicoErrado.getMessage());
        MeuGendazPromocaoEntity mirrorRestrita = mirrorRepository
                .findByEmpresaIdAndPromocaoOrigemId(empresa.getId(), restrita.getId()).orElseThrow();
        assertEquals(0, mirrorRestrita.getQuantidadeUsada());
    }
}
