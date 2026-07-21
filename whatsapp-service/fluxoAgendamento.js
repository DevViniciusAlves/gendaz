/*
+----------------------------------------+
¦    DESATIVADO - FUNCIONALIDADE WhatsApp ¦
¦  Código comentado. Remova comentários  ¦
¦  para reativar.                        ¦
+----------------------------------------+
*/

const axios = require('axios');
const Groq = require('groq-sdk');

// TODO: migrar este estado para Redis ou tabela no banco se os fluxos ficarem longos
// ou se o serviço reiniciar com frequência durante conversas ativas.

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

function respostaPositiva(texto) {
  const valor = normalizarBusca(texto);
  return /^(sim|s|ok|pode|confirma|confirmo|isso|exato|certo|beleza|fechado|com\s?certeza|claro|e\s?isso|e\s?exato|pode\s?ser|valeu|ta\s?bom|ta\s?certo|combinado|blz|bleza|show|maravilha|perfeito|opa|opa\s?ok|ok\s?opa)/.test(valor);
}

function respostaNegativa(texto) {
  const valor = normalizarBusca(texto);
  return /^(nao|não|cancelar|cancela|deixa|esquece)/.test(valor);
}

function prefereAgendarPorWhatsapp(texto) {
  const valor = normalizarBusca(texto);
  const palavrasChave = [
    'por aq', 'aq','2',
    'por aqui', 'pelo whatsapp', 'faz por aqui', 'aqui mesmo',
    'nao quero link', 'não quero link', 'sem link', 'me ajuda por aqui',
    'pode ser aqui', 'marca pra mim', 'marca para mim', 'melhor voce marcar',
    'melhor você marcar', 'voce marca', 'você marca', 'quero por aqui',
    'continuar por aqui', 'nao sei mexer', 'não sei mexer', 'pode resolver',
    'resolve ai', 'resolve aí', 'voce mesmo', 'você mesmo', 'prefiro aqui',
  ];
  return palavrasChave.some((p) => valor.includes(normalizarBusca(p)));
}

function prefereLink(texto) {
  const valor = normalizarBusca(texto);
  const palavrasChave = [
    '1',
    'link', 'pelo link', 'prefiro o link', 'manda o link', 'envia o link',
    'quero o link', 'pode ser o link',
  ];
  return palavrasChave.some((p) => valor.includes(normalizarBusca(p)));
}

function detectarEscolhaCanalAgendamento(texto) {
  const t = normalizarBusca(texto);

  if (
    t.includes('por aqui') ||
    t.includes('por aq') ||
    t.includes('aqui mesmo') ||
    t.includes('whatsapp') ||
    t.includes('pelo whatsapp') ||
    t.includes('2'),
    t === 'aqui'
  ) {
    return 'WHATSAPP';
  }

  if (
    t.includes('link') ||
    t.includes('site') ||
    t.includes('1'),
    t.includes('pelo link')
  ) {
    return 'LINK';
  }

  return null;
}

async function classificarPreferenciaComIA(texto, groqApiKey) {
  if (!groqApiKey) return null;
  try {
    const groq = new Groq({ apiKey: groqApiKey });
    const chat = await groq.chat.completions.create({
      model: 'llama-3.1-8b-instant',
      messages: [{
        role: 'system',
        content: 'Classifique a mensagem do cliente em uma palavra: "WHATSAPP" se ele quer que você conduza o agendamento na conversa, ou "LINK" se ele prefere usar o link. Se não estiver claro, responda "INDEFINIDO". Responda APENAS a palavra.',
      }, { role: 'user', content: texto }],
      temperature: 0,
      max_tokens: 10,
    });
    const resultado = normalizarBusca(chat.choices?.[0]?.message?.content || '');
    if (resultado.includes('whatsapp')) return 'WHATSAPP';
    if (resultado.includes('link')) return 'LINK';
    return null;
  } catch {
    return null;
  }
}

function garantirFluxo(estado) {
  if (!estado.fluxoAgendamento) {
    estado.fluxoAgendamento = {
      ativo: false,
      etapa: null,
      tipoFluxo: 'AGENDAMENTO',
      modoSelecionado: null,
      clienteNome: null,
      servicosDisponiveis: [],
      servicoSelecionado: null,
      profissionaisDisponiveis: [],
      profissionalSelecionado: null,
      dataSelecionada: null,
      horariosDisponiveis: [],
      horarioSelecionado: null,
      nomeCliente: null,
      ultimaPergunta: null,
    };
  }
  return estado.fluxoAgendamento;
}

function resetarFluxoAgendamento(estado) {
  estado.fluxoAgendamento = {
    ativo: false,
    etapa: null,
    tipoFluxo: 'AGENDAMENTO',
    modoSelecionado: null,
    clienteNome: null,
    servicosDisponiveis: [],
    servicoSelecionado: null,
    profissionaisDisponiveis: [],
    profissionalSelecionado: null,
    dataSelecionada: null,
    horariosDisponiveis: [],
    horarioSelecionado: null,
    nomeCliente: null,
    ultimaPergunta: null,
  };
  return estado.fluxoAgendamento;
}

function iniciarEscolhaCanalAgendamento(fluxo, { empresaId, remoteJid, linkAgendamento }) {
  fluxo.ativo = true;
  fluxo.etapa = 'AGUARDANDO_ESCOLHA_CANAL_AGENDAMENTO';
  fluxo.modoSelecionado = null;
  fluxo.updatedAt = Date.now();
  console.log('[Scheduling] primeira oferta enviada', {
    empresaId,
    remoteJid,
    linkAgendamento,
  });
  return `Claro! Você pode agendar de duas formas:\n\n1. Pelo link, escolhendo serviço, dia e horário:\n${linkAgendamento}\n\n2. Ou, se preferir, posso te ajudar por aqui pelo WhatsApp.\n\nVocê prefere fazer pelo link ou por aqui?`;
}

