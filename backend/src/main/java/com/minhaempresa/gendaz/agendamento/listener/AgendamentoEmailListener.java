package com.minhaempresa.gendaz.agendamento.listener;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.event.AgendamentoCriadoEvent;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgendamentoEmailListener {

    private final ResendEmailService resendEmailService;
    private final EmpresaRepository empresaRepository;
    private final AgendamentoRepository agendamentoRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAgendamentoCriado(AgendamentoCriadoEvent event) {
        if (event == null || event.agendamentoId() == null) {
            return;
        }
        try {
            EmpresaEntity empresa = empresaRepository.findById(event.empresaId()).orElse(null);
            AgendamentoEntity agendamento = agendamentoRepository.findById(event.agendamentoId()).orElse(null);
            if (empresa != null && agendamento != null) {
                resendEmailService.enviarEmailNovoAgendamento(empresa, agendamento);
            }
        } catch (Exception e) {
            Map<String, Object> contextoEmailErro = new LinkedHashMap<>();
            contextoEmailErro.put("agendamentoId", event.agendamentoId());
            contextoEmailErro.put("empresaId", event.empresaId());
            contextoEmailErro.put("clienteId", event.clienteId());
            contextoEmailErro.put("servicoId", event.servicoId());
            contextoEmailErro.put("profissionalId", event.profissionalId());
            log.error("[agendamento-email] falha ao enviar email novo agendamento. erroTipo={} contexto={}",
                    e.getClass().getSimpleName(), contextoEmailErro);
        }

        try {
            if (event.clienteEmail() != null && !event.clienteEmail().isBlank()) {
                resendEmailService.enviarConfirmacaoAgendamento(
                        event.clienteEmail(),
                        event.clienteNome(),
                        event.servicoNome(),
                        event.profissionalNome(),
                        event.data(),
                        event.horaInicio(),
                        event.empresaNomeFantasia(),
                        event.empresaAgendamentoSlug()
                );
            }
        } catch (Exception e) {
            Map<String, Object> contextoEmailConfirmacaoErro = new LinkedHashMap<>();
            contextoEmailConfirmacaoErro.put("agendamentoId", event.agendamentoId());
            log.error("[agendamento-email] falha ao enviar email confirmacao cliente. erroTipo={} contexto={}",
                    e.getClass().getSimpleName(), contextoEmailConfirmacaoErro);
        }
    }
}
