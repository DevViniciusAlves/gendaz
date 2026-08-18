package com.minhaempresa.gendaz.insights.scheduler;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.insights.repository.InsightRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InsightsCleanupSchedulerTest {

    @Mock
    private InsightRepository insightRepository;

    @Test
    void limparExpiradosRemoveInsightsVencidosPelaDataExpiracao() {
        InsightsCleanupScheduler scheduler = new InsightsCleanupScheduler(insightRepository);
        when(insightRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(3);

        scheduler.limparExpirados();

        ArgumentCaptor<LocalDateTime> limite = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(insightRepository).deleteExpiredBefore(limite.capture());
        assertTrue(limite.getValue().isBefore(LocalDateTime.now()));
    }
}