function montarMensagemSucessoAgendamento({ nome, servico, data, horario, protocolo, profissional }) {
  const dataFormatada = new Date(`${data}T12:00:00`).toLocaleDateString('pt-BR');
  const linhas = [
    'Agendamento confirmado com sucesso!',
    protocolo ? `Protocolo: ${protocolo}` : null,
    `Nome: ${nome}`,
    `Serviço: ${servico}`,
    `Data: ${dataFormatada}`,
    `Horário: ${horario}`,
    profissional ? `Profissional: ${profissional}` : null,
    'Guarde esse protocolo. Ele pode ser usado caso você queira cancelar ou reagendar.',
  ];
  return linhas.filter(Boolean).join('\n');
}

function atualizarEtapaFluxo(fluxo, novaEtapa, contexto = {}) {
  const etapaAnterior = fluxo.etapa || null;
  fluxo.etapa = novaEtapa;
  console.log('[bot-fluxo] etapa atualizada', {
    empresaId: contexto.empresaId || null,
    remoteJid: contexto.remoteJid || null,
    etapaAnterior,
    novaEtapa,
  });
  return fluxo;
}

function fluxoExpirado(estado) {
  return Boolean(estado?.fluxoAgendamento?.ativo)
    && (Date.now() - Number(estado?.updatedAt || 0)) > 10 * 60 * 1000;
}

function formatarMoeda(valor) {
  const numero = Number(valor);
  if (Number.isNaN(numero)) return '';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(numero);
}

function montarMensagemListaServicos(servicos) {
  const linhas = (Array.isArray(servicos) ? servicos : []).map((s, i) => `${i + 1}. ${s.nome} - ${formatarMoeda(s.valor) || 'sob consulta'}`);
  return `Qual serviço você deseja?\n\n${linhas.join('\n')}`;
}

function montarMensagemListaProfissionais(profissionais) {
  const linhas = (Array.isArray(profissionais) ? profissionais : []).map((profissional, i) => {
    const nomeFormatado = formatarProfissionalLista(profissional);
    return `${i + 1}. ${nomeFormatado || 'Profissional'}`;
  });
  return `Agora escolha o profissional:\n\n${linhas.join('\n')}`;
}

function mensagemInicioAgendamento(linkAgendamento) {
  return linkAgendamento
    ? `Claro! Você pode agendar de duas formas:\n\n1. Pelo link, escolhendo serviço, dia e horário:\n${linkAgendamento}\n\n2. Ou, se preferir, posso te ajudar por aqui pelo WhatsApp.\n\nVocê prefere fazer pelo link ou por aqui?`
    : 'Claro! Você pode agendar de duas formas:\n\n1. Pelo link oficial de agendamento.\n\n2. Ou, se preferir, posso te ajudar por aqui pelo WhatsApp.\n\nVocê prefere fazer pelo link ou por aqui?';
}

function nomeClienteValido(texto) {
  const valor = String(texto || '').trim();
  if (valor.length < 2) return '';
  if (/^(sim|nao|não|ok|okey|pode|claro|beleza|tudo bem)$/i.test(valor)) return '';
  if (!/[a-záàâãéèêíïóôõúç]/i.test(valor)) return '';
  return valor.replace(/\s+/g, ' ');
}

function identificarServicoMencionado(texto, servicos) {
  const valor = normalizarBusca(texto);
  if (!valor) return null;
  for (const servico of servicos) {
    const nome = normalizarBusca(servico?.nome || '');
    if (!nome) continue;
    if (valor.includes(nome)) return servico;
    const tokens = nome.split(' ').filter(Boolean);
    if (tokens.length > 1 && tokens.every((token) => valor.includes(token))) {
      return servico;
    }
  }
  return null;
}

function identificarServicoEscolhido(texto, servicos) {
  const valor = normalizarBusca(texto);
  const matchNumero = valor.match(/^(\d+)$/) || valor.match(/opcao (\d+)/);
  if (matchNumero) {
    const indice = parseInt(matchNumero[1], 10) - 1;
    if (servicos[indice]) return servicos[indice];
  }
  return identificarServicoMencionado(texto, servicos);
}

function identificarProfissionalEscolhido(texto, profissionais) {
  const lista = Array.isArray(profissionais) ? profissionais.filter(Boolean) : [];
  const valor = normalizarBusca(texto);
  const matchNumero = valor.match(/\b(?:opcao\s*)?(\d+)\b/);
  if (matchNumero) {
    const indice = parseInt(matchNumero[1], 10) - 1;
    if (indice >= 0 && indice < lista.length) return lista[indice];
  }
  const nome = normalizarBusca(texto);
  if (!nome) return null;
  return lista.find((profissional) => {
    const nomeProfissional = normalizarBusca(profissional?.nome || '');
    if (!nomeProfissional) return false;
    if (nome.includes(nomeProfissional)) return true;
    const tokens = nomeProfissional.split(' ').filter(Boolean);
    return tokens.length > 1 && tokens.every((token) => nome.includes(token));
  }) || null;
}

function formatarProfissionalLista(profissional) {
  const nome = normalizarTexto(profissional?.nome || '');
  const especialidade = normalizarTexto(profissional?.especialidade || '');
  if (!nome) return '';
  if (normalizarBusca(nome) === 'sem preferencia' || normalizarBusca(nome) === 'atendimento principal') {
    return nome;
  }
  return especialidade ? `${nome} (${especialidade})` : nome;
}

function ehCorrecaoData(texto) {
  const valor = normalizarBusca(texto);
  if (!valor) return false;
  return [
    'nao e',
    'não e',
    'nao eh',
    'não eh',
    'errado',
    'essa nao',
    'essa não',
    'data errada',
    'dia errado',
    'isso esta errado',
    'isso está errado',
    'esse nao',
    'esse não',
    'nao é esse dia',
    'não é esse dia',
    'não é essa data',
    'nao é essa data',
  ].some((trecho) => valor.includes(normalizarBusca(trecho)));
}

