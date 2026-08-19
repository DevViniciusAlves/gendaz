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
import java.util.Map;

public final class LgpdDtos {
    private LgpdDtos() {}

    public record ExportacaoDadosResponse(
            Map<String, Object> exportacao,
            EmpresaExportada empresa,
            UsuarioResponse meusDados,
            AceitesLgpd aceitesLgpd,
            PlanoExportado plano,
            List<AuditoriaExportada> dadosTecnicos,
            MeuGendazExportado meuGendaz
    ) {}

    public record AceitesLgpd(
            boolean aceitouTermos,
            LocalDateTime dataAceiteTermos,
            String versaoTermos,
            LocalDateTime dataAceitePolitica,
            String versaoPolitica
    ) {}

    public record PlanoExportado(
            String plano,
            String status,
            LocalDateTime dataCriacao,
            LocalDateTime dataExpiracao
    ) {}

    public record MeuGendazExportado(
            String nome,
            String email,
            String status
    ) {}

    public record EmpresaExportada(Long id, String nomeFantasia, String telefone, String email, String agendamentoSlug, String status, String timezone, String ramo, LocalDateTime dataCriacao, LocalDateTime dataAtualizacao) {}

    public record AuditoriaExportada(Long id, String tipo, String severidade, String descricao, String motivo, String ip, String userAgent, LocalDateTime dataCriacao) {}

    public record ExcluirContaResponse(String mensagem, Long empresaId, String statusEmpresa, String stripeStatus) {}
}

