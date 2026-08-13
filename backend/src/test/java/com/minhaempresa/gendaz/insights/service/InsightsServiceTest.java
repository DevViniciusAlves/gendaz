package com.minhaempresa.gendaz.insights.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minhaempresa.gendaz.insights.client.GroqClient;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.DashboardResponse;
import com.minhaempresa.gendaz.insights.entity.InsightEntity;
import com.minhaempresa.gendaz.insights.repository.InsightRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class InsightsServiceTest {
    @Mock
    private InsightsAnalyzer analyzer;
    @Mock
    private GroqClient groqClient;
    @Mock
    private InsightRepository insightRepository;

    private AutoCloseable mocks;
    private InsightsService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new InsightsService(analyzer, groqClient, insightRepository, objectMapper);
        ReflectionTestUtils.setField(service, "appTimezone", "America/Cuiaba");
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() throws Exception {
        CompanyContext.clear();
        mocks.close();
    }

    @Test
    void getComSnapshotRetornaUltimoSemGerarOutro() throws Exception {
        DashboardResponse dashboard = dashboard(1L, 70, LocalDateTime.of(2026, 8, 13, 18, 0));
        when(insightRepository.findFirstByEmpresaIdAndTipoOrderByDataCriacaoDesc(1L, "dashboard"))
                .thenReturn(Optional.of(insight(1L, dashboard, dashboard.geradoEm())));

        Optional<DashboardResponse> resposta = service.buscarUltimoDashboardPersistido(1L);

        assertTrue(resposta.isPresent());
        assertEquals(70, resposta.get().scoreGeral());
        assertEquals(dashboard.geradoEm(), resposta.get().geradoEm());
        verify(analyzer, never()).coletarDados(any(), any());
        verify(groqClient, never()).analisar(any(), any());
        verify(insightRepository, never()).saveAndFlush(any());
    }

    @Test
    void getSemSnapshotNaoGeraNaoChamaIaNaoSalva() {
        when(insightRepository.findFirstByEmpresaIdAndTipoOrderByDataCriacaoDesc(1L, "dashboard"))
                .thenReturn(Optional.empty());

        Optional<DashboardResponse> resposta = service.buscarUltimoDashboardPersistido(1L);

        assertTrue(resposta.isEmpty());
        verify(analyzer, never()).coletarDados(any(), any());
        verify(groqClient, never()).analisar(any(), any());
        verify(insightRepository, never()).saveAndFlush(any());
    }

    @Test
    void postRecalcularGeraSalvaERetorna() {
        when(analyzer.coletarDados(1L, 30)).thenReturn(dadosComMovimento(1L));
        when(groqClient.disponivel()).thenReturn(false);
        when(insightRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InsightEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        DashboardResponse resposta = service.recalcularDashboard(1L, 30);

        assertEquals(1L, resposta.empresaId());
        verify(analyzer).coletarDados(1L, 30);
        verify(insightRepository).saveAndFlush(any(InsightEntity.class));
    }

    @Test
    void novoPostCriaSnapshotNovoEGetPosteriorRetornaNovo() throws Exception {
        DashboardResponse antigo = dashboard(1L, 60, LocalDateTime.of(2026, 8, 13, 18, 0));
        DashboardResponse novo = dashboard(1L, 85, LocalDateTime.of(2026, 8, 13, 20, 0));
        when(insightRepository.findFirstByEmpresaIdAndTipoOrderByDataCriacaoDesc(1L, "dashboard"))
                .thenReturn(Optional.of(insight(1L, antigo, antigo.geradoEm())))
                .thenReturn(Optional.of(insight(1L, novo, novo.geradoEm())));
        when(analyzer.coletarDados(1L, 30)).thenReturn(dadosComMovimento(1L));
        when(groqClient.disponivel()).thenReturn(false);
        when(insightRepository.saveAndFlush(any())).thenReturn(insight(1L, novo, novo.geradoEm()));

        assertEquals(60, service.buscarUltimoDashboardPersistido(1L).orElseThrow().scoreGeral());
        service.recalcularDashboard(1L, 30);
        assertEquals(85, service.buscarUltimoDashboardPersistido(1L).orElseThrow().scoreGeral());
    }

    @Test
    void falhaNoPostNaoApagaSnapshotAntigo() throws Exception {
        DashboardResponse antigo = dashboard(1L, 60, LocalDateTime.of(2026, 8, 13, 18, 0));
        when(insightRepository.findFirstByEmpresaIdAndTipoOrderByDataCriacaoDesc(1L, "dashboard"))
                .thenReturn(Optional.of(insight(1L, antigo, antigo.geradoEm())));
        when(analyzer.coletarDados(1L, 30)).thenThrow(new RuntimeException("falha"));

        assertThrows(RuntimeException.class, () -> service.recalcularDashboard(1L, 30));
        assertEquals(60, service.buscarUltimoDashboardPersistido(1L).orElseThrow().scoreGeral());
        verify(insightRepository, never()).delete(any());
        verify(insightRepository, never()).saveAndFlush(any());
    }

    @Test
    void empresaDiferenteNaoLeSnapshotDeOutraEmpresa() {
        assertThrows(BusinessException.class, () -> service.buscarUltimoDashboardPersistido(2L));
        verify(insightRepository, never()).findFirstByEmpresaIdAndTipoOrderByDataCriacaoDesc(eq(2L), eq("dashboard"));
    }

    @Test
    void getsRepetidosNaoEscrevemNoBanco() {
        when(insightRepository.findFirstByEmpresaIdAndTipoOrderByDataCriacaoDesc(1L, "dashboard"))
                .thenReturn(Optional.empty());
        when(insightRepository.countByEmpresaIdAndTipo(1L, "dashboard")).thenReturn(3L);

        long antes = insightRepository.countByEmpresaIdAndTipo(1L, "dashboard");
        for (int i = 0; i < 10; i++) {
            service.buscarUltimoDashboardPersistido(1L);
        }
        long depois = insightRepository.countByEmpresaIdAndTipo(1L, "dashboard");

        assertEquals(antes, depois);
        verify(insightRepository, never()).saveAndFlush(any());
        verify(groqClient, never()).analisar(any(), any());
    }

    private InsightEntity insight(Long empresaId, DashboardResponse dashboard, LocalDateTime dataCriacao) throws Exception {
        return InsightEntity.builder()
                .id(1L)
                .empresaId(empresaId)
                .tipo("dashboard")
                .pergunta("MANUAL - Dashboard")
                .resposta(objectMapper.writeValueAsString(dashboard))
                .payloadJson("{}")
                .origem("MANUAL")
                .dataCriacao(dataCriacao)
                .build();
    }

    private DashboardResponse dashboard(Long empresaId, int score, LocalDateTime geradoEm) {
        return new DashboardResponse(empresaId, "Empresa", score, List.of(), List.of(), List.of(), List.of(), "N/A", geradoEm);
    }

    private Map<String, Object> dadosComMovimento(Long empresaId) {
        return Map.of(
                "empresaId", empresaId,
                "empresaNome", "Empresa",
                "empresaRamo", "OUTRO",
                "empresaRamoDisplayName", "Outro",
                "clientes", Map.of("total", 1, "inativos_status", 0),
                "financeiro", Map.of("receita_30d", 100, "receita_60d", 50, "pendente", 0),
                "resumo", Map.of("servicos_inativos", 0, "profissionais_inativos", 0),
                "servicos", List.of(),
                "profissionais", List.of(),
                "agendamentosRecentes", List.of(Map.of("id", 1))
        );
    }
}