function interpretarHorarioNatural(texto, disponiveis) {
  const valor = normalizarBuscaData(texto);
  const horarios = Array.isArray(disponiveis)
    ? disponiveis.map((horario) => normalizarHorarioHHmm(horario)).filter(Boolean)
    : [];
  if (!horarios.length) return null;

  const temPeriodoTarde = /\b(pm|da tarde|de tarde|a tarde|tarde|à tarde|noite|da noite|de noite|a noite)\b/.test(valor);
  const temPeriodoManha = /\b(am|da manha|de manha|a manha|manha|matutino)\b/.test(valor);

  const ajustarHorario = (horaTexto, minutoTexto = '00', periodoTexto = '') => {
    let hora = Number(horaTexto);
    let minuto = Number(minutoTexto);
    if (Number.isNaN(hora) || Number.isNaN(minuto)) return null;

    const periodo = normalizarBusca(periodoTexto);
    const aplicaTarde = periodo === 'pm' || (!periodo && temPeriodoTarde);
    const aplicaManha = periodo === 'am' || (!periodo && temPeriodoManha);

    if (aplicaTarde && hora < 12) hora += 12;
    if (aplicaManha && hora === 12) hora = 0;

    if (hora < 0 || hora > 23 || minuto < 0 || minuto > 59) return null;

    const candidato = `${String(hora).padStart(2, '0')}:${String(minuto).padStart(2, '0')}`;
    return horarios.includes(candidato) ? candidato : null;
  };

  let matchHora = valor.match(/\b(\d{1,2})\s*h\s*(\d{1,2})?\s*(am|pm)?\b/);
  if (matchHora) {
    const candidato = ajustarHorario(matchHora[1], matchHora[2] || '00', matchHora[3] || '');
    if (candidato) return candidato;
  }

  matchHora = valor.match(/\b(\d{1,2})\s*(?:[:\s])\s*(\d{1,2})\s*(am|pm)?\b/);
  if (matchHora) {
    const candidato = ajustarHorario(matchHora[1], matchHora[2], matchHora[3] || '');
    if (candidato) return candidato;
  }

  matchHora = valor.match(/\b(\d{1,2})\s*(am|pm)?\b/);
  if (matchHora) {
    const candidato = ajustarHorario(matchHora[1], '00', matchHora[2] || '');
    if (candidato) return candidato;
  }

  if (/\bmeio\s*dia\b/.test(valor)) {
    const candidato = horarios.includes('12:00') ? '12:00' : null;
    if (candidato) return candidato;
  }

  if (/\bmeia\s*noite\b/.test(valor)) {
    const candidato = horarios.includes('00:00') ? '00:00' : null;
    if (candidato) return candidato;
  }

  if (/\bprimeiro(a)?\b/.test(valor)) return horarios[0] || null;
  if (/\bultimo(a)?\b|\bultimo\b/.test(valor)) return horarios[horarios.length - 1] || null;
  return null;
}

function isHoje(dataYYYYMMDD) {
  const hoje = new Date();
  const yyyy = hoje.getFullYear();
  const mm = String(hoje.getMonth() + 1).padStart(2, '0');
  const dd = String(hoje.getDate()).padStart(2, '0');

  return dataYYYYMMDD === `${yyyy}-${mm}-${dd}`;
}

function ehHoje(texto) {
  const valor = normalizarBusca(texto);
  if (!valor) return false;
  return [
    'hoje',
    'hj',
    'hje',
    'today',
    'pra hoje',
    'para hoje',
    'quero hoje',
    'pode ser hoje',
    'hoje mesmo',
    'agr',
    'agora',
  ].some((termo) => valor.includes(normalizarBusca(termo)));
}

function dataHojeYYYYMMDD(fusoHorario = 'America/Sao_Paulo', base = new Date()) {
  const data = obterDataAgoraNoFuso(fusoHorario, base);
  return normalizarParaISO(data);
}

function normalizarHorarioHHmm(horario) {
  const valor = String(horario || '').trim();
  const match = valor.match(/^(\d{1,2})(?:\s*[:h]\s*|\s+)?(\d{1,2})?$/i);
  if (!match) return null;

  const horas = Number(match[1]);
  const minutos = Number(match[2] || '0');
  if (Number.isNaN(horas) || Number.isNaN(minutos)) return null;
  if (horas < 0 || horas > 23 || minutos < 0 || minutos > 59) return null;

  return `${String(horas).padStart(2, '0')}:${String(minutos).padStart(2, '0')}`;
}

function horarioParaMinutos(horarioHHmm) {
  const normalizado = normalizarHorarioHHmm(horarioHHmm);
  if (!normalizado) return null;

  const [horas, minutos] = normalizado.split(':').map(Number);
  if (Number.isNaN(horas) || Number.isNaN(minutos)) return null;

  return horas * 60 + minutos;
}

function filtrarHorariosFuturos(dataYYYYMMDD, horarios) {
  if (!Array.isArray(horarios)) return [];

  const vistos = new Set();

  return horarios
    .map((horario) => normalizarHorarioHHmm(horario))
    .filter(Boolean)
    .filter((horario) => {
      if (vistos.has(horario)) return false;
      vistos.add(horario);

      if (!isHoje(dataYYYYMMDD)) return true;

      const minutosHorario = horarioParaMinutos(horario);
      if (minutosHorario === null) return false;

      const agora = new Date();
      const minutosAgora = agora.getHours() * 60 + agora.getMinutes();
      const limiteMinimo = minutosAgora + 5;
      return minutosHorario > limiteMinimo;
    });
}

function horarioJaPassou(dataYYYYMMDD, horarioHHmm) {
  if (!isHoje(dataYYYYMMDD)) return false;

  const minutosHorario = horarioParaMinutos(horarioHHmm);
  if (minutosHorario === null) return false;

  const agora = new Date();
  const minutosAgora = agora.getHours() * 60 + agora.getMinutes();
  return minutosHorario <= (minutosAgora + 5);
}

