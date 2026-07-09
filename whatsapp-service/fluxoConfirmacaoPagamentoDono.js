/*
╔════════════════════════════════════════╗
║  ⚠️  DESATIVADO - FUNCIONALIDADE WhatsApp ║
║  Código comentado. Remova comentários  ║
║  para reativar.                        ║
╚════════════════════════════════════════╝
*/

const axios = require('axios');

function normalizarBusca(valor) {
  return String(valor || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function identificarStatusPagamentoDono(texto) {
  const valor = normalizarBusca(texto);

  if (/^(1|sim|s|pago|foi pago|pagou|confirmado|confirmar)$/.test(valor)) {
    return 'PAGO';
  }

  if (/^(2|nao|não|n|pendente|nao foi|não foi|nao pagou|não pagou|ficou pendente|em aberto)$/.test(valor)) {
    return 'PENDENTE';
  }

  if (/^(3|cancelado|cancelar|foi cancelado|cliente cancelou|cancelada|cancelamento)$/.test(valor)) {
    return 'CANCELADO';
  }

  if (valor.includes('foi pago') || valor.includes('ja pagou') || valor.includes('já pagou')) {
    return 'PAGO';
  }

  if (valor.includes('nao foi pago') || valor.includes('não foi pago') || valor.includes('nao pagou') || valor.includes('não pagou')) {
    return 'PENDENTE';
  }

  if (valor.includes('cancelou') || valor.includes('foi cancelado')) {
    return 'CANCELADO';
  }

  return null;
}

async function atualizarStatusPagamentoDonoBackend({ backendUrl, backendToken, empresaId, agendamentoId, statusPagamento }) {
  const base = String(backendUrl || '').replace(/\/+$/, '');
  const response = await axios.put(
    `${base}/api/internal/whatsapp/agendamentos/${agendamentoId}/confirmacao-pagamento-dono`,
    {
      empresaId,
      statusPagamento,
    },
    {
      headers: backendToken ? { 'X-Internal-Token': backendToken } : {},
      timeout: 15000,
    }
  );
  return response.data || {};
}

async function conduzirFluxoConfirmacaoPagamentoDono({ entrada, estado, empresaId, remoteJid, backendUrl, backendToken }) {
  const fluxo = estado.fluxoConfirmacaoPagamentoDono;
  if (!fluxo?.ativo || fluxo.etapa !== 'AGUARDANDO_RESPOSTA_PAGAMENTO_DONO') {
    return null;
  }

  const statusPagamento = identificarStatusPagamentoDono(entrada);
  console.log('[bot-pagamento-dono] resposta identificada', {
    empresaId,
    remoteJid: remoteJid || null,
    agendamentoId: fluxo.agendamentoId,
    statusPagamento,
  });
  if (!statusPagamento) {
    return 'Não entendi. Responda com:\n1. Sim, foi pago\n2. Não, ficou pendente\n3. Foi cancelado';
  }

  const resposta = await atualizarStatusPagamentoDonoBackend({
    backendUrl,
    backendToken,
    empresaId,
    agendamentoId: fluxo.agendamentoId,
    statusPagamento,
  });

  if (!resposta?.success) {
    return resposta?.mensagem || 'Não consegui atualizar o status do pagamento agora. Tente novamente.';
  }

  fluxo.ativo = false;
  fluxo.etapa = null;
  fluxo.enviadoEm = fluxo.enviadoEm || Date.now();
  estado.updatedAt = Date.now();

  console.log('[bot-pagamento-dono] status atualizado no backend', {
    empresaId,
    remoteJid: remoteJid || null,
    agendamentoId: fluxo.agendamentoId,
    statusPagamento,
  });

  if (statusPagamento === 'PAGO') {
    return 'Perfeito, marquei esse atendimento como PAGO no SaaS.';
  }

  if (statusPagamento === 'PENDENTE') {
    return 'Certo, marquei esse atendimento como PENDENTE no SaaS.';
  }

  if (statusPagamento === 'CANCELADO') {
    return 'Certo, marquei esse atendimento como CANCELADO no SaaS.';
  }

  return 'Status atualizado no SaaS.';
}

module.exports = { conduzirFluxoConfirmacaoPagamentoDono: async () => null };
