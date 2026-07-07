package com.minhaempresa.agendapro.shared;

import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.agendapro.assinatura.enums.StatusAssinatura;
import com.minhaempresa.agendapro.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.agendapro.auth.service.PasswordService;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.conversa.entity.ConversaEntity;
import com.minhaempresa.agendapro.conversa.enums.StatusConversa;
import com.minhaempresa.agendapro.conversa.repository.ConversaRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.enums.StatusEmpresa;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.mensagem.entity.MensagemEntity;
import com.minhaempresa.agendapro.mensagem.enums.DirecaoMensagem;
import com.minhaempresa.agendapro.mensagem.enums.TipoMensagem;
import com.minhaempresa.agendapro.mensagem.repository.MensagemRepository;
import com.minhaempresa.agendapro.pagamento.entity.PagamentoEntity;
import com.minhaempresa.agendapro.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.agendapro.pagamento.enums.MetodoPagamento;
import com.minhaempresa.agendapro.pagamento.enums.StatusPagamento;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.plano.entity.PlanoEntity;
import com.minhaempresa.agendapro.plano.enums.StatusPlano;
import com.minhaempresa.agendapro.plano.repository.PlanoRepository;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;
import com.minhaempresa.agendapro.profissional.repository.ProfissionalRepository;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import com.minhaempresa.agendapro.servico.repository.ServicoRepository;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.enums.StatusUsuario;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
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
                .orElseGet(() -> planoRepository.save(new PlanoEntity(null, "BASICO", "WhatsApp, agenda, clientes e servicos.", new BigDecimal("39.00"), StatusPlano.ATIVO)));
        PlanoEntity pro = planoRepository.findByNome("PRO")
                .orElseGet(() -> planoRepository.save(new PlanoEntity(null, "PRO", "Agenda com financeiro, pagamentos e relatorios.", new BigDecimal("89.00"), StatusPlano.ATIVO)));

        EmpresaEntity empresaBasico = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("AgendaPro Matriz")
                .documento("12345678000190")
                .telefone("(65) 99336-0300")
                .email("contato@agendapro.com")
                .status(StatusEmpresa.ATIVA)
                .build());

        EmpresaEntity empresaPro = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("AgendaPro Premium")
                .documento("98765432000100")
                .telefone("(65) 98888-0000")
                .email("premium@agendapro.com")
                .status(StatusEmpresa.ATIVA)
                .build());

        usuarioRepository.save(UsuarioEntity.builder()
                .nome("Ana Basico")
                .email("basico@agendapro.com")
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
                .email("pro@agendapro.com")
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
                .documento("11222333000181")
                .telefone("(65) 99999-0000")
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
                .telefone("(65) 99911-1111")
                .email("cliente@" + empresa.getId() + ".com")
                .observacoes("Cliente de teste.")
                .empresa(empresa)
                .build());

        ServicoEntity servico = servicoRepository.save(ServicoEntity.builder()
                .nome("Consulta")
                .descricao("Atendimento completo")
                .duracaoMinutos(60)
                .valor(new BigDecimal("180.00"))
                .status(StatusCadastro.ATIVO)
                .empresa(empresa)
                .build());

        ProfissionalEntity profissional = profissionalRepository.save(ProfissionalEntity.builder()
                .nome("Dra. Marina")
                .especialidade("Clinica geral")
                .telefone("(65) 98888-1111")
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
                .observacoes("Chegar 10 minutos antes.")
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