function montarMensagemHorariosDisponiveis(dataYYYYMMDD, horarios) {
  const horariosFuturos = filtrarHorariosFuturos(dataYYYYMMDD, horarios);
  if (!horariosFuturos.length) return null;

  return `Tenho esses horários disponíveis para ${new Date(`${dataYYYYMMDD}T12:00:00`).toLocaleDateString('pt-BR')}:\n${horariosFuturos.join('\n')}\n\nQual horário você prefere?`;
}

function nomeDiaSemana(nome) {
  const mapa = {
    domingo: 0,
    segunda: 1,
    terca: 2,
    'terça': 2,
    quarta: 3,
    quinta: 4,
    sexta: 5,
    sabado: 6,
    'sábado': 6,
  };
  return mapa[nome];
}

function numeroMesPorExtenso(nomeMes) {
  const mapa = {
    janeiro: 1,
    fevereiro: 2,
    marco: 3,
    abril: 4,
    maio: 5,
    junho: 6,
    julho: 7,
    agosto: 8,
    setembro: 9,
    outubro: 10,
    novembro: 11,
    dezembro: 12,
  };
  return mapa[normalizarBusca(nomeMes)];
}

function normalizarParaISO(data) {
  const ano = data.getUTCFullYear();
  const mes = String(data.getUTCMonth() + 1).padStart(2, '0');
  const dia = String(data.getUTCDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

function obterDataAgoraNoFuso(fusoHorario = 'America/Sao_Paulo', base = new Date()) {
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: fusoHorario,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(base);
  const mapa = Object.fromEntries(partes.map((parte) => [parte.type, parte.value]));
  const ano = Number(mapa.year);
  const mes = Number(mapa.month);
  const dia = Number(mapa.day);
  return new Date(Date.UTC(ano, mes - 1, dia, 12, 0, 0));
}

function obterIndiceDiaSemanaNoFuso(data = new Date(), fusoHorario = 'America/Sao_Paulo') {
  const weekday = new Intl.DateTimeFormat('en-US', {
    timeZone: fusoHorario,
    weekday: 'short',
  }).format(data).toLowerCase();
  const mapa = {
    sun: 0,
    mon: 1,
    tue: 2,
    wed: 3,
    thu: 4,
    fri: 5,
    sat: 6,
  };
  return mapa[weekday] ?? data.getDay();
}

function ehDataReal(ano, mes, dia) {
  const data = new Date(Date.UTC(ano, mes - 1, dia, 12, 0, 0));
  return data.getUTCFullYear() === ano && data.getUTCMonth() === mes - 1 && data.getUTCDate() === dia;
}

function interpretarDataNatural(texto, agora = new Date()) {
  const valor = normalizarBuscaData(texto);
  const hoje = obterDataAgoraNoFuso('America/Sao_Paulo', agora);
  if (!valor) return null;

  if (ehHoje(texto) || /^(hoje|hj|hje|today|agora)$/.test(valor)) {
    return dataHojeYYYYMMDD('America/Sao_Paulo', agora);
  }

  if (valor.includes('depois de amanha') || valor.includes('depois de amanhã')) {
    const data = new Date(hoje);
    data.setDate(data.getDate() + 2);
    return normalizarParaISO(data);
  }

  if (valor.includes('amanha') || valor.includes('amanhã')) {
    const data = new Date(hoje);
    data.setDate(data.getDate() + 1);
    return normalizarParaISO(data);
  }

  const iso = valor.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (iso) {
    const ano = Number(iso[1]);
    const mes = Number(iso[2]);
    const dia = Number(iso[3]);
    if (!ehDataReal(ano, mes, dia)) return null;
    return `${iso[1]}-${iso[2]}-${iso[3]}`;
  }

  const brAno = valor.match(/^(\d{1,2})[\/-](\d{1,2})[\/-](\d{4})$/);
  if (brAno) {
    const dia = Number(brAno[1]);
    const mes = Number(brAno[2]);
    const ano = Number(brAno[3]);
    if (!ehDataReal(ano, mes, dia)) return null;
    return `${String(ano).padStart(4, '0')}-${String(mes).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
  }

  const br = valor.match(/^(\d{1,2})[\/-](\d{1,2})$/);
  if (br) {
    const dia = Number(br[1]);
    const mes = Number(br[2]);
    let ano = hoje.getFullYear();
    if (!ehDataReal(ano, mes, dia)) return null;
    let data = new Date(Date.UTC(ano, mes - 1, dia, 12, 0, 0));
    if (data < hoje) {
      ano += 1;
      if (!ehDataReal(ano, mes, dia)) return null;
      data = new Date(Date.UTC(ano, mes - 1, dia, 12, 0, 0));
    }
    return normalizarParaISO(data);
  }

  const mesExtenso = valor.match(/^(?:dia|no dia|para dia)?\s*(\d{1,2})\s*(?:de|da|do)?\s*(janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)$/);
  if (mesExtenso) {
    const dia = Number(mesExtenso[1]);
    const mes = numeroMesPorExtenso(mesExtenso[2]);
    let ano = hoje.getFullYear();
    if (!mes || !ehDataReal(ano, mes, dia)) return null;
    let data = new Date(Date.UTC(ano, mes - 1, dia, 12, 0, 0));
    if (data < hoje) {
      ano += 1;
      if (!ehDataReal(ano, mes, dia)) return null;
      data = new Date(Date.UTC(ano, mes - 1, dia, 12, 0, 0));
    }
    return normalizarParaISO(data);
  }

  const diaMes = valor.match(/^(?:dia|no dia|para dia)\s*(\d{1,2})$/);
  if (diaMes) {
    const dia = Number(diaMes[1]);
    if (dia < 1 || dia > 31) return null;
    let ano = hoje.getFullYear();
    let mes = hoje.getMonth() + 1;

    while (mes <= 12 && !ehDataReal(ano, mes, dia)) {
      mes += 1;
    }
    if (mes > 12) {
      ano += 1;
      mes = 1;
      while (mes <= 12 && !ehDataReal(ano, mes, dia)) {
        mes += 1;
      }
    }
    if (mes > 12 || !ehDataReal(ano, mes, dia)) return null;

    let data = new Date(Date.UTC(ano, mes - 1, dia, 12, 0, 0));
    if (data < hoje) {
      mes += 1;
      if (mes > 12) {
        ano += 1;
        mes = 1;
      }
      while (mes <= 12 && !ehDataReal(ano, mes, dia)) {
        mes += 1;
      }
      if (mes > 12 || !ehDataReal(ano, mes, dia)) return null;
      data = new Date(Date.UTC(ano, mes - 1, dia, 12, 0, 0));
    }
    return normalizarParaISO(data);
  }

  const diasSemana = [
    { nomes: ['domingo'], index: 0 },
    { nomes: ['segunda'], index: 1 },
    { nomes: ['terca', 'terça'], index: 2 },
    { nomes: ['quarta'], index: 3 },
    { nomes: ['quinta'], index: 4 },
    { nomes: ['sexta'], index: 5 },
    { nomes: ['sabado', 'sábado'], index: 6 },
  ];
  const diaSemana = diasSemana.find((item) => item.nomes.some((nome) => valor.includes(nome)));
  if (diaSemana) {
    const alvo = diaSemana.index;
    const hojeIndice = obterIndiceDiaSemanaNoFuso(hoje, 'America/Sao_Paulo');
    let diasAte = (alvo - hojeIndice + 7) % 7;
    if (diasAte === 0) {
      diasAte = 7;
    }
    const data = new Date(hoje);
    data.setDate(data.getDate() + diasAte);
    return normalizarParaISO(data);
  }

  try {
    const chrono = require('chrono-node');
    const parseFn = chrono?.pt?.parseDate || chrono.parseDate;
    if (typeof parseFn === 'function') {
      const resultado = chrono?.pt?.parseDate
        ? parseFn(texto, agora, { forwardDate: true })
        : parseFn(texto, agora, { forwardDate: true });
      if (resultado) return resultado.toISOString().slice(0, 10);
    }
  } catch {
    // fallback silencioso
  }

  return null;
}

function parseDataCliente(texto, agora = new Date()) {
  return interpretarDataNatural(texto, agora);
}

async function buscarServicosEmpresa(baseUrl, token, empresaId) {
  const response = await axios.get(`${baseUrl}/api/internal/whatsapp/servicos/${empresaId}`, {
    headers: token ? { 'X-Internal-Token': token } : {},
    timeout: 15000,
  });
  return Array.isArray(response.data) ? response.data : [];
}

async function consultarDisponibilidade(baseUrl, token, empresaId, servicoId, data, profissionalId = null) {
  const response = await axios.get(`${baseUrl}/api/internal/whatsapp/disponibilidade/${empresaId}`, {
    headers: token ? { 'X-Internal-Token': token } : {},
    params: { servicoId, data, profissionalId: profissionalId || undefined },
    timeout: 20000,
  });
  return response.data || {};
}

async function criarAgendamento(baseUrl, token, payload) {
  const response = await axios.post(`${baseUrl}/api/internal/whatsapp/agendar`, payload, {
    headers: token ? { 'X-Internal-Token': token } : {},
    timeout: 20000,
  });
  return response.data || {};
}

async function obterHorariosFuturosDisponiveis(baseUrl, token, empresaId, servicoId, data, profissionalId = null) {
  const disponibilidade = await consultarDisponibilidade(baseUrl, token, empresaId, servicoId, data, profissionalId);
  return filtrarHorariosFuturos(data, Array.isArray(disponibilidade.horarios) ? disponibilidade.horarios : []);
}

async function conduzirFluxoAgendamento({ estado, texto, contexto, empresaId, telefoneCliente, nomeCliente, remoteJid, backendUrl, backendToken, groqApiKey }) {
  const fluxo = garantirFluxo(estado);
  const linkAgendamento = normalizarTexto(contexto?.linkAgendamento || '');
  if (!Array.isArray(fluxo.profissionaisDisponiveis) || !fluxo.profissionaisDisponiveis.length) {
    fluxo.profissionaisDisponiveis = Array.isArray(contexto?.profissionais) ? contexto.profissionais : [];
  }
  const nomeAtual = normalizarTexto(fluxo.clienteNome || nomeCliente);

  if (fluxo.etapa === 'ESCOLHENDO_MODO') {
    if (prefereAgendarPorWhatsapp(texto)) {
      atualizarEtapaFluxo(fluxo, 'AGUARDANDO_NOME', { empresaId, remoteJid });
      estado.etapa = fluxo.etapa;
      fluxo.ativo = true;
      fluxo.modoSelecionado = 'WHATSAPP';
      return 'Perfeito! Para começar, qual é o seu nome?';
    }
    if (prefereLink(texto)) {
      resetarFluxoAgendamento(estado);
      return linkAgendamento
        ? `Perfeito! É só acessar:\n${linkAgendamento}\n\nPor lá você escolhe o serviço, o dia e o horário disponível.`
        : 'Ainda não encontrei o link de agendamento configurado para esta empresa. Vou encaminhar para um atendente te ajudar.';
    }
    const classificacao = await classificarPreferenciaComIA(texto, groqApiKey);
    if (classificacao === 'WHATSAPP') {
      atualizarEtapaFluxo(fluxo, 'AGUARDANDO_NOME', { empresaId, remoteJid });
      estado.etapa = fluxo.etapa;
      fluxo.ativo = true;
      fluxo.modoSelecionado = 'WHATSAPP';
      return 'Perfeito! Para começar, qual é o seu nome?';
    }
    if (classificacao === 'LINK') {
      resetarFluxoAgendamento(estado);
      return linkAgendamento
        ? `Perfeito! É só acessar:\n${linkAgendamento}\n\nPor lá você escolhe o serviço, o dia e o horário disponível.`
        : 'Ainda não encontrei o link de agendamento configurado para esta empresa. Vou encaminhar para um atendente te ajudar.';
    }
    return mensagemInicioAgendamento(linkAgendamento);
  }

  if (fluxo.etapa === 'AGUARDANDO_NOME') {
    const clienteDigitado = nomeClienteValido(texto);
    if (!clienteDigitado) {
      return 'Antes de confirmar, me diga seu nome, por favor.';
    }
    fluxo.clienteNome = clienteDigitado;
    atualizarEtapaFluxo(fluxo, 'AGUARDANDO_SERVICO', { empresaId, remoteJid });
    estado.etapa = fluxo.etapa;
    fluxo.ativo = true;
    const servicos = await buscarServicosEmpresa(backendUrl, backendToken, empresaId);
    if (!servicos.length) {
      resetarFluxoAgendamento(estado);
      return linkAgendamento
        ? `No momento não encontrei serviços cadastrados. Você pode fazer seu agendamento pelo link:\n${linkAgendamento}`
        : 'No momento não encontrei serviços cadastrados. Vou encaminhar para um atendente te ajudar.';
    }
    fluxo.servicosDisponiveis = servicos;
    return montarMensagemListaServicos(servicos);
  }

  if (fluxo.etapa === 'AGUARDANDO_SERVICO' || fluxo.etapa === 'ESCOLHENDO_SERVICO') {
    const servico = identificarServicoEscolhido(texto, fluxo.servicosDisponiveis);
    if (!servico) {
      return `Não encontrei esse serviço. Escolha uma das opções:\n${montarMensagemListaServicos(fluxo.servicosDisponiveis)}`;
    }
    fluxo.servicoSelecionado = servico;
    const profissionaisAtivos = Array.isArray(fluxo.profissionaisDisponiveis) ? fluxo.profissionaisDisponiveis : [];
    if (profissionaisAtivos.length > 1) {
      atualizarEtapaFluxo(fluxo, 'AGUARDANDO_PROFISSIONAL', { empresaId, remoteJid });
      estado.etapa = fluxo.etapa;
      fluxo.ativo = true;
      return montarMensagemListaProfissionais(profissionaisAtivos);
    }
    fluxo.profissionalSelecionado = profissionaisAtivos[0] || null;
    atualizarEtapaFluxo(fluxo, 'AGUARDANDO_DATA', { empresaId, remoteJid });
    estado.etapa = fluxo.etapa;
    fluxo.ativo = true;
    return 'Ótimo! Qual dia você prefere?';
  }

  if (fluxo.etapa === 'AGUARDANDO_PROFISSIONAL' || fluxo.etapa === 'ESCOLHENDO_PROFISSIONAL') {
    const profissional = identificarProfissionalEscolhido(texto, fluxo.profissionaisDisponiveis);
    if (!profissional) {
      return `Não encontrei esse profissional. Escolha uma das opções:\n${montarMensagemListaProfissionais(fluxo.profissionaisDisponiveis)}`;
    }
    fluxo.profissionalSelecionado = profissional;
    atualizarEtapaFluxo(fluxo, 'AGUARDANDO_DATA', { empresaId, remoteJid });
    estado.etapa = fluxo.etapa;
    fluxo.ativo = true;
    return 'Ótimo! Qual dia você prefere?';
  }

  if (fluxo.etapa === 'AGUARDANDO_DATA' || fluxo.etapa === 'ESCOLHENDO_DATA') {
    if (ehCorrecaoData(texto)) {
      fluxo.dataSelecionada = null;
      fluxo.horarioSelecionado = null;
      fluxo.horariosDisponiveis = [];
      atualizarEtapaFluxo(fluxo, 'AGUARDANDO_DATA', { empresaId, remoteJid });
      estado.etapa = fluxo.etapa;
      fluxo.ativo = true;
      return 'Desculpa pelo erro! Qual o dia certo que você prefere?';
    }
    console.log('[bot-data] tentando interpretar data', {
      empresaId,
      remoteJid,
      texto,
    });
    const dataIso = parseDataCliente(texto);
    if (!dataIso) {
      console.log('[bot-data] data nao interpretada', {
        empresaId,
        remoteJid,
        texto,
      });
      return 'Não entendi a data. Pode me dizer o dia que prefere? Ex: "amanhã", "sexta", "dia 10".';
    }
    let horariosDisponiveis;
    try {
      horariosDisponiveis = await obterHorariosFuturosDisponiveis(backendUrl, backendToken, empresaId, fluxo.servicoSelecionado.id, dataIso, fluxo.profissionalSelecionado?.id || null);
    } catch (error) {
      console.warn('[Scheduling] falha ao buscar disponibilidade', {
        empresaId,
        remoteJid,
        dataSelecionada: dataIso,
        servicoId: fluxo.servicoSelecionado?.id,
        profissionalId: fluxo.profissionalSelecionado?.id || null,
        status: error.response?.status,
        respostaBackend: error.response?.data,
        codigoErro: error.code,
        detalhe: error.message,
      });
      return 'Entendi a data, mas tive uma instabilidade para buscar os horários agora. Pode tentar novamente em alguns segundos?';
    }
    if (!horariosDisponiveis.length) {
      console.log('[bot-data] data invalida ou passada', {
        empresaId,
        remoteJid,
        texto,
        dataSelecionada: dataIso,
      });
      fluxo.dataSelecionada = null;
      fluxo.horariosDisponiveis = [];
      return isHoje(dataIso)
        ? 'Para hoje não encontrei mais horários disponíveis. Quer tentar amanhã ou outro dia?'
        : `Não encontrei horários disponíveis para ${new Date(`${dataIso}T12:00:00`).toLocaleDateString('pt-BR')}. Quer tentar outro dia?`;
    }
    fluxo.dataSelecionada = dataIso;
    fluxo.horariosDisponiveis = horariosDisponiveis;
    atualizarEtapaFluxo(fluxo, 'AGUARDANDO_HORARIO', { empresaId, remoteJid });
    estado.etapa = fluxo.etapa;
    fluxo.ativo = true;
    return montarMensagemHorariosDisponiveis(dataIso, horariosDisponiveis);
  }

  if (fluxo.etapa === 'AGUARDANDO_HORARIO' || fluxo.etapa === 'ESCOLHENDO_HORARIO') {
    if (ehCorrecaoData(texto)) {
      fluxo.dataSelecionada = null;
      fluxo.horarioSelecionado = null;
      fluxo.horariosDisponiveis = [];
      atualizarEtapaFluxo(fluxo, 'AGUARDANDO_DATA', { empresaId, remoteJid });
      estado.etapa = fluxo.etapa;
      fluxo.ativo = true;
      return 'Desculpa pelo erro! Qual o dia certo que você prefere?';
    }
    const horario = interpretarHorarioNatural(texto, fluxo.horariosDisponiveis);
    if (!horario) {
      const horariosFuturos = filtrarHorariosFuturos(fluxo.dataSelecionada, fluxo.horariosDisponiveis);
      if (!horariosFuturos.length) {
        fluxo.horarioSelecionado = null;
        fluxo.dataSelecionada = null;
        fluxo.horariosDisponiveis = [];
        atualizarEtapaFluxo(fluxo, 'AGUARDANDO_DATA', { empresaId, remoteJid });
        estado.etapa = fluxo.etapa;
        return 'Não encontrei horários livres para hoje a partir de agora. Quer tentar outro dia?';
      }
      fluxo.horariosDisponiveis = horariosFuturos;
      return `Não encontrei esse horário. Escolha um dos disponíveis:\n${horariosFuturos.join('\n')}`;
    }
    if (horarioJaPassou(fluxo.dataSelecionada, horario)) {
      const horariosFuturos = filtrarHorariosFuturos(fluxo.dataSelecionada, fluxo.horariosDisponiveis);
      fluxo.horarioSelecionado = null;
      fluxo.horariosDisponiveis = horariosFuturos;
      if (!horariosFuturos.length) {
        fluxo.dataSelecionada = null;
        atualizarEtapaFluxo(fluxo, 'AGUARDANDO_DATA', { empresaId, remoteJid });
        estado.etapa = fluxo.etapa;
        return 'Esse horário já passou. Para hoje não encontrei mais horários disponíveis. Quer tentar amanhã ou outro dia?';
      }
      atualizarEtapaFluxo(fluxo, 'AGUARDANDO_HORARIO', { empresaId, remoteJid });
      estado.etapa = fluxo.etapa;
      fluxo.ativo = true;
      return `Esse horário já passou. Vou te mostrar os horários disponíveis a partir de agora.\n${horariosFuturos.join('\n')}\n\nQual horário você prefere?`;
    }
    fluxo.horarioSelecionado = horario;
    atualizarEtapaFluxo(fluxo, 'AGUARDANDO_CONFIRMACAO', { empresaId, remoteJid });
    estado.etapa = fluxo.etapa;
    fluxo.ativo = true;
    const confirmacao = [
      'Confirmando seu agendamento:',
      '',
      `Serviço: ${fluxo.servicoSelecionado.nome}`,
      fluxo.profissionalSelecionado?.nome ? `Profissional: ${formatarProfissionalLista(fluxo.profissionalSelecionado)}` : null,
      `Data: ${new Date(`${fluxo.dataSelecionada}T12:00:00`).toLocaleDateString('pt-BR')}`,
      `Horário: ${horario}`,
      '',
      'Posso confirmar?',
    ];
    return confirmacao.filter(Boolean).join('\n');
  }

  if (fluxo.etapa === 'AGUARDANDO_CONFIRMACAO' || fluxo.etapa === 'CONFIRMANDO') {
    if (respostaPositiva(texto)) {
      if (!nomeAtual) {
        atualizarEtapaFluxo(fluxo, 'AGUARDANDO_NOME', { empresaId, remoteJid });
        estado.etapa = fluxo.etapa;
        fluxo.ativo = true;
        return 'Antes de confirmar, me diga seu nome, por favor.';
      }
      if (horarioJaPassou(fluxo.dataSelecionada, fluxo.horarioSelecionado)) {
        const horariosFuturos = filtrarHorariosFuturos(fluxo.dataSelecionada, fluxo.horariosDisponiveis);
        fluxo.horarioSelecionado = null;
        fluxo.horariosDisponiveis = horariosFuturos;
        if (!horariosFuturos.length) {
          fluxo.dataSelecionada = null;
          atualizarEtapaFluxo(fluxo, 'AGUARDANDO_DATA', { empresaId, remoteJid });
          estado.etapa = fluxo.etapa;
          return 'Esse horário já passou. Não encontrei horários livres para hoje a partir de agora. Quer tentar outro dia?';
        }
        atualizarEtapaFluxo(fluxo, 'AGUARDANDO_HORARIO', { empresaId, remoteJid });
        estado.etapa = fluxo.etapa;
        fluxo.ativo = true;
        return `Esse horário já passou. Vou te mostrar os horários disponíveis a partir de agora.\n${horariosFuturos.join('\n')}\n\nQual horário você prefere?`;
      }
      try {
        const horarioConfirmado = fluxo.horarioSelecionado;
        const dataConfirmada = fluxo.dataSelecionada;
        const payload = {
          empresaId,
          telefoneCliente,
          nomeCliente: nomeAtual,
          remoteJid: remoteJid || null,
          servicoId: fluxo.servicoSelecionado.id,
          profissionalId: fluxo.profissionalSelecionado?.id || null,
          data: dataConfirmada,
          horario: horarioConfirmado,
          origem: 'WHATSAPP',
        };
        console.log('[Scheduling] confirmando no backend', {
          empresaId,
          remoteJid: remoteJid || null,
          clienteNome: nomeAtual,
          servicoId: fluxo.servicoSelecionado.id,
          profissionalId: fluxo.profissionalSelecionado?.id || null,
          data: dataConfirmada,
          horario: horarioConfirmado,
        });
        console.log('[Scheduling] payload criação', payload);
        const resultado = await criarAgendamento(backendUrl, backendToken, payload);
        if (!resultado?.protocolo) {
          console.error('[Scheduling] protocolo ausente no retorno do backend', {
            empresaId,
            remoteJid: remoteJid || null,
            agendamentoId: resultado?.agendamentoId || null,
          });
        } else {
          console.log('[Scheduling] agendamento criado', {
            empresaId,
            remoteJid: remoteJid || null,
            agendamentoId: resultado?.agendamentoId,
            protocolo: resultado?.protocolo,
          });
        }
        estado.finalizouFluxoAgendamento = 'agendamento_confirmado';
        resetarFluxoAgendamento(estado);
        return montarMensagemSucessoAgendamento({
          nome: nomeAtual,
          servico: fluxo.servicoSelecionado.nome,
          data: dataConfirmada,
          horario: horarioConfirmado,
          protocolo: resultado?.protocolo,
          profissional: fluxo.profissionalSelecionado?.nome || null,
        });
      } catch (error) {
        const codigoErro = normalizarBusca(error.response?.data?.erro || '');
        console.error('[Scheduling] erro ao criar agendamento', {
          empresaId,
          remoteJid: remoteJid || null,
          status: error.response?.status || null,
          data: error.response?.data || null,
          message: error.message,
          stack: error.stack,
        });

        const camposValidacao = error.response?.data?.campos || error.response?.data?.fieldErrors || null;
        const camposFaltantes = camposValidacao && typeof camposValidacao === 'object'
          ? Object.keys(camposValidacao)
          : [];
        const campoPrincipal = camposFaltantes.includes('horario')
          ? 'horario'
          : (camposFaltantes.includes('data') ? 'data' : camposFaltantes[0] || null);
        const erroHorarioIndisponivel = error.response?.status === 409
          || codigoErro.includes('horario')
          || codigoErro.includes('conflito');

        if (erroHorarioIndisponivel) {
          const horariosFuturos = await obterHorariosFuturosDisponiveis(backendUrl, backendToken, empresaId, fluxo.servicoSelecionado.id, fluxo.dataSelecionada, fluxo.profissionalSelecionado?.id || null);
          fluxo.horarioSelecionado = null;
          fluxo.horariosDisponiveis = horariosFuturos;
          if (!horariosFuturos.length) {
            fluxo.dataSelecionada = null;
            atualizarEtapaFluxo(fluxo, 'AGUARDANDO_DATA', { empresaId, remoteJid });
            estado.etapa = fluxo.etapa;
            return 'Esse horário não está mais disponível. Não encontrei horários livres para hoje a partir de agora. Quer tentar outro dia?';
          }
          atualizarEtapaFluxo(fluxo, 'AGUARDANDO_HORARIO', { empresaId, remoteJid });
          estado.etapa = fluxo.etapa;
          fluxo.ativo = true;
          return `Esse horário não está mais disponível. Vou te mostrar os horários livres novamente.\n${horariosFuturos.join('\n')}`;
        }

        if (codigoErro.includes('validacao') || campoPrincipal) {
          console.warn('[Scheduling] validacao na finalizacao', {
            empresaId,
            remoteJid: remoteJid || null,
            campos: camposFaltantes,
            campoPrincipal,
            detalhe: error.response?.data || null,
          });

          if (campoPrincipal === 'horario') {
            const horariosFuturos = filtrarHorariosFuturos(fluxo.dataSelecionada, fluxo.horariosDisponiveis);
            fluxo.horarioSelecionado = null;
            fluxo.horariosDisponiveis = horariosFuturos;
            if (!horariosFuturos.length) {
              fluxo.dataSelecionada = null;
              atualizarEtapaFluxo(fluxo, 'AGUARDANDO_DATA', { empresaId, remoteJid });
              estado.etapa = fluxo.etapa;
              return 'Não encontrei horários livres para hoje a partir de agora. Quer tentar outro dia?';
            }
            atualizarEtapaFluxo(fluxo, 'AGUARDANDO_HORARIO', { empresaId, remoteJid });
            estado.etapa = fluxo.etapa;
            fluxo.ativo = true;
            return `Esse horário não está mais disponível. Vou te mostrar os horários livres novamente.\n${horariosFuturos.join('\n')}`;
          }

          if (campoPrincipal === 'data') {
            fluxo.dataSelecionada = null;
            fluxo.horarioSelecionado = null;
            atualizarEtapaFluxo(fluxo, 'AGUARDANDO_DATA', { empresaId, remoteJid });
            estado.etapa = fluxo.etapa;
            return 'Preciso que você me diga a data novamente.';
          }

          if (campoPrincipal === 'servicoId' || campoPrincipal === 'servico') {
            fluxo.servicoSelecionado = null;
            fluxo.dataSelecionada = null;
            fluxo.horarioSelecionado = null;
            atualizarEtapaFluxo(fluxo, 'AGUARDANDO_SERVICO', { empresaId, remoteJid });
            estado.etapa = fluxo.etapa;
            return 'Preciso que você escolha o serviço novamente.';
          }

          return 'Tive um problema para confirmar o agendamento. Vou te pedir os dados novamente.';
        }
        resetarFluxoAgendamento(estado);
        return linkAgendamento
          ? `Tive um problema para confirmar o agendamento. Você pode tentar novamente ou fazer pelo link:\n${linkAgendamento}`
          : 'Tive um problema para confirmar o agendamento. Vou encaminhar para um atendente te ajudar.';
      }
    }
    if (respostaNegativa(texto)) {
      estado.finalizouFluxoAgendamento = 'cliente_respondeu_nao';
      resetarFluxoAgendamento(estado);
      return 'Sem problemas! Se quiser tentar de novo é só me chamar.';
    }
    return 'Posso confirmar o agendamento? Responda sim ou não.';
  }

  return null;
}

module.exports = { conduzirFluxoAgendamento: async () => null, fluxoExpirado: () => false, resetarFluxoAgendamento: () => ({}) };
