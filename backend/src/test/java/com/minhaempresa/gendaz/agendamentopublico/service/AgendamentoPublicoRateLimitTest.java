package com.minhaempresa.gendaz.agendamentopublico.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.gendaz.agendamento.service.AgendamentoService;
import com.minhaempresa.gendaz.agendamentopublico.dto.AgendamentoPublicoDtos.AgendamentoPublicoResponse;
import com.minhaempresa.gendaz.agendamentopublico.dto.AgendamentoPublicoDtos.CriarAgendamentoPublicoRequest;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.auth.config.MeuGendazSecurityProperties;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.security.PersistentRateLimitService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgendamentoPublicoRateLimitTest {

    @Mock EmpresaRepository empresaRepository;
    @Mock ServicoRepository servicoRepository;
    @Mock ProfissionalRepository profissionalRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock AgendamentoService agendamentoService;
    @Mock HorarioAtendimentoService horarioAtendimentoService;
    @Mock AssinaturaRepository assinaturaRepository;
    @Mock SanitizacaoService sanitizacaoService;
    @Mock PersistentRateLimitService persistentRateLimitService;
    @Mock MeuGendazSecurityProperties securityProperties;

    private PhoneNumberService phoneNumberService;
    private AgendamentoPublicoService service;

    private MeuGendazSecurityProperties.PublicBooking publicBooking;

    @BeforeEach
    void setUp() {
        phoneNumberService = new PhoneNumberService();
        service = new AgendamentoPublicoService(
                empresaRepository, servicoRepository, profissionalRepository, clienteRepository,
                agendamentoService, horarioAtendimentoService, assinaturaRepository, sanitizacaoService,
                persistentRateLimitService, securityProperties, phoneNumberService);
        publicBooking = new MeuGendazSecurityProperties.PublicBooking();
        org.mockito.Mockito.lenient().when(securityProperties.getPublicBooking()).thenReturn(publicBooking);
    }

    private EmpresaEntity empresa() {
        return EmpresaEntity.builder().id(5L).nomeFantasia("Loja").status(StatusEmpresa.ATIVA)
                .email("x@x.com").agendamentoSlug("loja").build();
    }

    private void prepararAgendamento(String telefone, String clienteTelefone) {
        EmpresaEntity empresa = empresa();
        when(empresaRepository.findByAgendamentoSlug("loja")).thenReturn(Optional.of(empresa));
        when(assinaturaRepository.findByEmpresaId(5L))
                .thenReturn(List.of(AssinaturaEntity.builder().status(StatusAssinatura.ATIVA).build()));

        ServicoEntity servico = ServicoEntity.builder().id(1L).empresa(empresa)
                .status(com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO).build();
        when(servicoRepository.findById(1L)).thenReturn(Optional.of(servico));

        ClienteEntity cliente = ClienteEntity.builder().id(9L).nome("Joao").telefone(telefone)
                .empresa(empresa).status(com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO).build();
        when(clienteRepository.findFirstByEmpresaIdAndTelefone(eq(5L), anyString()))
                .thenReturn(Optional.of(cliente));
        when(clienteRepository.save(any(ClienteEntity.class))).thenReturn(cliente);
        when(sanitizacaoService.telefone(anyString())).thenReturn(telefone);
        when(agendamentoService.criar(any())).thenReturn(new AgendamentoResponse(
                1L, "PROTO-1", 9L, "Joao", 1L, "Consulta", null, "Dra. Marina", 5L, null,
                null, null, null, null, null, null, null, null, null, null, null,
                com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO));
    }

    @Test
    void formatosEquivalentesGeramMesmaChaveCanonicaDeRateLimit() {
        prepararAgendamento("5565993360341", null);
        ArgumentCaptor<String> chaveCaptor = ArgumentCaptor.forClass(String.class);

        CriarAgendamentoPublicoRequest r1 = new CriarAgendamentoPublicoRequest(
                1L, null, LocalDate.now(), LocalTime.of(9, 0), null, "Joao", "(65) 99336-0341", "j@j.com", null);
        CriarAgendamentoPublicoRequest r2 = new CriarAgendamentoPublicoRequest(
                1L, null, LocalDate.now(), LocalTime.of(9, 0), null, "Joao", "+55 65 99336-0341", "j@j.com", null);
        CriarAgendamentoPublicoRequest r3 = new CriarAgendamentoPublicoRequest(
                1L, null, LocalDate.now(), LocalTime.of(9, 0), null, "Joao", "+5565993360341", "j@j.com", null);

        service.agendar("loja", r1, "1.1.1.1");
        service.agendar("loja", r2, "1.1.1.1");
        service.agendar("loja", r3, "1.1.1.1");

        verify(persistentRateLimitService, atLeastOnce())
                .consumir(chaveCaptor.capture(), anyInt(), any(), any());

        long chavesCanonicas = chaveCaptor.getAllValues().stream()
                .filter(k -> k.startsWith("BOOKING_PHONE:5:")).distinct().count();
        // Todos os três formatos equivalentes caem na MESMA chave canônica.
        assertEquals(1, chavesCanonicas);
        assertEquals("BOOKING_PHONE:5:5565993360341",
                chaveCaptor.getAllValues().stream().filter(k -> k.startsWith("BOOKING_PHONE")).findFirst().orElseThrow());
    }

    @Test
    void telefoneInvalidoRetorna400ViaBusinessException() {
        EmpresaEntity empresa = empresa();
        when(empresaRepository.findByAgendamentoSlug("loja")).thenReturn(Optional.of(empresa));
        when(assinaturaRepository.findByEmpresaId(5L))
                .thenReturn(List.of(AssinaturaEntity.builder().status(StatusAssinatura.ATIVA).build()));

        CriarAgendamentoPublicoRequest invalido = new CriarAgendamentoPublicoRequest(
                1L, null, LocalDate.now(), LocalTime.of(9, 0), null, "Joao", "abc", "j@j.com", null);

        assertThrows(com.minhaempresa.gendaz.shared.BusinessException.class,
                () -> service.agendar("loja", invalido, "1.1.1.1"));
    }
}
