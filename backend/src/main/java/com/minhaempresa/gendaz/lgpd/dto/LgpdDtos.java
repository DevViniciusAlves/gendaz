package com.minhaempresa.gendaz.lgpd.dto;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.ClienteResponse;
import com.minhaempresa.gendaz.conversa.dto.ConversaDtos.ConversaResponse;
import com.minhaempresa.gendaz.entrega.dto.EntregaDtos.EntregaResponse;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.gendaz.notafiscal.dto.NotaFiscalDtos.NotaFiscalResponse;
import com.minhaempresa.gendaz.notificacao.dto.NotificacaoDtos.NotificacaoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoResponse;
import com.minhaempresa.gendaz.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.gendaz.servico.dto.ServicoDtos.ServicoResponse;
import com.minhaempresa.gendaz.usuario.dto.UsuarioDtos.UsuarioResponse;
import com.minhaempresa.gendaz.mensagem.dto.MensagemDtos.MensagemResponse;
import java.time.LocalDateTime;
import java.util.List;

public final class LgpdDtos {
    private LgpdDtos() {}

    public record ExportacaoDadosResponse(
            EmpresaExportada empresa,
            UsuarioResponse usuarioSolicitante,
            AssinaturaResponse assinatura,
            ResumoFinanceiroResponse financeiro,
            List<UsuarioResponse> usuarios,
            List<ClienteResponse> clientes,
            List<ServicoResponse> servicos,
            List<ProfissionalResponse> profissionais,
            List<AgendamentoResponse> agendamentos,
            List<ConversaResponse> conversas,
            List<MensagemResponse> mensagens,
            List<PagamentoResponse> pagamentos,
            List<PagamentoPlanoResponse> pagamentosPlano,
            List<NotaFiscalResponse> notasFiscais,
            List<EntregaResponse> entregas,
            List<NotificacaoResponse> notificacoes,
            List<ChamadoResponse> chamados,
            List<AuditoriaExportada> auditoria
    ) {}

    public record EmpresaExportada(Long id, String nomeFantasia, String telefone, String email, String status, LocalDateTime dataCriacao) {}

    public record AuditoriaExportada(Long id, String tipo, String severidade, String descricao, String motivo, String ip, String userAgent, LocalDateTime dataCriacao) {}

    public record ExcluirContaResponse(String mensagem, Long empresaId, String statusEmpresa, String stripeStatus) {}
}

