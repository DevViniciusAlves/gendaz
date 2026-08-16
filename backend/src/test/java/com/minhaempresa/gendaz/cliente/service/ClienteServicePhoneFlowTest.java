package com.minhaempresa.gendaz.cliente.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.ClienteResponse;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.SalvarClienteRequest;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.conversa.repository.ConversaRepository;
import com.minhaempresa.gendaz.crm.repository.CrmContatoRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.entrega.repository.EntregaRepository;
import com.minhaempresa.gendaz.mensagem.repository.MensagemRepository;
import com.minhaempresa.gendaz.meugendazpromocao.repository.MeuGendazPromocaoNotificacaoRepository;
import com.minhaempresa.gendaz.notafiscal.repository.NotaFiscalRepository;
import com.minhaempresa.gendaz.notificacao.repository.NotificacaoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.promocao.repository.PromocaoNotificacaoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServicePhoneFlowTest {

    @Mock ClienteRepository clienteRepository;
    @Mock EmpresaService empresaService;
    @Mock AgendamentoRepository agendamentoRepository;
    @Mock PagamentoRepository pagamentoRepository;
    @Mock ConversaRepository conversaRepository;
    @Mock CrmContatoRepository crmContatoRepository;
    @Mock MensagemRepository mensagemRepository;
    @Mock EntregaRepository entregaRepository;
    @Mock NotificacaoRepository notificacaoRepository;
    @Mock NotaFiscalRepository notaFiscalRepository;
    @Mock PromocaoNotificacaoRepository promocaoNotificacaoRepository;
    @Mock MeuGendazPromocaoNotificacaoRepository meuGendazPromocaoNotificacaoRepository;
    @Mock ClienteEmailBloqueadoService clienteEmailBloqueadoService;
    @Mock SanitizacaoService sanitizacaoService;
    @Mock AdminAuditService auditService;

    private PhoneNumberService phoneNumberService;
    private ClienteService clienteService;

    @BeforeEach
    void setUp() {
        phoneNumberService = new PhoneNumberService();
        clienteService = new ClienteService(
                clienteRepository, empresaService, agendamentoRepository, pagamentoRepository,
                conversaRepository, crmContatoRepository, mensagemRepository, entregaRepository,
                notificacaoRepository, notaFiscalRepository, promocaoNotificacaoRepository,
                meuGendazPromocaoNotificacaoRepository, clienteEmailBloqueadoService,
                sanitizacaoService, phoneNumberService, auditService);
        CompanyContext.setCompanyId(10L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void buscarPorTelefoneConsultaDiretamenteNaEmpresaDoContexto() {
        CompanyContext.setCompanyId(10L);
        ClienteEntity cliente = ClienteEntity.builder()
                .id(1L).nome("Maria").telefone("5565993360341").email("maria@test.com")
                .status(StatusCadastro.ATIVO).empresa(EmpresaEntity.builder().id(10L).build()).build();
        when(clienteRepository.findFirstByEmpresaIdAndTelefone(10L, "5565993360341"))
                .thenReturn(Optional.of(cliente));

        ClienteResponse response = clienteService.buscarPorTelefone("+55 65 99336-0341");

        assertEquals("5565993360341", response.telefone());
        // Nunca faz lookup global com findFirstByTelefone para depois filtrar em memória.
        verify(clienteRepository, never()).findFirstByTelefone(any());
        verify(clienteRepository).findFirstByEmpresaIdAndTelefone(eq(10L), eq("5565993360341"));
    }

    @Test
    void buscarPorTelefoneSemClienteLancaNaoEncontrado() {
        CompanyContext.setCompanyId(10L);
        when(clienteRepository.findFirstByEmpresaIdAndTelefone(10L, "5565993360341"))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> clienteService.buscarPorTelefone("(65) 99336-0341"));
    }

    @Test
    void criarNormalizaTelefoneParaCanonico() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(10L).build();
        when(empresaService.buscarEntidade(10L)).thenReturn(empresa);
        when(sanitizacaoService.textoObrigatorio("Maria")).thenReturn("Maria");
        when(sanitizacaoService.email("maria@test.com")).thenReturn("maria@test.com");
        when(sanitizacaoService.texto(any())).thenReturn(null);
        when(clienteRepository.findFirstByEmpresaIdAndTelefone(10L, "5565993360341")).thenReturn(Optional.empty());
        when(clienteRepository.existsByEmpresaIdAndEmail(anyLong(), any())).thenReturn(false);

        ClienteEntity novo = ClienteEntity.builder()
                .id(1L).nome("Maria").telefone("5565993360341").email("maria@test.com")
                .status(StatusCadastro.ATIVO).empresa(empresa).dataCriacao(java.time.LocalDateTime.now()).build();
        when(clienteRepository.save(any(ClienteEntity.class))).thenReturn(novo);

        SalvarClienteRequest request = new SalvarClienteRequest(
                "Maria", "(65) 99336-0341", "maria@test.com", null, 10L);

        ClienteResponse response = clienteService.salvar(request);

        assertEquals("5565993360341", response.telefone());
        verify(clienteRepository).findFirstByEmpresaIdAndTelefone(10L, "5565993360341");
    }

    @Test
    void criarRejeitaTelefoneJaUsadoNaMesmaEmpresa() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(10L).build();
        when(empresaService.buscarEntidade(10L)).thenReturn(empresa);
        when(sanitizacaoService.textoObrigatorio("Maria")).thenReturn("Maria");
        when(sanitizacaoService.email("maria@test.com")).thenReturn("maria@test.com");
        ClienteEntity existente = ClienteEntity.builder()
                .id(2L).telefone("5565993360341").empresa(empresa).build();
        when(clienteRepository.findFirstByEmpresaIdAndTelefone(10L, "5565993360341"))
                .thenReturn(Optional.of(existente));

        SalvarClienteRequest request = new SalvarClienteRequest(
                "Maria", "+55 65 99336-0341", "maria@test.com", null, 10L);

        BusinessException ex = assertThrows(BusinessException.class, () -> clienteService.salvar(request));
        assertEquals("Ja existe um cliente com este telefone.", ex.getMessage());
        verify(clienteRepository, never()).save(any(ClienteEntity.class));
    }
}
