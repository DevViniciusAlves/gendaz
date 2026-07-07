const axios = require('axios');
const {
  extrairProtocolo,
  extrairHorario,
  parseDataCliente,
  buscarCancelamentoPorProtocolo,
  buscarCancelamentoPorData,
} = require('./fluxoCancelamento');
const {
  interpretarHorarioNatural,
} = require('./fluxoAgendamento');

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

function prefereAgendarPorWhatsapp(texto) {
  const valor = normalizarBusca(texto);
  return [
    'aq',
    'aqui',
    'por aq',
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

function respostaPositiva(texto) {
  const valor = normalizarBusca(texto);
  return [
    'sim',
    'pode ser',
    'pode',
    'confirma',
    'confirmado',
    'isso',
    'isso mesmo',
    'ok',
    'beleza',
    'fechado',
    'certo',
    'e isso',
    'é isso',
  ].some((item) => valor === normalizarBusca(item) || valor.includes(normalizarBusca(item)));
}

function respostaNegativa(texto) {
  const valor = normalizarBusca(texto);
  return [
    'não',
    'nao',
    'nao quero esse horario',
    'não quero esse horário',
    'ta errado',
    'está errado',
    'errado',
    'nao e esse',
    'não é esse',
    'muda',
    'quero outro',
    'não pode',
    'nao pode',
    'não serve',
  ].some((item) => valor.includes(normalizarBusca(item)));
}

function resetarFluxoReagendamento(estado) {
  estado.fluxoAgendamento = {
    ativo: false,
    tipoFluxo: 'REAGENDAMENTO',
    etapa: null,
    modoSelecionado: null,
    agendamentoAtual: null,
    agendamentosDisponiveis: [],
    novaDataSelecionada: null,
    novosHorariosDisponiveis: [],
    novoHorarioSelecionado: null,
    profissionaisDisponiveis: [],
    profissionalSelecionado: null,
    etapaRetornoAoNegar: null,
    ultimaPergunta: null,
  };
  return estado.fluxoAgendamento;
}

function fluxoExpiradoReagendamento(estado) {
  return Boolean(estado?.fluxoAgendamento?.ativo)
    && (Date.now() - Number(estado?.updatedAt || 0)) > 15 * 60 * 1000;
}

function normalizarAgendamentoReagendamento(dados, protocoloFallback = null) {
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
    servicoId: agendamento.servicoId
      ?? agendamento.servico?.id
      ?? agendamento.idServico
      ?? agendamento.servicoEntity?.id
      ?? null,
    profissionalId: agendamento.profissionalId
      ?? agendamento.profissional?.id
      ?? agendamento.idProfissional
      ?? agendamento.profissionalEntity?.id
      ?? null,
    profissionalNome: agendamento.profissionalNome || agendamento.profissional?.nome || null,
  };
}

async function reagendarAgendamentoBackend({ backendUrl, backendToken, empresaId, agendamentoId, novaData, novoHorario, profissionalId }) {
  const base = String(backendUrl || '').replace(/\/+$/, '');
  const response = await axios.put(`${base}/api/internal/whatsapp/agendamentos/${agendamentoId}/reagendar`, {
    empresaId,
    novaData,
    novoHorario,
    profissionalId: profissionalId || null,
  }, {
    headers: backendToken ? { 'X-Internal-Token': backendToken } : {},
    timeout: 15000,
  });
  return response.data || {};
}

async function buscarProfissionaisEmpresa({ backendUrl, backendToken, empresaId, servicoId }) {
  const base = String(backendUrl || '').replace(/\/+$/, '');
  try {
    const response = await axios.get(`${base}/api/internal/whatsapp/profissionais/${empresaId}`, {
      headers: backendToken ? { 'X-Internal-Token': backendToken } : {},
      params: { servicoId: servicoId || undefined },
      timeout: 15000,
    });
    return Array.isArray(response.data) ? response.data : [];
  } catch (error) {
    console.warn('[bot-reagendamento] falha ao buscar profissionais', {
      empresaId,
      servicoId,
      detalhe: error.message,
    });
    return [];
  }
}

async function obterHorariosDisponiveisReagendamento({ backendUrl, backendToken, empresaId, servicoId, data, profissionalId }) {
  const base = String(backendUrl || '').replace(/\/+$/, '');
  const response = await axios.get(`${base}/api/internal/whatsapp/disponibilidade/${empresaId}`, {
    headers: backendToken ? { 'X-Internal-Token': backendToken } : {},
    params: {
      servicoId,
      data,
      profissionalId: profissionalId || undefined,
    },
    timeout: 15000,
  });
  const horarios = Array.isArray(response.data?.horarios) ? response.data.horarios : [];
  const valorTexto = String(data || '').trim();
  return horarios
    .map((item) => normalizarTexto(item))
    .filter(Boolean)
    .filter((horario) => {
      if (!/^(\d{4}-\d{2}-\d{2})$/.test(valorTexto)) return true;
      return true;
    });
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

function montarResumoReagendamento(agendamento, novaData, novoHorario, profissional = null) {
  return [
    'Vamos remarcar este agendamento:',
    '',
    `Protocolo: ${agendamento.protocolo || '------'}`,
    `Servi�o: ${agendamento.servicoNome || '-'}`,
    profissional?.nome ? `Profissional: ${profissional.nome}` : null,
    `De: ${formatarDataBrasileira(agendamento.data)} �s ${formatarHorario(agendamento.horario)}`,
    `Para: ${formatarDataBrasileira(novaData)} �s ${formatarHorario(novoHorario)}`,
    '',
    'Posso confirmar?',
  ].filter(Boolean).join('\n');
}

function montarMensagemEscolhaAgendamento(lista) {
  const linhas = lista.map((item, index) => `${index + 1}. #${item.protocolo || '------'} - ${item.servicoNome || '-'} - ${formatarDataBrasileira(item.data)} ${formatarHorario(item.horario || item.horaInicio)}`);
  return `Encontrei mais de um agendamento nesse dia:\n\n${linhas.join('\n')}\n\nQual deles deseja remarcar?`;
}

function montarMensagemListaProfissionais(profissionais) {
  const lista = Array.isArray(profissionais) ? profissionais : [];
  const linhas = lista.map((p, i) => `${i + 1}. ${p?.nome || 'Profissional'}`);
  return `Com qual profissional você prefere?\n\n${linhas.join('\n')}`;
}

function identificarProfissionalEscolhido(texto, profissionais) {
  const lista = Array.isArray(profissionais) ? profissionais : [];
  const valor = normalizarBusca(texto);

  const matchNumero = valor.match(/^(\d+)$/);
  if (matchNumero) {
    const indice = parseInt(matchNumero[1], 10) - 1;
    if (lista[indice]) return lista[indice];
  }

  if (/(sem preferencia|qualquer|tanto faz|qualquer um|nao importa|não importa)/.test(valor)) {
    return lista[0] || null;
  }

  for (const profissional of lista) {
    const nome = normalizarBusca(profissional?.nome || '');
    if (!nome) continue;
    if (valor.includes(nome) || nome.includes(valor)) return profissional;
    const tokens = nome.split(' ').filter(Boolean);
    if (tokens.some((t) => t.length > 3 && valor.includes(t))) return profissional;
  }

  return null;
}

function textoPedidoNovaData() {
  return 'Certo. Me informe a nova data que você prefere para remarcar.';
}

function textoPedidoNovoHorario() {
  return 'Agora me diga o novo horário que você prefere.';
}

function textoRepetirConfirmacao() {
  return 'Não entendi. Pode confirmar dizendo "sim" ou "não"?';
}

function atualizarEtapa(fluxo, novaEtapa, contexto = {}) {
  const etapaAnterior = fluxo.etapa || null;
  fluxo.etapa = novaEtapa;
  fluxo.etapaRetornoAoNegar = etapaAnterior;
  console.log('[bot-fluxo] etapa atualizada', {
    empresaId: contexto.empresaId || null,
    remoteJid: contexto.remoteJid || null,
    etapaAnterior,
    novaEtapa,
  });
}

async function conduzirFluxoReagendamento({
  estado,
  texto,
  contexto,
  empresaId,
  telefoneCliente,
  backendUrl,
  backendToken,
  remoteJid,
}) {
  const fluxo = estado.fluxoAgendamento || resetarFluxoReagendamento(estado);
  const linkAgendamento = normalizarTexto(contexto?.linkGerenciamento || contexto?.linkAgendamento || '');
  const telefone = normalizarTexto(telefoneCliente || '');
  const entrada = normalizarTexto(texto);

  console.log('[bot-reagendamento] entrada recebida', {
    empresaId,
    remoteJid,
    texto: entrada,
    etapa: fluxo.etapa || null,
  });

  if (fluxo.etapa === 'ESCOLHENDO_MODO' || !fluxo.etapa) {
    if (prefereLink(entrada)) {
      resetarFluxoReagendamento(estado);
      return linkAgendamento
        ? `Claro. Use o link de gerenciamento para reagendar o agendamento:\n${linkAgendamento}`
        : 'Ainda não encontrei o link de gerenciamento configurado para esta empresa.';
    }
    if (prefereAgendarPorWhatsapp(entrada)) {
      fluxo.ativo = true;
      atualizarEtapa(fluxo, 'AGUARDANDO_DATA_ATUAL', { empresaId, remoteJid });
      estado.updatedAt = Date.now();
      return 'Certo. Me informe o protocolo do agendamento ou a data e horário do agendamento que deseja remarcar.';
    }
    return 'Claro. Você prefere reagendar por aqui mesmo ou pelo link de gerenciamento?';
  }

  if (fluxo.etapa === 'AGUARDANDO_ESCOLHA_CANAL_REAGENDAMENTO') {
    const escolha = prefereAgendarPorWhatsapp(entrada) ? 'WHATSAPP' : (prefereLink(entrada) ? 'LINK' : null);
    console.log('[bot-reagendamento] escolha de canal detectada', {
      empresaId,
      remoteJid,
      escolha,
      etapaAnterior: fluxo.etapa || null,
    });
    if (escolha === 'LINK') {
      resetarFluxoReagendamento(estado);
      return linkAgendamento
        ? `Claro. Use o link de gerenciamento para reagendar o agendamento:\n${linkAgendamento}`
        : 'Ainda não encontrei o link de gerenciamento configurado para esta empresa.';
    }
    if (escolha === 'WHATSAPP') {
      atualizarEtapa(fluxo, 'AGUARDANDO_DATA_ATUAL', { empresaId, remoteJid });
      estado.updatedAt = Date.now();
      return 'Certo. Me informe o protocolo do agendamento ou a data e horário do agendamento que deseja remarcar.';
    }
    return 'Claro. Você prefere reagendar por aqui mesmo ou pelo link de gerenciamento?';
  }

  if (fluxo.etapa === 'AGUARDANDO_DATA_ATUAL' || fluxo.etapa === 'ESCOLHENDO_AGENDAMENTO') {
    const protocolo = extrairProtocolo(entrada);
    console.log('[bot-reagendamento] protocolo extraido', {
      empresaId,
      remoteJid,
      protocolo,
    });
    if (protocolo) {
      try {
        const dados = await buscarCancelamentoPorProtocolo({ backendUrl, backendToken, empresaId, protocolo, telefone });
        console.log('[bot-reagendamento] resposta bruta do backend por protocolo', JSON.stringify(dados));
        if (!dados) {
          return 'Não encontrei nenhum agendamento com esse protocolo. Verifique e tente novamente.';
        }
        const encontrado = normalizarAgendamentoReagendamento(dados, protocolo);
        if (!encontrado?.id) {
          return 'Não encontrei nenhum agendamento com esse protocolo.';
        }
        fluxo.ativo = true;
        fluxo.agendamentoAtual = encontrado;
        atualizarEtapa(fluxo, 'AGUARDANDO_NOVA_DATA', { empresaId, remoteJid });
        estado.updatedAt = Date.now();
        console.log('[bot-reagendamento] agendamento localizado', {
          empresaId,
          remoteJid,
          protocolo,
          agendamentoId: encontrado.id,
          servicoId: encontrado.servicoId,
          profissionalId: encontrado.profissionalId,
        });
        if (!encontrado.servicoId) {
          console.warn('[bot-reagendamento] servicoId ausente após normalização', {
            empresaId,
            remoteJid,
            raw: JSON.stringify(dados),
          });
          return 'Não consegui identificar o serviço desse agendamento. Entre em contato com a empresa.';
        }
        return textoPedidoNovaData();
      } catch (error) {
        return 'Não encontrei nenhum agendamento com esse protocolo.';
      }
    }

    const dataSelecionada = parseDataCliente(entrada);
    console.log('[bot-reagendamento] data extraida', {
      empresaId,
      remoteJid,
      dataSelecionada,
    });
    const horario = extrairHorario(entrada);
    console.log('[bot-reagendamento] horario extraido', {
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
          const escolhido = lista.find((item) => normalizarTexto(item.horaInicio || item.horario).startsWith(horario.slice(0, 5)));
          if (escolhido) {
            fluxo.ativo = true;
            fluxo.agendamentoAtual = normalizarAgendamentoReagendamento(escolhido);
            atualizarEtapa(fluxo, 'AGUARDANDO_NOVA_DATA', { empresaId, remoteJid });
            estado.updatedAt = Date.now();
            console.log('[bot-reagendamento] agendamento localizado', {
              empresaId,
              remoteJid,
              protocolo: escolhido.protocolo || null,
              agendamentoId: escolhido.id,
            });
            return textoPedidoNovaData();
          }
        }
        if (lista.length === 1) {
          const unico = normalizarAgendamentoReagendamento(lista[0]);
          fluxo.ativo = true;
          fluxo.agendamentoAtual = unico;
          atualizarEtapa(fluxo, 'AGUARDANDO_NOVA_DATA', { empresaId, remoteJid });
          estado.updatedAt = Date.now();
          console.log('[bot-reagendamento] agendamento localizado', {
            empresaId,
            remoteJid,
            protocolo: unico.protocolo || null,
            agendamentoId: unico.id,
          });
          return textoPedidoNovaData();
        }
        fluxo.ativo = true;
        fluxo.agendamentosDisponiveis = lista.map(normalizarAgendamentoReagendamento);
        atualizarEtapa(fluxo, 'AGUARDANDO_ESCOLHA_AGENDAMENTO_REAGENDAMENTO', { empresaId, remoteJid });
        estado.updatedAt = Date.now();
        return montarMensagemEscolhaAgendamento(fluxo.agendamentosDisponiveis);
      } catch (error) {
        return 'Não encontrei agendamentos nessa data. Você pode me enviar o protocolo de 6 dígitos ou outra data?';
      }
    }

    if (horario) {
      fluxo.ativo = true;
      fluxo.horarioSelecionado = horario;
      atualizarEtapa(fluxo, 'AGUARDANDO_NOVA_DATA', { empresaId, remoteJid });
      estado.updatedAt = Date.now();
      return 'Certo. Me informe a data do agendamento que deseja remarcar.';
    }

    return 'Certo. Me informe o protocolo do agendamento ou a data e horário do agendamento que deseja remarcar.';
  }

  if (fluxo.etapa === 'AGUARDANDO_ESCOLHA_AGENDAMENTO_REAGENDAMENTO') {
    const indice = Number(normalizarBusca(entrada));
    if (Number.isInteger(indice) && indice >= 1) {
      const escolha = Array.isArray(fluxo.agendamentosDisponiveis) ? fluxo.agendamentosDisponiveis[indice - 1] : null;
      if (escolha) {
        fluxo.agendamentoAtual = escolha;
        atualizarEtapa(fluxo, 'AGUARDANDO_NOVA_DATA', { empresaId, remoteJid });
        estado.updatedAt = Date.now();
        console.log('[bot-reagendamento] agendamento selecionado', {
          empresaId,
          remoteJid,
          protocolo: escolha.protocolo || null,
          agendamentoId: escolha.id,
        });
        return textoPedidoNovaData();
      }
    }
    return 'Não consegui identificar qual agendamento você quer remarcar. Responda com o número da opção.';
  }

  if (fluxo.etapa === 'AGUARDANDO_NOVA_DATA') {
    if (respostaNegativa(entrada)) {
      return 'Sem problemas. Me diga a nova data que você prefere para remarcar.';
    }
    const novaData = parseDataCliente(entrada);
    console.log('[bot-reagendamento] nova data interpretada', {
      empresaId,
      remoteJid,
      novaData,
    });
    if (!novaData) {
      return 'Não entendi a nova data. Pode me dizer, por exemplo, "amanhã", "terça" ou "07/08"?';
    }
    fluxo.novaDataSelecionada = novaData;
    console.log('[bot-reagendamento] dados do agendamento atual', {
      empresaId,
      agendamentoId: fluxo.agendamentoAtual?.id,
      servicoId: fluxo.agendamentoAtual?.servicoId,
      profissionalId: fluxo.agendamentoAtual?.profissionalId,
      agendamentoRaw: JSON.stringify(fluxo.agendamentoAtual),
    });
    if (!fluxo.agendamentoAtual?.servicoId) {
      console.warn('[bot-reagendamento] servicoId ausente no agendamento', {
        empresaId,
        agendamentoId: fluxo.agendamentoAtual?.id,
        agendamentoRaw: JSON.stringify(fluxo.agendamentoAtual),
      });
      return 'Não consegui identificar o serviço desse agendamento. Tente informar o protocolo novamente.';
    }
    try {
      const horarios = await obterHorariosDisponiveisReagendamento({
        backendUrl,
        backendToken,
        empresaId,
        servicoId: fluxo.agendamentoAtual?.servicoId,
        data: novaData,
        profissionalId: fluxo.agendamentoAtual?.profissionalId || null,
      });
      if (!horarios.length) {
        fluxo.novaDataSelecionada = null;
        atualizarEtapa(fluxo, 'AGUARDANDO_NOVA_DATA', { empresaId, remoteJid });
        estado.updatedAt = Date.now();
        return 'Não encontrei horários livres nessa data. Quer tentar outro dia?';
      }
      fluxo.novosHorariosDisponiveis = horarios;
      atualizarEtapa(fluxo, 'AGUARDANDO_NOVO_HORARIO', { empresaId, remoteJid });
      estado.updatedAt = Date.now();
      return `Tenho esses horários disponíveis para ${formatarDataBrasileira(novaData)}:\n${horarios.join('\n')}\n\nQual horário você prefere?`;
    } catch (error) {
      console.warn('[bot-reagendamento] falha ao consultar horarios', {
        empresaId,
        remoteJid,
        servicoId: fluxo.agendamentoAtual?.servicoId,
        profissionalId: fluxo.agendamentoAtual?.profissionalId,
        data: novaData,
        status: error.response?.status,
        respostaBackend: error.response?.data,
        codigoErro: error.code,
        detalhe: error.message,
      });
      return 'Não consegui verificar os horários agora. Tente novamente em instantes.';
    }
  }

  if (fluxo.etapa === 'AGUARDANDO_NOVO_HORARIO') {
    if (respostaNegativa(entrada)) {
      fluxo.novaDataSelecionada = null;
      fluxo.novosHorariosDisponiveis = [];
      atualizarEtapa(fluxo, 'AGUARDANDO_NOVA_DATA', { empresaId, remoteJid });
      estado.updatedAt = Date.now();
      return 'Sem problemas. Me diga a nova data que você prefere para remarcar.';
    }
    const novaDataDetectada = parseDataCliente(entrada);
    if (novaDataDetectada) {
      fluxo.novaDataSelecionada = novaDataDetectada;
      console.log('[bot-reagendamento] dados do agendamento atual', {
        empresaId,
        agendamentoId: fluxo.agendamentoAtual?.id,
        servicoId: fluxo.agendamentoAtual?.servicoId,
        profissionalId: fluxo.agendamentoAtual?.profissionalId,
        agendamentoRaw: JSON.stringify(fluxo.agendamentoAtual),
      });
      if (!fluxo.agendamentoAtual?.servicoId) {
        console.warn('[bot-reagendamento] servicoId ausente no agendamento', {
          empresaId,
          agendamentoId: fluxo.agendamentoAtual?.id,
          agendamentoRaw: JSON.stringify(fluxo.agendamentoAtual),
        });
        return 'Não consegui identificar o serviço desse agendamento. Tente informar o protocolo novamente.';
      }
      try {
        const horarios = await obterHorariosDisponiveisReagendamento({
          backendUrl,
          backendToken,
          empresaId,
          servicoId: fluxo.agendamentoAtual?.servicoId,
          data: novaDataDetectada,
          profissionalId: fluxo.profissionalSelecionado?.id || null,
        });
        if (!horarios.length) {
          return 'Não encontrei horários livres nessa data. Quer tentar outro dia?';
        }
        fluxo.novosHorariosDisponiveis = horarios;
        atualizarEtapa(fluxo, 'AGUARDANDO_NOVO_HORARIO', { empresaId, remoteJid });
        estado.updatedAt = Date.now();
        return `Tenho esses horários disponíveis para ${formatarDataBrasileira(novaDataDetectada)}:\n${horarios.join('\n')}\n\nQual horário você prefere?`;
      } catch (error) {
        console.warn('[bot-reagendamento] falha ao consultar horarios', {
          empresaId,
          remoteJid,
          servicoId: fluxo.agendamentoAtual?.servicoId,
          profissionalId: fluxo.agendamentoAtual?.profissionalId,
          data: novaDataDetectada,
          status: error.response?.status,
          respostaBackend: error.response?.data,
          codigoErro: error.code,
          detalhe: error.message,
        });
        return 'Não consegui verificar os horários agora. Tente novamente em instantes.';
      }
    }

    const horario = interpretarHorarioNatural(entrada, fluxo.novosHorariosDisponiveis || []);
    if (!horario) {
      if (!Array.isArray(fluxo.novosHorariosDisponiveis) || !fluxo.novosHorariosDisponiveis.length) {
        return 'Não encontrei horários livres nessa data. Quer tentar outro dia?';
      }
      return `Não entendi esse horário. Escolha um destes:\n${fluxo.novosHorariosDisponiveis.join('\n')}`;
    }
    fluxo.novoHorarioSelecionado = horario;
    const profissionais = await buscarProfissionaisEmpresa({
      backendUrl,
      backendToken,
      empresaId,
      servicoId: fluxo.agendamentoAtual?.servicoId,
    });
    fluxo.profissionaisDisponiveis = profissionais;
    if (!profissionais.length || profissionais.length === 1) {
      fluxo.profissionalSelecionado = profissionais[0] || null;
      atualizarEtapa(fluxo, 'AGUARDANDO_CONFIRMACAO_REAGENDAMENTO', { empresaId, remoteJid });
      estado.updatedAt = Date.now();
      return montarResumoReagendamento(fluxo.agendamentoAtual, fluxo.novaDataSelecionada, horario, fluxo.profissionalSelecionado);
    }
    atualizarEtapa(fluxo, 'ESCOLHENDO_PROFISSIONAL', { empresaId, remoteJid });
    estado.updatedAt = Date.now();
    return montarMensagemListaProfissionais(profissionais);
  }

  if (fluxo.etapa === 'ESCOLHENDO_PROFISSIONAL') {
    const profissional = identificarProfissionalEscolhido(entrada, fluxo.profissionaisDisponiveis);
    if (!profissional) {
      return `N�o entendi. Escolha um dos profissionais:\n${montarMensagemListaProfissionais(fluxo.profissionaisDisponiveis)}`;
    }
    fluxo.profissionalSelecionado = profissional;
    atualizarEtapa(fluxo, 'AGUARDANDO_CONFIRMACAO_REAGENDAMENTO', { empresaId, remoteJid });
    estado.updatedAt = Date.now();
    return montarResumoReagendamento(fluxo.agendamentoAtual, fluxo.novaDataSelecionada, fluxo.novoHorarioSelecionado, profissional);
  }

  if (fluxo.etapa === 'AGUARDANDO_CONFIRMACAO_REAGENDAMENTO') {
    if (respostaPositiva(entrada)) {
      if (!fluxo.agendamentoAtual?.id || !fluxo.novaDataSelecionada || !fluxo.novoHorarioSelecionado) {
        atualizarEtapa(fluxo, 'AGUARDANDO_NOVA_DATA', { empresaId, remoteJid });
        estado.updatedAt = Date.now();
        return 'Não consegui confirmar os dados. Me diga a nova data novamente.';
      }
      try {
        const resposta = await reagendarAgendamentoBackend({
          backendUrl,
          backendToken,
          empresaId,
          agendamentoId: fluxo.agendamentoAtual.id,
          novaData: fluxo.novaDataSelecionada,
          novoHorario: fluxo.novoHorarioSelecionado,
          profissionalId: fluxo.profissionalSelecionado?.id || null,
        });
        console.log('[bot-reagendamento] reagendamento confirmado', {
          empresaId,
          remoteJid,
          agendamentoId: fluxo.agendamentoAtual.id,
          novaData: fluxo.novaDataSelecionada,
          novoHorario: fluxo.novoHorarioSelecionado,
          profissionalId: fluxo.profissionalSelecionado?.id || null,
        });
        const nomeCliente = fluxo.agendamentoAtual?.clienteNome || '-';
        const servico = fluxo.agendamentoAtual?.servicoNome || '-';
        const mensagem = [
          `Prontinho! Seu agendamento foi remarcado para ${formatarDataBrasileira(fluxo.novaDataSelecionada)} às ${formatarHorario(fluxo.novoHorarioSelecionado)}.`,
          `Serviço: ${servico}`,
          `Cliente: ${nomeCliente}`,
          resposta?.protocolo ? `Protocolo: ${resposta.protocolo}` : null,
          'Até lá!',
        ].filter(Boolean).join('\n');
        resetarFluxoReagendamento(estado);
        estado.updatedAt = Date.now();
        return mensagem;
      } catch (error) {
        console.warn('[bot-reagendamento] falha ao reagendar', {
          empresaId,
          remoteJid,
          detalhe: error.message,
        });
        return error.response?.data?.mensagem || 'Não consegui remarcar agora. Tente novamente em instantes.';
      }
    }

    if (respostaNegativa(entrada)) {
      const retorno = fluxo.etapaRetornoAoNegar === 'AGUARDANDO_NOVO_HORARIO'
        ? 'AGUARDANDO_NOVO_HORARIO'
        : 'AGUARDANDO_NOVA_DATA';
      atualizarEtapa(fluxo, retorno, { empresaId, remoteJid });
      estado.updatedAt = Date.now();
      return retorno === 'AGUARDANDO_NOVO_HORARIO'
        ? textoPedidoNovoHorario()
        : 'Sem problemas. Me diga a nova data que você prefere para remarcar.';
    }

    return textoRepetirConfirmacao();
  }

  return 'Certo. Me informe o protocolo do agendamento ou a data e horário do agendamento que deseja remarcar.';
}

module.exports = {
  conduzirFluxoReagendamento,
  fluxoExpiradoReagendamento,
  resetarFluxoReagendamento,
};

