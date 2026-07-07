const axios = require('axios');

function normalizarTexto(valor) {
  return String(valor || '').trim();
}

function normalizarBusca(valor) {
  return String(valor || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function normalizarBuscaData(valor) {
  return String(valor || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s\/-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function extrairProtocolo(texto) {
  const t = String(texto || '').trim();
  const match = t.match(/\b\d{6}\b/);
  return match ? match[0] : null;
}

function normalizarAgendamentoCancelamento(dados, protocoloFallback = null) {
  const agendamento = dados?.agendamento || dados || {};
  return {
    id: agendamento.id !== undefined && agendamento.id !== null
      ? agendamento.id
      : (agendamento.agendamentoId !== undefined && agendamento.agendamentoId !== null ? agendamento.agendamentoId : null),
    protocolo: agendamento.protocolo || protocoloFallback || null,
    clienteNome: agendamento.clienteNome || agendamento.nomeCliente || agendamento.cliente?.nome || '-',
    servicoNome: agendamento.servicoNome || agendamento.nomeServico || agendamento.servico?.nome || '-',
    data: agendamento.data || agendamento.dataAgendamento || '',
    horario: agendamento.horario || agendamento.horaInicio || agendamento.horarioInicio || '',
    status: agendamento.status || null,
  };
}

function extrairHorario(texto) {
  const t = normalizarBusca(texto);
  const match = t.match(/\b(\d{1,2})(?:[:h](\d{2}))?\b/);
  if (!match) return null;
  const hora = String(match[1]).padStart(2, '0');
  const minuto = String(match[2] || '00').padStart(2, '0');
  const valor = `${hora}:${minuto}`;
  if (Number(hora) > 23 || Number(minuto) > 59) return null;
  return valor;
}

function parseDataCliente(texto) {
  const t = normalizarBuscaData(texto);
  const hoje = new Date();
  hoje.setHours(12, 0, 0, 0);
  if (!t) return null;
  if (t.includes('hoje')) return hoje.toISOString().slice(0, 10);
  if (t.includes('amanha')) {
    const data = new Date(hoje);
    data.setDate(data.getDate() + 1);
    return data.toISOString().slice(0, 10);
  }
  const br = t.match(/\b(\d{1,2})[\/-](\d{1,2})(?:[\/-](\d{2,4}))?\b/);
  if (br) {
    const dia = String(br[1]).padStart(2, '0');
    const mes = String(br[2]).padStart(2, '0');
    let ano = br[3] || String(hoje.getFullYear());
    if (ano.length === 2) {
      ano = `20${ano}`;
    }
    let data = new Date(`${ano}-${mes}-${dia}T12:00:00`);
    if (!br[3] && data < hoje) {
      data = new Date(`${Number(hoje.getFullYear()) + 1}-${mes}-${dia}T12:00:00`);
    }
    return data.toISOString().slice(0, 10);
  }
  const diaMes = t.match(/\bdia\s*(\d{1,2})\b/);
  if (diaMes) {
    const dia = String(diaMes[1]).padStart(2, '0');
    const mes = String(hoje.getMonth() + 1).padStart(2, '0');
    const ano = String(hoje.getFullYear());
    return `${ano}-${mes}-${dia}`;
  }
  const diasSemana = ['segunda', 'terca', 'quarta', 'quinta', 'sexta', 'sabado', 'domingo'];
  const encontrado = diasSemana.find((dia) => t.includes(dia));
  if (encontrado) {
    const mapa = { domingo: 0, segunda: 1, terca: 2, quarta: 3, quinta: 4, sexta: 5, sabado: 6 };
    const alvo = mapa[encontrado];
    for (let i = 0; i < 14; i += 1) {
      const data = new Date(hoje);
      data.setDate(data.getDate() + i);
      if (data.getDay() === alvo) {
        return data.toISOString().slice(0, 10);
      }
    }
  }
  return null;
}

function prefereAgendarPorWhatsapp(texto) {
  const valor = normalizarBusca(texto);
  return [
    'por aqui',
    'aqui mesmo',
    'pelo whatsapp',
    'faz por aqui',
    'quero por aqui',
    'prefiro aqui',
    'me ajuda por aqui',
    'pode ser aqui',
    'continuar por aqui',
  ].some((item) => valor.includes(normalizarBusca(item)));
}

function prefereLink(texto) {
  const valor = normalizarBusca(texto);
  return ['link', 'pelo link', 'manda o link', 'envia o link', 'quero o link', 'prefiro o link', 'pode ser o link']
    .some((item) => valor.includes(normalizarBusca(item)));
}

function resetarFluxoCancelamento(estado) {
  estado.fluxoAgendamento = {
    ativo: false,
    tipoFluxo: 'CANCELAMENTO',
    etapa: null,
    modoSelecionado: null,
    servicosDisponiveis: [],
    servicoSelecionado: null,
    dataSelecionada: null,
    horariosDisponiveis: [],
    horarioSelecionado: null,
    nomeCliente: null,
    ultimaPergunta: null,
    agendamentoCancelamento: null,
  };
  return estado.fluxoAgendamento;
}

function fluxoExpiradoCancelamento(estado) {
  return Boolean(estado?.fluxoAgendamento?.ativo)
    && (Date.now() - Number(estado?.updatedAt || 0)) > 15 * 60 * 1000;
}

async function buscarCancelamentoPorProtocolo({ backendUrl, backendToken, empresaId, protocolo, telefone }) {
  const base = String(backendUrl || '').replace(/\/+$/, '');
  const response = await axios.get(`${base}/api/internal/whatsapp/agendamentos/protocolo/${protocolo}`, {
    headers: backendToken ? { 'X-Internal-Token': backendToken } : {},
    params: { empresaId, telefone },
    timeout: 15000,
  });
  const dados = response.data || null;
  if (!dados?.success) {
    console.warn('[bot-cancelamento] protocolo não encontrado no backend', {
      empresaId,
      protocolo,
      mensagem: dados?.mensagem || null,
    });
    return null;
  }
  return dados;
}

async function buscarCancelamentoPorData({ backendUrl, backendToken, empresaId, data, telefone }) {
  const base = String(backendUrl || '').replace(/\/+$/, '');
  const response = await axios.get(`${base}/api/internal/whatsapp/agendamentos`, {
    headers: backendToken ? { 'X-Internal-Token': backendToken } : {},
    params: { empresaId, data, telefone },
    timeout: 15000,
  });
  return Array.isArray(response.data) ? response.data : [];
}

async function cancelarAgendamentoBackend({ backendUrl, backendToken, empresaId, agendamentoId }) {
  const base = String(backendUrl || '').replace(/\/+$/, '');
  const response = await axios.post(`${base}/api/internal/whatsapp/agendamentos/${agendamentoId}/cancelar`, {
    empresaId,
    origem: 'WHATSAPP',
  }, {
    headers: backendToken ? { 'X-Internal-Token': backendToken } : {},
    timeout: 15000,
  });
  return response.data || {};
}

function formatarDataBrasileira(dataIso) {
  if (!dataIso) return '';
  const [ano, mes, dia] = String(dataIso).split('-');
  return `${dia}/${mes}/${ano}`;
}

function formatarHorario(valor) {
  const t = String(valor || '').trim();
  if (!t) return '';
  return t.length === 5 ? t : t.slice(0, 5);
}

function montarMensagemConfirmacao(item) {
  return `Encontrei este agendamento:\n\nProtocolo: ${item.protocolo || '------'}\nNome: ${item.clienteNome || '-'}\nServiço: ${item.servicoNome || '-'}\nData: ${formatarDataBrasileira(item.data)}\nHorário: ${formatarHorario(item.horario || item.horaInicio)}\n\nDeseja confirmar o cancelamento?\nResponda sim ou não.`;
}

function montarMensagemSucesso(item) {
  return `Agendamento cancelado com sucesso.\n\nProtocolo: ${item.protocolo || '------'}\nServiço: ${item.servicoNome || '-'}\nData: ${formatarDataBrasileira(item.data)}\nHorário: ${formatarHorario(item.horario || item.horaInicio)}`;
}

async function conduzirFluxoCancelamento({ estado, texto, contexto, empresaId, remoteJid, telefoneCliente, backendUrl, backendToken }) {
  const fluxo = estado.fluxoAgendamento || resetarFluxoCancelamento(estado);
  const linkAgendamento = normalizarTexto(contexto?.linkGerenciamento || contexto?.linkAgendamento || '');
  const telefone = normalizarTexto(telefoneCliente || '');
  const entrada = normalizarTexto(texto);

  console.log('[bot-cancelamento] entrada recebida', {
    empresaId,
    remoteJid,
    texto: entrada,
    etapa: fluxo.etapa || null,
  });

  if (fluxo.etapa === 'AGUARDANDO_CONFIRMACAO_CANCELAMENTO' && /^(nao|não|n|cancelar|cancela)$/i.test(normalizarBusca(entrada))) {
    resetarFluxoCancelamento(estado);
    estado.updatedAt = Date.now();
    return 'Sem problemas! O agendamento não foi cancelado.';
  }

  if (fluxo.etapa === 'AGUARDANDO_CONFIRMACAO_CANCELAMENTO' && /^(sim|s|ok|confirmar|confirma)$/i.test(normalizarBusca(entrada))) {
    const item = normalizarAgendamentoCancelamento(fluxo.agendamentoCancelamento);
    if (!item?.id) {
      resetarFluxoCancelamento(estado);
      return 'Não consegui localizar o agendamento para cancelar.';
    }
    try {
      const resposta = await cancelarAgendamentoBackend({ backendUrl, backendToken, empresaId, agendamentoId: item.id });
      console.log('[bot-cancelamento] cancelamento confirmado', {
        empresaId,
        remoteJid,
        agendamentoId: item.id,
        protocolo: item.protocolo || null,
      });
      resetarFluxoCancelamento(estado);
      estado.updatedAt = Date.now();
      return resposta?.success === false && resposta?.mensagem
        ? resposta.mensagem
        : montarMensagemSucesso(item);
    } catch (error) {
      const mensagem = String(error.response?.data?.mensagem || error.message || '');
      if (String(error.response?.data?.erro || '').includes('CANCELAMENTO_FORA_DO_PRAZO')) {
        return 'Não consegui cancelar por aqui porque o cancelamento só é permitido até 30 minutos antes do horário marcado.';
      }
      return mensagem || 'Não foi possível cancelar este agendamento agora.';
    }
  }

  const protocolo = extrairProtocolo(entrada);
  console.log('[bot-cancelamento] protocolo extraido', {
    empresaId,
    remoteJid,
    protocolo,
  });
  if (protocolo) {
    try {
      const encontrado = normalizarAgendamentoCancelamento(await buscarCancelamentoPorProtocolo({ backendUrl, backendToken, empresaId, protocolo, telefone }), protocolo);
      if (!encontrado?.id) {
        return 'Não encontrei nenhum agendamento com esse protocolo.';
      }
      fluxo.etapa = 'AGUARDANDO_CONFIRMACAO_CANCELAMENTO';
      fluxo.ativo = true;
      fluxo.updatedAt = Date.now();
      fluxo.agendamentoCancelamento = encontrado;
      console.log('[bot-cancelamento] agendamento localizado', {
        empresaId,
        remoteJid,
        protocolo,
        agendamentoId: encontrado.id,
      });
      return montarMensagemConfirmacao(encontrado);
    } catch (error) {
      return 'Não encontrei nenhum agendamento com esse protocolo.';
    }
  }

  const dataSelecionada = parseDataCliente(entrada);
  console.log('[bot-cancelamento] data extraida', {
    empresaId,
    remoteJid,
    dataSelecionada,
  });
  const horario = extrairHorario(entrada);
  console.log('[bot-cancelamento] horario extraido', {
    empresaId,
    remoteJid,
    horario,
  });

  if (dataSelecionada) {
    try {
      const lista = await buscarCancelamentoPorData({ backendUrl, backendToken, empresaId, data: dataSelecionada, telefone });
      if (!lista.length) {
        return 'Não encontrei agendamentos nessa data. Você pode me enviar o protocolo de 6 dígitos ou outra data?';
      }
      if (horario) {
        const escolhido = lista.find((item) => normalizarTexto(item.horaInicio).startsWith(horario.slice(0, 5)));
        if (escolhido) {
          fluxo.etapa = 'AGUARDANDO_CONFIRMACAO_CANCELAMENTO';
          fluxo.ativo = true;
          fluxo.updatedAt = Date.now();
          fluxo.agendamentoCancelamento = escolhido;
          console.log('[bot-cancelamento] agendamento localizado', {
            empresaId,
            remoteJid,
            protocolo: escolhido.protocolo || null,
            agendamentoId: escolhido.id,
          });
          return montarMensagemConfirmacao(escolhido);
        }
      }
      if (lista.length === 1) {
        const unico = lista[0];
        fluxo.etapa = 'AGUARDANDO_CONFIRMACAO_CANCELAMENTO';
        fluxo.ativo = true;
        fluxo.updatedAt = Date.now();
        fluxo.agendamentoCancelamento = unico;
        console.log('[bot-cancelamento] agendamento localizado', {
          empresaId,
          remoteJid,
          protocolo: unico.protocolo || null,
          agendamentoId: unico.id,
        });
        return montarMensagemConfirmacao(unico);
      }
      fluxo.etapa = 'AGUARDANDO_ESCOLHA_AGENDAMENTO_CANCELAMENTO';
      fluxo.ativo = true;
      fluxo.updatedAt = Date.now();
      fluxo.opcoesCancelamento = lista.map((item) => ({
        id: item.id,
        protocolo: item.protocolo,
        clienteNome: item.clienteNome,
        servicoNome: item.servicoNome,
        data: item.data,
        horaInicio: item.horaInicio,
      }));
      const linhas = lista.map((item, index) => `${index + 1}. #${item.protocolo || '------'} - ${item.servicoNome || '-'} - ${formatarHorario(item.horaInicio)}`);
      return `Encontrei mais de um agendamento nesse dia:\n\n${linhas.join('\n')}\n\nQual deles deseja cancelar?`;
    } catch (error) {
      return 'Não encontrei agendamentos nessa data. Você pode me enviar o protocolo de 6 dígitos ou outra data?';
    }
  }

  if (horario) {
    fluxo.etapa = 'AGUARDANDO_DATA_CANCELAMENTO';
    fluxo.ativo = true;
    fluxo.updatedAt = Date.now();
    fluxo.horarioSelecionado = horario;
    return 'Entendi o horário. Agora me informe a data do agendamento, por favor.';
  }

  if (fluxo.etapa === 'AGUARDANDO_ESCOLHA_AGENDAMENTO_CANCELAMENTO' && /^\d+$/.test(normalizarBusca(entrada))) {
    const indice = Number(normalizarBusca(entrada)) - 1;
    const escolha = Array.isArray(fluxo.opcoesCancelamento) ? fluxo.opcoesCancelamento[indice] : null;
    if (escolha) {
      fluxo.etapa = 'AGUARDANDO_CONFIRMACAO_CANCELAMENTO';
      fluxo.ativo = true;
      fluxo.updatedAt = Date.now();
      fluxo.agendamentoCancelamento = escolha;
      console.log('[bot-cancelamento] agendamento localizado', {
        empresaId,
        remoteJid,
        protocolo: escolha.protocolo || null,
        agendamentoId: escolha.id,
      });
      return montarMensagemConfirmacao(escolha);
    }
  }

  if (fluxo.etapa === 'AGUARDANDO_DATA_CANCELAMENTO' && fluxo.horarioSelecionado) {
    return 'Me informe a data do agendamento, por favor.';
  }

  if (prefereLink(entrada)) {
    resetarFluxoCancelamento(estado);
    return linkAgendamento
      ? `Claro. Use o link de gerenciamento para cancelar o agendamento:\n${linkAgendamento}`
      : 'Ainda não encontrei o link de gerenciamento configurado para esta empresa.';
  }

  if (prefereAgendarPorWhatsapp(entrada)) {
    fluxo.etapa = 'AGUARDANDO_ESCOLHA_CANAL_CANCELAMENTO';
    fluxo.ativo = true;
    fluxo.updatedAt = Date.now();
    return 'Certo. Me informe o protocolo do agendamento ou a data e horário que deseja cancelar.';
  }

  return 'Certo. Me informe o protocolo do agendamento ou a data e horário que deseja cancelar.';
}

module.exports = {
  conduzirFluxoCancelamento,
  fluxoExpiradoCancelamento,
  resetarFluxoCancelamento,
  extrairProtocolo,
  extrairHorario,
  parseDataCliente,
  buscarCancelamentoPorProtocolo,
  buscarCancelamentoPorData,
};
