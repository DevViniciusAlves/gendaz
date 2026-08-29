package com.minhaempresa.gendaz.shared;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.auth.service.PasswordService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.conversa.entity.ConversaEntity;
import com.minhaempresa.gendaz.conversa.enums.StatusConversa;
import com.minhaempresa.gendaz.conversa.repository.ConversaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.mensagem.entity.MensagemEntity;
import com.minhaempresa.gendaz.mensagem.enums.DirecaoMensagem;
import com.minhaempresa.gendaz.mensagem.enums.TipoMensagem;
import com.minhaempresa.gendaz.mensagem.repository.MensagemRepository;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.enums.StatusPlano;
import com.minhaempresa.gendaz.plano.repository.PlanoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "APP_SEED_TEST_DATA", havingValue = "true")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlanoRepository planoRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final ClienteRepository clienteRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final PasswordService passwordService;

    @Override
    public void run(String... args) {
        if (empresaRepository.count() > 0) {
            return;
        }

        PlanoEntity basico = planoRepository.findByNome("BASICO")
                .orElseGet(() -> planoRepository.save(new PlanoEntity(null, "BASICO", "Agenda, clientes e servicos.", new BigDecimal("29.90"), StatusPlano.ATIVO)));
        PlanoEntity pro = planoRepository.findByNome("PRO")
                .orElseGet(() -> planoRepository.save(new PlanoEntity(null, "PRO", "Agenda com financeiro, pagamentos e relatorios.", new BigDecimal("79.90"), StatusPlano.ATIVO)));

        EmpresaEntity empresaBasico = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Gendaz Matriz")
                .telefone("5565993360300")
                .email("contato@Gendaz.com")
                .status(StatusEmpresa.ATIVA)
                .build());

        EmpresaEntity empresaPro = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Gendaz Premium")
                .telefone("5565988880000")
                .email("premium@Gendaz.com")
                .status(StatusEmpresa.ATIVA)
                .build());

        usuarioRepository.save(UsuarioEntity.builder()
                .nome("Ana Basico")
                .email("basico@Gendaz.com")
                .senha(passwordService.hash("Senha123!"))
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .aceitouTermos(true)
                .dataAceiteTermos(LocalDateTime.now())
                .versaoTermos("2026-06-17")
                .empresa(empresaBasico)
                .build());

        usuarioRepository.save(UsuarioEntity.builder()
                .nome("Bruno Pro")
                .email("pro@Gendaz.com")
                .senha(passwordService.hash("Senha123!"))
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .aceitouTermos(true)
                .dataAceiteTermos(LocalDateTime.now())
                .versaoTermos("2026-06-17")
                .empresa(empresaPro)
                .build());

        assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresaBasico)
                .plano(basico)
                .status(StatusAssinatura.TESTE)
                .dataInicio(LocalDate.now())
                .dataFim(LocalDate.now().plusDays(7))
                .dataInicioTeste(LocalDate.now())
                .dataFimTeste(LocalDate.now().plusDays(7))
                .build());

        AssinaturaEntity assinaturaPro = assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresaPro)
                .plano(pro)
                .status(StatusAssinatura.ATIVA)
                .dataInicio(LocalDate.now())
                .dataFim(LocalDate.now().plusMonths(1))
                .build());

        pagamentoPlanoRepository.save(PagamentoPlanoEntity.builder()
                .empresa(empresaPro)
                .plano(pro)
                .assinatura(assinaturaPro)
                .valor(pro.getValorMensal())
                .metodoPagamento(MetodoPagamento.PIX)
                .status(StatusPagamento.PAYMENT_APPROVED)
                .provider("local")
                .providerPaymentId("seed-pro-" + empresaPro.getId())
                .checkoutUrl("http://localhost:5173/sistema/planos")
                .dataExpiracao(LocalDateTime.now().plusMinutes(30))
                .dataPagamento(LocalDateTime.now())
                .build());

        EmpresaEntity empresaTeste = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("AgendNew Testes")
                .telefone("5565999990000")
                .email("teste@agendnew.com")
                .status(StatusEmpresa.ATIVA)
                .build());

        AssinaturaEntity assinaturaTeste = assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresaTeste)
                .plano(pro)
                .status(StatusAssinatura.ATIVA)
                .dataInicio(LocalDate.now())
                .dataFim(LocalDate.now().plusMonths(1))
                .build());

        pagamentoPlanoRepository.save(PagamentoPlanoEntity.builder()
                .empresa(empresaTeste)
                .plano(pro)
                .assinatura(assinaturaTeste)
                .valor(pro.getValorMensal())
                .metodoPagamento(MetodoPagamento.PIX)
                .status(StatusPagamento.PAYMENT_APPROVED)
                .provider("local")
                .providerPaymentId("seed-teste-" + empresaTeste.getId())
                .checkoutUrl("http://localhost:5173/sistema/planos")
                .dataExpiracao(LocalDateTime.now().plusMinutes(30))
                .dataPagamento(LocalDateTime.now())
                .build());

        usuarioRepository.save(UsuarioEntity.builder()
                .nome("Vinicius Henrique")
                .email("vini@gmail.com")
                .senha(passwordService.hash("Vini101010#"))
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .aceitouTermos(true)
                .dataAceiteTermos(LocalDateTime.now())
                .versaoTermos("2026-06-17")
                .empresa(empresaTeste)
                .build());

        seedDemoData(empresaBasico);
        seedDemoData(empresaPro);
        seedDemoData(empresaTeste);
    }

    private void seedDemoData(EmpresaEntity empresa) {
        ClienteEntity cliente = clienteRepository.save(ClienteEntity.builder()
                .nome(empresa.getNomeFantasia() + " Cliente")
                .telefone("5565999111111")
                .email("cliente@" + empresa.getId() + ".com")
                .observações("Cliente de teste.")
                .empresa(empresa)
                .build());

        ServicoEntity servico = servicoRepository.save(ServicoEntity.builder()
                .nome("Consulta")
                .descrição("Atendimento completo")
                .duracaoMinutos(60)
                .valor(new BigDecimal("180.00"))
                .status(StatusCadastro.ATIVO)
                .empresa(empresa)
                .build());

        ProfissionalEntity profissional = profissionalRepository.save(ProfissionalEntity.builder()
                .nome("Dra. Marina")
                .especialidade("Clinica geral")
                .telefone("5565988881111")
                .status(StatusCadastro.ATIVO)
                .empresa(empresa)
                .build());

        AgendamentoEntity agendamento = agendamentoRepository.save(AgendamentoEntity.builder()
                .cliente(cliente)
                .servico(servico)
                .profissional(profissional)
                .empresa(empresa)
                .data(LocalDate.now())
                .horaInicio(LocalTime.of(9, 0))
                .horaFim(LocalTime.of(10, 0))
                .status(StatusAgendamento.CONFIRMADO)
                .observações("Chegar 10 minutos antes.")
                .build());

        ConversaEntity conversa = conversaRepository.save(ConversaEntity.builder()
                .cliente(cliente)
                .empresa(empresa)
                .status(StatusConversa.ABERTA)
                .ultimaMensagem("Pode confirmar minha consulta?")
                .dataUltimaMensagem(LocalDateTime.now())
                .build());

        mensagemRepository.save(MensagemEntity.builder()
                .conversa(conversa)
                .conteudo("Ola, gostaria de horarios para hoje.")
                .direcao(DirecaoMensagem.CLIENTE_PARA_EMPRESA)
                .tipo(TipoMensagem.TEXTO)
                .dataEnvio(LocalDateTime.now().minusMinutes(20))
                .build());

        pagamentoRepository.save(PagamentoEntity.builder()
                .agendamento(agendamento)
                .cliente(cliente)
                .empresa(empresa)
                .valor(new BigDecimal("180.00"))
                .metodoPagamento(MetodoPagamento.PIX)
                .status(StatusPagamento.PAGO)
                .dataPagamento(LocalDateTime.now())
                .build());
    }
}

