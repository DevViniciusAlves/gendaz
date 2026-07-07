package com.minhaempresa.agendapro.lgpd.dto;

import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.agendapro.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.ClienteResponse;
import com.minhaempresa.agendapro.conversa.dto.ConversaDtos.ConversaResponse;
import com.minhaempresa.agendapro.entrega.dto.EntregaDtos.EntregaResponse;
import com.minhaempresa.agendapro.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.agendapro.notafiscal.dto.NotaFiscalDtos.NotaFiscalResponse;
import com.minhaempresa.agendapro.notificacao.dto.NotificacaoDtos.NotificacaoResponse;
import com.minhaempresa.agendapro.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.agendapro.pagamento.dto.PagamentoDtos.PagamentoResponse;
import com.minhaempresa.agendapro.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.agendapro.servico.dto.ServicoDtos.ServicoResponse;
import com.minhaempresa.agendapro.usuario.dto.UsuarioDtos.UsuarioResponse;
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
            List<PagamentoResponse> pagamentos,
            List<PagamentoPlanoResponse> pagamentosPlano,
            List<NotaFiscalResponse> notasFiscais,
            List<EntregaResponse> entregas,
            List<NotificacaoResponse> notificacoes,
            List<ChamadoResponse> chamados,
            List<AuditoriaExportada> auditoria
    ) {}

    public record EmpresaExportada(Long id, String nomeFantasia, String documento, String telefone, String email, String status, LocalDateTime dataCriacao) {}

    public record AuditoriaExportada(Long id, String tipo, String severidade, String descricao, String motivo, String ip, String userAgent, LocalDateTime dataCriacao) {}

    public record ExcluirContaResponse(String mensagem, Long empresaId, String statusEmpresa) {}
}
