/*
╔════════════════════════════════════════╗
║  ⚠️  DESATIVADO - FUNCIONALIDADE WhatsApp ║
║  Código comentado. Remova comentários  ║
║  para reativar.                        ║
╚════════════════════════════════════════╝
*/
// const axios = require('axios');
// const Groq = require('groq-sdk');
// const {
//   conduzirFluxoAgendamento,
//   fluxoExpirado,
//   mensagemInicioAgendamento,
//   resetarFluxoAgendamento,
// } = require('./fluxoAgendamento');
// const {
//   conduzirFluxoCancelamento,
//   fluxoExpiradoCancelamento,
//   resetarFluxoCancelamento,
// } = require('./fluxoCancelamento');
// const {
//   conduzirFluxoReagendamento,
//   fluxoExpiradoReagendamento,
//   resetarFluxoReagendamento,
// } = require('./fluxoReagendamento');
// const {
//   conduzirFluxoConfirmacaoPagamentoDono,
// } = require('./fluxoConfirmacaoPagamentoDono');
// const history = new Map();
// const conversationState = new Map();
// const contextoEmpresaCache = new Map();
// const LIMPEZA_MS = 5 * 60 * 1000;
// const INATIVIDADE_MS = 60 * 60 * 1000;
// const TEMPO_EXPIRACAO_CONVERSA_MS = 60 * 60 * 1000;
// const CONVERSATION_TTL_MS = 24 * 60 * 60 * 1000;
// const CONTEXTO_CACHE_TTL_MS = Number(process.env.WHATSAPP_CONTEXT_CACHE_TTL_MS || 30000);
// const DEPLOY_VERSION = 'whatsapp-2-real';

// console.log(`[Bot] DEPLOY_VERSION=${DEPLOY_VERSION}`);

// function normalizarTexto(valor) {
//   return String(valor || '').trim();
// }

// function headersInternos(token) {
//   return token ? { 'X-Internal-Token': token } : {};
// }

// function backendBase(normalizado) {
//   const base = normalizado || process.env.BACKEND_URL || process.env.BACKEND_JAVA_URL || 'http://localhost:8080';
//   return String(base || '').replace(/\/+$/, '');
// }

// function normalizarContextoWpp(raw = {}) {
//   const agendamentoSlug =
//     raw.agendamentoSlug ||
//     raw.agendamento_slug ||
//     raw.slug ||
//     raw.slugPublico ||
//     '';

//   let linkAgendamento =
//     raw.linkAgendamento ||
//     raw.link_agendamento ||
//     raw.urlAgendamento ||
//     raw.linkPublico ||
//     '';

//   if (!linkAgendamento && agendamentoSlug) {
//     const base = (process.env.PUBLIC_BASE_URL || 'https://gendaz.site').replace(/\/+$/, '');
//     linkAgendamento = `${base}/agendar/${agendamentoSlug}`;
//   }

//   const nomeEmpresa =
//     raw.nomeEmpresa ||
//     raw.nome_empresa ||
//     raw.nomeFantasia ||
//     raw.nome ||
//     raw.razaoSocial ||
//     '';

//   const servicos = Array.isArray(raw.servicos) ? raw.servicos : [];
//   const profissionais = Array.isArray(raw.profissionais) ? raw.profissionais : [];
//   const horariosDisponiveis = Array.isArray(raw.horariosDisponiveis) ? raw.horariosDisponiveis : [];

//   return {
//     ...raw,
//     agendamentoSlug,
//     linkAgendamento,
//     nomeEmpresa,
//     servicos,
//     profissionais,
//     horariosDisponiveis,
//   };
// }

// function resolverLinkAgendamento(contexto) {
//   const normalizado = normalizarContextoWpp(contexto);
//   if (normalizado.linkAgendamento) return normalizado.linkAgendamento;
//   if (normalizado.agendamentoSlug) {
//     const base = (process.env.PUBLIC_BASE_URL || 'https://gendaz.site').replace(/\/+$/, '');
//     return `${base}/agendar/${normalizado.agendamentoSlug}`;
//   }
//   return '';
// }

// function detectarIntencaoWpp2(texto) {
//   const valor = normalizarBusca(texto);
//   if (!valor) return 'OUTRO';
//   if (/(cancelamento|cancelar|desmarcar|nao vou conseguir ir|não vou conseguir ir|nao posso ir|não posso ir|tirar meu horario|tirar meu horário|preciso cancelar|cancelar agendamento)/.test(valor)) return 'CANCELAMENTO';
//   if (/(reagendar|reagendamento|reagenda|remarcar|remarca|remarco|quero remarcar|quero reagendar|queria remarcar|queria reagendar|mudar meu horario|mudar horario|trocar horario|trocar meu horario|alterar horario|alterar meu horario|mudar minha consulta|trocar minha consulta|muda o horario|troca o horario)/.test(valor)) return 'REAGENDAMENTO';
//   if (/(outro horario|outro horário|ver outro horario|ver outro horário|tem outro|tem mais horario|tem mais horário|mudar horario|mudar horário|trocar horario|trocar horário)/.test(valor)) return 'HORARIOS';
//   if (/(quero marcar|quero agendar|quero fazer um agendamento|agendamento|agenda pra mim|quero um horario|quero um horário|tem horario|tem horário|tem vaga|fazer agendamento|marcar um corte|queria marcar)/.test(valor)) return 'AGENDAMENTO';
//   return 'OUTRO';
// }

// function primeiraFrase(valor) {
//   const texto = normalizarTexto(valor).replace(/\s+/g, ' ');
//   if (!texto) return '';
//   const partes = texto.split(/(?<=[.!?])\s+/);
//   return normalizarTexto(partes[0] || texto);
// }

// function nomeEmpresaSeguro(contexto) {
//   const nome = typeof contexto === 'string' ? normalizarTexto(contexto) : normalizarTexto(contexto?.nomeEmpresa);
//   if (!nome) {
//     console.warn('[AI-Response] nomeEmpresa ausente no contexto');
//     return '';
//   }
//   return nome;
// }

// function sanitizarResposta(resposta, contexto, fluxoAtivo = false) {
//   const nomeEmpresa = nomeEmpresaSeguro(contexto);
//   const linkAgendamento = normalizarTexto(contexto?.linkAgendamento || '');
//   let texto = normalizarTexto(resposta);
//   if (nomeEmpresa) {
//     texto = texto.replace(/\besta empresa\b/gi, nomeEmpresa);
//   }
//   if (nomeEmpresa && nomeEmpresa.toLowerCase() !== 'agendnew') {
//     texto = texto.replace(/\bAgendNew\b/g, nomeEmpresa);
//   }
//   if (fluxoAtivo) {
//     return texto;
//   }
//   if (linkAgendamento) {
//     texto = texto.replace(/\[link de agendamento\]/gi, linkAgendamento);
//     const respostaProibida = /(não encontrei|nao encontrei|não há horários|nao ha horarios|não ha horarios|não há dias|nao ha dias|dias livres|vou verificar|te retorno|em breve)/i.test(texto);
//     const assuntoAgendamento = /(hor[aá]rio|agenda|agendamento|marcar|consulta|dispon[ií]vel|vaga|dia)/i.test(texto);
//     if (respostaProibida && assuntoAgendamento) {
//       return `Claro! Para verificar os dias e horários disponíveis e fazer seu agendamento, acesse este link:\n${linkAgendamento}\n\nPor lá você escolhe o serviço, o dia e o horário disponível.`;
//     }
//   }
//   return texto;
// }

// function combinarTomComConteudo(tom, conteudo) {
//   const base = primeiraFrase(tom).replace(/\s+/g, ' ');
//   const corpo = normalizarTexto(conteudo).replace(/^\s*[-:]\s*/, '');
//   if (!base) return corpo;
//   if (!corpo) return base;
//   if (corpo.toLowerCase().startsWith(base.toLowerCase())) return corpo;
//   return `${base} ${corpo}`.trim();
// }

// function primeiroNome(valor) {
//   const texto = normalizarTexto(valor).replace(/\s+/g, ' ');
//   if (!texto) return '';
//   return texto.split(' ').filter(Boolean)[0] || '';
// }

// function saudacaoComNome(clienteNome, respostaBoasVindas) {
//   const nome = primeiroNome(clienteNome);
//   if (!nome) return respostaBoasVindas;
//   const saudacao = primeiraFrase(respostaBoasVindas)
//     .replace(/^ol[aá]!?[\s,]*/i, '')
//     .trim();
//   return `Olá ${nome}! ${saudacao || 'Como posso te ajudar hoje?'}`;
// }

// function montarDescricaoEmpresa(nomeEmpresa, servicos) {
//   const nome = normalizarTexto(nomeEmpresa);
//   const lista = Array.isArray(servicos)
//     ? servicos
//         .map((item) => normalizarTexto(item?.nome || item?.label || item?.servico || ''))
//         .filter(Boolean)
//         .slice(0, 3)
//     : [];
//   if (!lista.length) {
//     return nome ? `Atendimento da ${nome} pelo WhatsApp.` : '';
//   }
//   const complemento = lista.length > 1 ? ` com servicos como ${lista.join(', ')}` : ` com servico como ${lista[0]}`;
//   return nome ? `Atendimento da ${nome}${complemento}.` : '';
// }

// function chaveHistorico(tenantId, phoneCliente) {
//   return `${tenantId}:${phoneCliente}`;
// }

// function garantirHistorico(tenantId, phoneCliente) {
//   const chave = chaveHistorico(tenantId, phoneCliente);
//   if (!history.has(chave)) {
//     history.set(chave, { messages: [], lastActivity: Date.now() });
//   }
//   return history.get(chave);
// }

// function estadoChave(tenantId, remoteJid) {
//   return `${tenantId}:${remoteJid}`;
// }

// function conversaKey(tenantId, remoteJid) {
//   return estadoChave(tenantId, remoteJid);
// }

// function garantirEstadoConversa(tenantId, remoteJid) {
//   const chave = estadoChave(tenantId, remoteJid);
//   if (!conversationState.has(chave)) {
//     conversationState.set(chave, {
//       lastIntent: null,
//       lastService: null,
//       lastDate: null,
//       lastMessage: null,
//       rateLimit: {
//         timestamps: [],
//         bloqueadoAte: null,
//         ultimoAvisoEm: null,
//       },
//       updatedAt: Date.now(),
//       fluxoAgendamento: {
//         ativo: false,
//         tipoFluxo: 'AGENDAMENTO',
//         etapa: null,
//         modoSelecionado: null,
//         servicosDisponiveis: [],
//         servicoSelecionado: null,
//         dataSelecionada: null,
//         horariosDisponiveis: [],
//         horarioSelecionado: null,
//         clienteNome: null,
//         nomeCliente: null,
//         ultimaPergunta: null,
//       },
//       fluxoConfirmacaoPagamentoDono: {
//         ativo: false,
//         etapa: null,
//         agendamentoId: null,
//         protocolo: null,
//         clienteNome: null,
//         clienteTelefone: null,
//         servicoNome: null,
//         profissionalNome: null,
//         data: null,
//         horario: null,
//         enviadoEm: null,
//         segundoLembrete: false,
//       },
//     });
//   }
//   const estado = conversationState.get(chave);
//   estado.rateLimit = estado.rateLimit || {
//     timestamps: [],
//     bloqueadoAte: null,
//     ultimoAvisoEm: null,
//   };
//   if (!Array.isArray(estado.rateLimit.timestamps)) {
//     estado.rateLimit.timestamps = [];
//   }
//   if (!estado.fluxoAgendamento) {
//     estado.fluxoAgendamento = {
//       ativo: false,
//       tipoFluxo: 'AGENDAMENTO',
//       etapa: null,
//       modoSelecionado: null,
//       servicosDisponiveis: [],
//       servicoSelecionado: null,
//       dataSelecionada: null,
//       horariosDisponiveis: [],
//       horarioSelecionado: null,
//       clienteNome: null,
//       nomeCliente: null,
//       ultimaPergunta: null,
//     };
//   }
//   if (!estado.fluxoConfirmacaoPagamentoDono) {
//     estado.fluxoConfirmacaoPagamentoDono = {
//       ativo: false,
//       etapa: null,
//       agendamentoId: null,
//       protocolo: null,
//       clienteNome: null,
//       clienteTelefone: null,
//       servicoNome: null,
//       profissionalNome: null,
//       data: null,
//       horario: null,
//       enviadoEm: null,
//       segundoLembrete: false,
//     };
//   }
//   return estado;
// }

// function obterEstadoValido(chave) {
//   const estado = conversationState.get(chave);
//   if (!estado) return null;
//   console.log('[bot-state] estado carregado', {
//     conversaKey: chave,
//     etapa: estado?.fluxoAgendamento?.etapa || estado?.etapa || null,
//     updatedAt: estado?.updatedAt || null,
//   });
//   const updatedAt = Number(estado.updatedAt || 0);
//   const expirou = Date.now() - updatedAt > TEMPO_EXPIRACAO_CONVERSA_MS;
//   if (expirou) {
//     conversationState.delete(chave);
//     console.log('[bot-state] estado expirado por inatividade', {
//       conversaKey: chave,
//       etapa: estado?.fluxoAgendamento?.etapa || estado?.etapa || null,
//       minutosInativo: Math.round((Date.now() - updatedAt) / 60000),
//     });
//     return null;
//   }
//   return estado;
// }

// function limparEstadoConversa(tenantId, remoteJid, motivo) {
//   const chave = conversaKey(tenantId, remoteJid);
//   conversationState.delete(chave);
//   console.log('[bot-state] estado limpo', {
//     empresaId: tenantId,
//     remoteJid: remoteJid || null,
//     motivo,
//   });
// }

// function normalizarRemoteJid(remoteJid, telefone) {
//   const jid = String(remoteJid || '').trim();
//   if (jid) return jid;
//   const telefoneNormalizado = String(telefone || '').replace(/\D/g, '');
//   return telefoneNormalizado ? `${telefoneNormalizado}@s.whatsapp.net` : '';
// }

// function registrarConfirmacaoPagamentoDono({
//   empresaId,
//   telefone,
//   remoteJid,
//   agendamentoId,
//   protocolo,
//   clienteNome,
//   clienteTelefone,
//   servicoNome,
//   profissionalNome,
//   data,
//   horario,
//   segundoLembrete,
// }) {
//   const jid = normalizarRemoteJid(remoteJid, telefone);
//   const chave = conversaKey(empresaId, jid);
//   const estado = garantirEstadoConversa(empresaId, jid);
//   estado.fluxoConfirmacaoPagamentoDono = {
//     ativo: true,
//     etapa: 'AGUARDANDO_RESPOSTA_PAGAMENTO_DONO',
//     agendamentoId: agendamentoId || null,
//     protocolo: protocolo || null,
//     clienteNome: clienteNome || null,
//     clienteTelefone: clienteTelefone || null,
//     servicoNome: servicoNome || null,
//     profissionalNome: profissionalNome || null,
//     data: data || null,
//     horario: horario || null,
//     enviadoEm: Date.now(),
//     segundoLembrete: Boolean(segundoLembrete),
//   };
//   estado.updatedAt = Date.now();
//   conversationState.set(chave, estado);
//   console.log('[bot-pagamento-dono] estado registrado', {
//     empresaId,
//     remoteJid: jid || null,
//     agendamentoId: agendamentoId || null,
//     protocolo: protocolo || null,
//     segundoLembrete: Boolean(segundoLembrete),
//   });
//   return estado;
// }

// function isIntencaoForte(intent) {
//   return [
//     'CANCELAR',
//     'HUMANO',
//     'ATENDENTE',
//     'NOME_EMPRESA',
//     'SERVICOS',
//     'VALORES',
//     'HORARIOS',
//     'DISPONIBILIDADE',
//     'AGENDAMENTO',
//     'SAUDACAO',
//   ].includes(String(intent || '').toUpperCase());
// }

// function ehConfirmacaoPositiva(texto) {
//   const valor = normalizarBusca(texto);
//   return ['sim', 's', 'ok', 'pode', 'confirmo', 'confirmar', 'confirma'].includes(valor);
// }

// function ehConfirmacaoNegativa(texto) {
//   const valor = normalizarBusca(texto);
//   return ['nao', 'não', 'n', 'nao quero', 'não quero', 'cancelar', 'cancela', 'desmarcar', 'desmarca'].some((item) => valor.includes(normalizarBusca(item)));
// }

// function isRespostaEsperadaDaEtapa(estado, texto) {
//   const valor = normalizarBusca(texto);
//   const etapaNormalizada = String(estado?.fluxoAgendamento?.etapa || estado?.etapa || estado || '').toUpperCase();
//   if (!etapaNormalizada) return false;
//   if (['cancelar', 'cancela', 'falar com atendente', 'humano'].some((item) => valor.includes(item))) {
//     return false;
//   }
//   if (etapaNormalizada === 'AGUARDANDO_ESCOLHA_CANAL_AGENDAMENTO') {
//     return true;
//   }
//   if (etapaNormalizada === 'AGUARDANDO_NOME') {
//     return valor.length >= 2;
//   }
//   if (['AGUARDANDO_SERVICO', 'ESCOLHENDO_SERVICO', 'AGUARDANDO_PROFISSIONAL'].includes(etapaNormalizada)) {
//     return true;
//   }
//   if (['AGUARDANDO_DATA', 'ESCOLHENDO_DATA', 'AGUARDANDO_HORARIO', 'ESCOLHENDO_HORARIO', 'AGUARDANDO_CONFIRMACAO', 'CONFIRMANDO'].includes(etapaNormalizada)) {
//     return true;
//   }
//   return false;
// }

// function respostaEsperadaDaEtapa(etapa, texto) {
//   return isRespostaEsperadaDaEtapa({ etapa }, texto);
// }

// function detectarEscolhaCanalAgendamento(texto) {
//   const t = normalizarBusca(texto);
//   if (
//     t.includes('por aqui') ||
//     t.includes('por aq') ||
//     t.includes('aqui mesmo') ||
//     t.includes('whatsapp') ||
//     t.includes('pelo whatsapp') ||
//     t === 'aqui'
//   ) {
//     return 'WHATSAPP';
//   }
//   if (t.includes('link') || t.includes('site') || t.includes('pelo link')) {
//     return 'LINK';
//   }
//   return null;
// }

// function houveTrocaDeAssunto(estado, intentAtual) {
//   if (!estado) return false;
//   if (!isIntencaoForte(intentAtual)) return false;
//   const etapa = estado?.fluxoAgendamento?.etapa || estado?.etapa || null;
//   if (etapa === 'AGUARDANDO_CONFIRMACAO') {
//     return !['CONFIRMACAO', 'CONFIRMACAO_SIM_NAO'].includes(String(intentAtual || '').toUpperCase());
//   }
//   return true;
// }

// function marcarPausaAtendimentoHumano(tenantId, remoteJid, duracaoMs) {
//   const estado = garantirEstadoConversa(tenantId, remoteJid);
//   const agora = Date.now();
//   const ate = agora + Math.max(1, Number(duracaoMs) || 0);
//   estado.pausadoPeloHumano = {
//     ativo: true,
//     desde: agora,
//     ate,
//   };
//   estado.updatedAt = agora;
//   return estado.pausadoPeloHumano;
// }

// function limparPausaAtendimentoHumano(estado) {
//   if (!estado?.pausadoPeloHumano) return;
//   estado.pausadoPeloHumano.ativo = false;
//   estado.pausadoPeloHumano.ate = null;
// }

// function limparHistoricos() {
//   const agora = Date.now();
//   for (const [chave, valor] of history.entries()) {
//     if (agora - valor.lastActivity > INATIVIDADE_MS) {
//       history.delete(chave);
//     }
//   }
//   for (const [chave, valor] of conversationState.entries()) {
//     if (agora - Number(valor?.updatedAt || 0) > INATIVIDADE_MS) {
//       conversationState.delete(chave);
//     }
//   }
// }

// setInterval(limparHistoricos, LIMPEZA_MS).unref?.();

// function limparConversasExpiradas() {
//   const agora = Date.now();
//   for (const [chave, estado] of conversationState.entries()) {
//     if (agora - Number(estado?.updatedAt || 0) > CONVERSATION_TTL_MS) {
//       conversationState.delete(chave);
//       console.log('[cleanup] conversa expirada deletada', chave);
//     }
//   }
// }

// async function carregarContextoEmpresa(backendUrl, backendToken, empresaId) {
//   const cacheKey = `${backendBase(backendUrl)}:${empresaId}`;
//   const cacheAtual = contextoEmpresaCache.get(cacheKey);
//   if (cacheAtual?.value && cacheAtual.expiresAt > Date.now()) {
//     return cacheAtual.value;
//   }
//   if (cacheAtual?.promise) {
//     return cacheAtual.promise;
//   }
//   const urlBase = backendBase(backendUrl);
//   const caminhos = [
//     `/api/internal/whatsapp/contexto/${empresaId}`,
//     `/api/internal/whatsapp/config/${empresaId}`,
//   ];
//   let ultimaFalha = null;
//   const promise = (async () => {
//     for (const endpoint of caminhos) {
//       try {
//         console.log('[Bot-Debug] chamando backend para contexto', {
//           backendUrl: urlBase,
//           empresaId,
//           endpoint,
//         });
//         const response = await chamadaBackendComRetry({
//           method: 'get',
//           url: `${urlBase}${endpoint}`,
//           headers: headersInternos(backendToken),
//         }, { timeoutMs: 8000, maxTentativas: 2 });
//         const contexto = normalizarContextoWpp(response.data || {});
//         console.log('[Bot-Debug] contexto carregado', {
//           empresaId,
//           endpoint,
//           servicos: contexto.servicos.length,
//           profissionais: contexto.profissionais.length,
//           diasComHorario: contexto.horariosDisponiveis.length,
//           temLinkAgendamento: Boolean(contexto.linkAgendamento),
//         });
//         contextoEmpresaCache.set(cacheKey, {
//           value: contexto,
//           expiresAt: Date.now() + CONTEXTO_CACHE_TTL_MS,
//         });
//         return contexto;
//       } catch (error) {
//         ultimaFalha = error;
//       }
//     }
//     if (ultimaFalha) {
//       console.warn('[Bot] falha ao carregar contexto da empresa', {
//         empresaId,
//         detalhe: ultimaFalha.message,
//       });
//     }
//     return normalizarContextoWpp({});
//   })();
//   contextoEmpresaCache.set(cacheKey, { promise, expiresAt: 0 });
//   try {
//     return await promise;
//   } finally {
//     const cacheFinal = contextoEmpresaCache.get(cacheKey);
//     if (cacheFinal?.promise === promise && !cacheFinal.value) {
//       contextoEmpresaCache.delete(cacheKey);
//     }
//   }
// }

// async function carregarFluxoPersistente(backendUrl, backendToken, empresaId, telefone) {
//   const urlBase = backendBase(backendUrl);
//   try {
//     const response = await chamadaBackendComRetry({
//       method: 'get',
//       url: `${urlBase}/api/internal/whatsapp/fluxo`,
//       headers: headersInternos(backendToken),
//       params: { empresaId, telefone },
//     }, { timeoutMs: 5000, maxTentativas: 2 });
//     return response.data || null;
//   } catch (error) {
//     if (error.response?.status !== 404) {
//       console.warn('[Bot] falha ao carregar fluxo persistente', {
//         empresaId,
//         telefone,
//         detalhe: error.message,
//       });
//     }
//     return null;
//   }
// }

// async function salvarFluxoPersistente(backendUrl, backendToken, payload) {
//   const urlBase = backendBase(backendUrl);
//   try {
//     await chamadaBackendComRetry({
//       method: 'post',
//       url: `${urlBase}/api/internal/whatsapp/fluxo/salvar`,
//       data: payload,
//       headers: headersInternos(backendToken),
//     }, { timeoutMs: 5000, maxTentativas: 2 });
//   } catch (error) {
//     console.warn('[Bot] falha ao salvar fluxo persistente', {
//       empresaId: payload?.empresaId,
//       telefone: payload?.telefoneCliente,
//       detalhe: error.message,
//     });
//   }
// }

// async function resetarFluxoPersistente(backendUrl, backendToken, empresaId, telefoneCliente) {
//   const urlBase = backendBase(backendUrl);
//   try {
//     await chamadaBackendComRetry({
//       method: 'post',
//       url: `${urlBase}/api/internal/whatsapp/fluxo/resetar`,
//       data: {
//         empresaId,
//         telefoneCliente,
//       },
//       headers: headersInternos(backendToken),
//     }, { timeoutMs: 5000, maxTentativas: 2 });
//   } catch (error) {
//     if (error.response?.status !== 404) {
//       console.warn('[Bot] falha ao resetar fluxo persistente', {
//         empresaId,
//         telefoneCliente,
//         detalhe: error.message,
//       });
//     }
//   }
// }

// async function conversaPausada(backendUrl, backendToken, empresaId, telefone) {
//   const urlBase = backendBase(backendUrl);
//   try {
//     const response = await chamadaBackendComRetry({
//       method: 'get',
//       url: `${urlBase}/api/internal/whatsapp/conversa-pausada`,
//       headers: headersInternos(backendToken),
//       params: { empresaId, telefone },
//     }, { timeoutMs: 3000, maxTentativas: 2 });
//     return Boolean(response.data?.pausada);
//   } catch (error) {
//     if (error.response?.status !== 404) {
//       console.warn('[Bot] falha ao consultar pausa manual', {
//         empresaId,
//         telefone,
//         detalhe: error.message,
//       });
//     }
//     return false;
//   }
// }

// function normalizarBusca(valor) {
//   const entrada = String(valor || '');
//   const texto = entrada.length > 10000 ? entrada.slice(0, 1000) : entrada;
//   return texto
//     .normalize('NFD')
//     .replace(/[\u0300-\u036f]/g, '')
//     .toLowerCase()
//     .replace(/[^a-z0-9\s]/g, ' ')
//     .replace(/\s+/g, ' ')
//     .trim();
// }

// async function chamadaBackendComRetry(config, opcoes = {}) {
//   const maxTentativas = Number(opcoes.maxTentativas || 2);
//   const timeoutMs = Number(opcoes.timeoutMs || 8000);
//   for (let tentativa = 1; tentativa <= maxTentativas; tentativa += 1) {
//     try {
//       const resposta = await axios({
//         timeout: timeoutMs,
//         ...config,
//       });
//       return resposta;
//     } catch (err) {
//       if (tentativa >= maxTentativas) {
//         throw err;
//       }
//       console.warn('[retry] tentativa falhou, tentando novamente', {
//         tentativa,
//         url: config?.url,
//         detalhe: err.message,
//       });
//       await new Promise((resolve) => setTimeout(resolve, 1000 * tentativa));
//     }
//   }
//   return null;
// }

// function listaDeStrings(valor) {
//   if (!Array.isArray(valor)) return [];
//   return valor
//     .map((item) => {
//       if (typeof item === 'string') return item.trim();
//       if (item && typeof item === 'object') return String(item.nome || item.label || item.valor || '').trim();
//       return '';
//     })
//     .filter(Boolean);
// }

// function formatarMoeda(valor) {
//   const numero = Number(valor);
//   if (Number.isNaN(numero)) return '';
//   return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(numero);
// }

// function normalizarServicos(valor) {
//   if (!Array.isArray(valor)) return [];
//   return valor
//     .map((item) => {
//       if (!item || typeof item !== 'object') return null;
//       return {
//         id: item.id ?? null,
//         nome: normalizarTexto(item.nome || item.label || item.servico || ''),
//         valor: item.valor ?? item.preco ?? null,
//         duracaoMinutos: item.duracaoMinutos ?? item.duracao ?? null,
//         status: normalizarTexto(item.status || ''),
//       };
//     })
//     .filter((item) => item && item.nome);
// }

// function normalizarHorariosDisponiveis(valor) {
//   if (!Array.isArray(valor)) return [];
//   return valor
//     .map((item) => {
//       if (!item || typeof item !== 'object') return null;
//       const data = normalizarTexto(item.data || '');
//       const horarios = Array.isArray(item.horarios) ? item.horarios.map((horario) => normalizarTexto(horario)).filter(Boolean) : [];
//       return data && horarios.length ? { data, horarios } : null;
//     })
//     .filter(Boolean);
// }

// function formatarDuracaoMinutos(duracaoMinutos) {
//   const numero = Number(duracaoMinutos);
//   if (!Number.isFinite(numero) || numero <= 0) return '';
//   const inteiro = Math.round(numero);
//   return `${inteiro} min`;
// }

// function formatarLinhaServico(servico) {
//   if (!servico || typeof servico !== 'object') return '';
//   const nome = normalizarTexto(servico.nome || '');
//   if (!nome) return '';
//   const partes = [`• *${nome}*`];
//   const valorFormatado = formatarMoeda(servico.valor);
//   if (valorFormatado) {
//     partes.push(valorFormatado);
//   }
//   const duracaoFormatada = formatarDuracaoMinutos(servico.duracaoMinutos);
//   if (duracaoFormatada) {
//     partes.push(`(${duracaoFormatada})`);
//   }
//   return partes.join(' — ');
// }

// function formatarListaServicos(servicos) {
//   const lista = Array.isArray(servicos) ? servicos : [];
//   const linhas = lista
//     .slice(0, 10)
//     .map(formatarLinhaServico)
//     .filter(Boolean);
//   if (!linhas.length) return '';
//   if (lista.length > 10) {
//     linhas.push('E mais alguns outros — me diga qual serviço te interessa que eu confirmo o valor.');
//   }
//   return `Aqui estão nossos serviços:\n\n${linhas.join('\n')}\n\nQuer agendar algum desses? Posso te mandar o link de agendamento.`;
// }

// function formatarListaHorarios(horarios) {
//   return horarios
//     .map((item) => {
//       const dataTexto = normalizarTexto(item.data);
//       const matchDataIso = dataTexto.match(/^(\d{4})-(\d{2})-(\d{2})$/);
//       const dataFormatada = matchDataIso
//         ? `${matchDataIso[3]}/${matchDataIso[2]}`
//         : dataTexto;
//       return `• ${dataFormatada}: ${item.horarios.join(', ')}`;
//     })
//     .filter(Boolean)
//     .join('\n');
// }

// function identificarServicoMencionado(texto, servicos) {
//   const conteudo = normalizarBusca(texto);
//   if (!conteudo) return null;
//   for (const servico of servicos) {
//     const nome = normalizarBusca(servico?.nome || '');
//     if (!nome) continue;
//     if (conteudo.includes(nome)) return servico;
//     const tokens = nome.split(' ').filter(Boolean);
//     if (tokens.length > 1 && tokens.every((token) => conteudo.includes(token))) {
//       return servico;
//     }
//   }
//   return null;
// }

// function ehMensagemVaga(texto) {
//   const valor = normalizarBusca(texto);
//   if (!valor) return true;
//   return [
//     '?',
//     'ok',
//     'okay',
//     'beleza',
//     'blz',
//     'certo',
//     'sim',
//     'nao',
//     'não',
//     'entendi',
//     'perfeito',
//     'pode ser',
//     'achou',
//     'achou?',
//     'e ai',
//     'e aí',
//     'conseguiu',
//     'conseguiu?',
//     'ta bom',
//     'tá bom',
//     'verificou',
//     'verificou?',
//     'viu',
//     'viu?',
//     'checou',
//     'checou?',
//   ].some((item) => valor === normalizarBusca(item));
// }

// function valorOuPadrao(valor, padrao) {
//   const texto = normalizarTexto(valor);
//   return texto || padrao;
// }

// function verificarRateLimit(estado, agora = Date.now()) {
//   const JANELA_MS = 60_000;
//   const LIMITE_MENSAGENS = 8;
//   const COOLDOWN_MS = 3 * 60_000;

//   estado.rateLimit = estado.rateLimit || {
//     timestamps: [],
//     bloqueadoAte: null,
//     ultimoAvisoEm: null,
//   };

//   const rateLimit = estado.rateLimit;
//   rateLimit.timestamps = Array.isArray(rateLimit.timestamps) ? rateLimit.timestamps : [];
//   rateLimit.timestamps = rateLimit.timestamps.filter((ts) => agora - ts < JANELA_MS);

//   if (rateLimit.bloqueadoAte && agora < rateLimit.bloqueadoAte) {
//     return { permitido: false, motivo: 'cooldown', deveAvisar: false };
//   }

//   if (rateLimit.bloqueadoAte && agora >= rateLimit.bloqueadoAte) {
//     rateLimit.bloqueadoAte = null;
//     rateLimit.ultimoAvisoEm = null;
//   }

//   rateLimit.timestamps.push(agora);

//   if (rateLimit.timestamps.length > LIMITE_MENSAGENS) {
//     rateLimit.bloqueadoAte = agora + COOLDOWN_MS;
//     rateLimit.ultimoAvisoEm = agora;
//     return { permitido: false, motivo: 'limite_excedido', deveAvisar: true };
//   }

//   return { permitido: true, motivo: null, deveAvisar: false };
// }

// function ehIntencaoIdentidade(texto) {
//   const valor = normalizarBusca(texto);
//   if (!valor) return false;
//   return [
//     /\bo que\s+(vc|voce)\s+(e|eh)\b/,
//     /\bquem\s+(e|eh)\s+(vc|voce)\b/,
//     /\b(vc|voce)\s+(e|eh)\s+um\s+(robo|bot|ia)\b/,
//     /\b(vc|voce)\s+(e|eh)\s+real\b/,
//     /\bisso\s+e\s+automatico\b/,
//     /\be\s+uma\s+ia\b/,
//     /\bt[oô]\s+falando\s+com\s+quem\b/,
//     /\bestou\s+falando\s+com\s+quem\b/,
//     /\bquem\s+estou\s+falando\b/,
//     /\bquem\s+sou\s+eu\s+falando\b/,
//     /\b(vc|voce)\s+(eh|e)\s+uma?\s+(ia|assistente|assistente virtual)\b/,
//   ].some((regex) => regex.test(valor));
// }

// function normalizarContextoBruto(raw = {}) {
//   const contextoEmpresa = raw?.empresa && typeof raw.empresa === 'object' ? raw.empresa : {};
//   const contextoDados = raw?.contexto && typeof raw.contexto === 'object' ? raw.contexto : {};
//   const configuracao = raw?.config && typeof raw.config === 'object' ? raw.config : {};
//   const configuracaoInterna = raw?.configuracao && typeof raw.configuracao === 'object' ? raw.configuracao : {};
//   const payloadPrincipal = raw?.data && typeof raw.data === 'object' ? raw.data : {};
//   const base = {
//     ...payloadPrincipal,
//     ...contextoEmpresa,
//     ...contextoDados,
//     ...configuracao,
//     ...configuracaoInterna,
//     ...(raw || {}),
//   };
//   return {
//     ...base,
//     linkAgendamento:
//       base.linkAgendamento ||
//       base.link_agendamento ||
//       base.urlAgendamento ||
//       base.linkPublico ||
//       '',
//     agendamentoSlug:
//       base.agendamentoSlug ||
//       base.agendamento_slug ||
//       base.slug ||
//       base.slugPublico ||
//       '',
//     nomeEmpresa:
//       base.nomeEmpresa ||
//       base.nome_empresa ||
//       base.nomeFantasia ||
//       base.nome ||
//       base.razaoSocial ||
//       '',
//     servicos: Array.isArray(base.servicos) ? base.servicos : [],
//   };
// }

// function resolverLinkAgendamentoLegacy(contexto) {
//   if (contexto?.linkAgendamento) return normalizarTexto(contexto.linkAgendamento);
//   const slug = normalizarTexto(contexto?.agendamentoSlug || contexto?.slug || '');
//   if (slug) {
//     const base = (process.env.PUBLIC_BASE_URL || 'https://gendaz.site').replace(/\/+$/, '');
//     return `${base}/agendar/${slug}`;
//   }
//   return '';
// }

// function contextoPadrao(phoneCliente) {
//   return {
//     empresaId: null,
//     nomeEmpresa: '',
//     descricaoEmpresa: '',
//     assistenteAtivo: false,
//     mensagemBoasVindas: 'Olá! Seja bem-vindo. Como posso te ajudar hoje?',
//     respostaHorarios: 'Claro! Para verificar os dias e horários disponíveis, acesse o link de agendamento.',
//     respostaServicos: 'Temos alguns serviços disponíveis. Me diga qual você deseja agendar.',
//     respostaNaoEntende: 'Desculpa, não entendi muito bem. Pode me explicar de outra forma?',
//     mensagemHumano: 'Vou encaminhar sua mensagem para um atendente continuar o atendimento.',
//     linkAgendamento: '',
//     servicos: [],
//     profissionais: [],
//     horariosDisponiveis: [],
//     whatsappConnected: false,
//     whatsappPhone: '',
//     notificacoesAutomaticas: false,
//     secretariaIaAtiva: false,
//     clientePhone: phoneCliente || '',
//   };
// }

// function normalizarContexto(raw, phoneCliente) {
//   const bruto = normalizarContextoBruto(raw || {});
//   const base = bruto;
//   const nomeEmpresa = nomeEmpresaSeguro(base.nomeEmpresa || base.nome || base.empresaNome || '');
//   const servicos = normalizarServicos(base.servicos);
//   const linkAgendamento = valorOuPadrao(base.linkAgendamento, resolverLinkAgendamento(base));
//   return {
//     empresaId: base.empresaId || base.tenantId || null,
//     nomeEmpresa,
//     descricaoEmpresa: valorOuPadrao(base.descricaoEmpresa, ''),
//     agendamentoSlug: valorOuPadrao(base.agendamentoSlug, ''),
//     assistenteAtivo: Boolean(base.assistenteAtivo || base.iaHabilitada || base.secretariaIaAtiva),
//     mensagemBoasVindas: valorOuPadrao(base.mensagemBoasVindas, nomeEmpresa ? `Olá! Seja bem-vindo à ${nomeEmpresa}. Como posso te ajudar hoje?` : 'Olá! Seja bem-vindo. Como posso te ajudar hoje?'),
//     respostaHorarios: valorOuPadrao(base.respostaHorarios, 'Claro! Para verificar os dias e horários disponíveis, acesse o link de agendamento.'),
//     respostaServicos: valorOuPadrao(base.respostaServicos, 'Temos alguns serviços disponíveis. Me diga qual você deseja agendar.'),
//     respostaNaoEntende: valorOuPadrao(base.respostaNaoEntende, 'Desculpa, não entendi muito bem. Pode me explicar de outra forma?'),
//     mensagemHumano: valorOuPadrao(base.mensagemHumano, 'Vou encaminhar sua mensagem para um atendente continuar o atendimento.'),
//     linkAgendamento,
//     servicos,
//     profissionais: Array.isArray(base.profissionais) ? base.profissionais : [],
//     horariosDisponiveis: normalizarHorariosDisponiveis(base.horariosDisponiveis),
//     whatsappConnected: Boolean(base.whatsappConnected || base.ativo),
//     whatsappPhone: valorOuPadrao(base.whatsappPhone || base.numeroConectado || base.displayPhoneNumber, ''),
//     notificacoesAutomaticas: Boolean(base.notificacoesAutomaticas || base.notificacoesHabilitadas),
//     secretariaIaAtiva: Boolean(base.secretariaIaAtiva || base.iaHabilitada),
//     clientePhone: phoneCliente || '',
//   };
// }

// async function carregarContextoEmpresaLegacy(baseUrl, token, tenantId, phoneCliente) {
//   const headers = token ? { 'X-Internal-Token': token } : {};
//   const rotas = [
//     `/api/internal/whatsapp/config/${tenantId}`,
//     `/api/internal/whatsapp/contexto/${tenantId}`,
//   ];
//   console.log('[Bot-Debug] chamando backend para contexto', {
//     backendUrl: baseUrl,
//     empresaId: tenantId,
//     endpoint: rotas[0],
//   });
//   let ultimaResposta = null;
//   for (const rota of rotas) {
//     try {
//       const response = await chamadaBackendComRetry({
//         method: 'get',
//         url: `${baseUrl}${rota}`,
//         headers,
//       }, { timeoutMs: 8000, maxTentativas: 2 });
//       ultimaResposta = response.data;
//       console.log('[Bot-Debug] resposta bruta do contexto', JSON.stringify(response.data, null, 2));
//       const contexto = normalizarContexto(response.data, phoneCliente);
//       const temDadosRelevantes = Boolean(
//         contexto?.nomeEmpresa ||
//         contexto?.linkAgendamento ||
//         contexto?.agendamentoSlug ||
//         (Array.isArray(contexto?.servicos) && contexto.servicos.length) ||
//         (Array.isArray(contexto?.profissionais) && contexto.profissionais.length) ||
//         (Array.isArray(contexto?.horariosDisponiveis) && contexto.horariosDisponiveis.length),
//       );
//       console.log('[bot-contexto] normalizado', {
//         empresaId: tenantId,
//         nomeEmpresa: contexto?.nomeEmpresa || '',
//         agendamentoSlug: contexto?.agendamentoSlug || '',
//         linkAgendamento: contexto?.linkAgendamento || '',
//         totalServicos: contexto?.servicos?.length || 0,
//       });
//       if (temDadosRelevantes) {
//         console.log('[Bot] contexto recebido', {
//           empresaId: tenantId,
//           nomeEmpresa: contexto?.nomeEmpresa || '',
//           agendamentoSlug: contexto?.agendamentoSlug || '',
//           descricaoEmpresa: contexto?.descricaoEmpresa || '',
//           linkAgendamento: contexto?.linkAgendamento || '',
//           totalServicos: contexto?.servicos?.length || 0,
//           totalProfissionais: contexto?.profissionais?.length || 0,
//         });
//         if (!contexto?.linkAgendamento) {
//           console.error('[Bot] linkAgendamento ausente no contexto', {
//             empresaId: tenantId,
//             nomeEmpresa: contexto?.nomeEmpresa || '',
//             totalServicos: contexto?.servicos?.length || 0,
//           });
//         }
//         if (!contexto?.nomeEmpresa || !Array.isArray(contexto?.servicos) || !Array.isArray(contexto?.horariosDisponiveis)) {
//           console.warn('[Bot] contexto incompleto recebido do backend', {
//             empresaId: tenantId,
//           nomeEmpresa: contexto?.nomeEmpresa || '',
//           totalServicos: contexto?.servicos?.length || 0,
//           totalProfissionais: contexto?.profissionais?.length || 0,
//           totalDiasComHorario: contexto?.horariosDisponiveis?.length || 0,
//           temLinkAgendamento: Boolean(contexto?.linkAgendamento),
//         });
//         }
//         return contexto;
//       }
//     } catch (error) {
//       console.warn('[Bot-Debug] falha ao carregar contexto pela rota', {
//         empresaId: tenantId,
//         endpoint: rota,
//         detalhe: error.message,
//       });
//     }
//   }
//   const contexto = normalizarContexto(ultimaResposta || {}, phoneCliente);
//   console.log('[bot-contexto] normalizado', {
//     empresaId: tenantId,
//     nomeEmpresa: contexto?.nomeEmpresa || '',
//     agendamentoSlug: contexto?.agendamentoSlug || '',
//     linkAgendamento: contexto?.linkAgendamento || '',
//     totalServicos: contexto?.servicos?.length || 0,
//   });
//   console.log('[Bot] contexto recebido', {
//     empresaId: tenantId,
//     nomeEmpresa: contexto?.nomeEmpresa || '',
//     agendamentoSlug: contexto?.agendamentoSlug || '',
//     descricaoEmpresa: contexto?.descricaoEmpresa || '',
//     linkAgendamento: contexto?.linkAgendamento || '',
//     totalServicos: contexto?.servicos?.length || 0,
//   });
//   if (!contexto?.linkAgendamento) {
//     console.error('[Bot] linkAgendamento ausente no contexto', {
//       empresaId: tenantId,
//       nomeEmpresa: contexto?.nomeEmpresa || '',
//       totalServicos: contexto?.servicos?.length || 0,
//     });
//   }
//   if (!contexto?.nomeEmpresa || !Array.isArray(contexto?.servicos) || !Array.isArray(contexto?.horariosDisponiveis)) {
//     console.warn('[Bot] contexto incompleto recebido do backend', {
//       empresaId: tenantId,
//       nomeEmpresa: contexto?.nomeEmpresa || '',
//       totalServicos: contexto?.servicos?.length || 0,
//       totalDiasComHorario: contexto?.horariosDisponiveis?.length || 0,
//       temLinkAgendamento: Boolean(contexto?.linkAgendamento),
//     });
//   }
//   const contextoPadraoRetorno = contextoPadrao(phoneCliente);
//   console.warn('[Bot] contexto indisponivel, usando padrao:', {
//     empresaId: tenantId,
//     detalhe: 'Nenhuma rota de contexto respondeu com payload util.',
//   });
//   return contexto || contextoPadraoRetorno;
// }

// const buscarContexto = carregarContextoEmpresa;

// async function buscarClienteNome(baseUrl, token, tenantId, phoneCliente) {
//   const telefone = String(phoneCliente || '').trim();
//   if (!/^\d{13}$/.test(telefone)) {
//     return '';
//   }
//   const headers = token ? { 'X-Internal-Token': token } : {};
//   try {
//     const response = await chamadaBackendComRetry({
//       method: 'get',
//       url: `${baseUrl}/api/internal/whatsapp/cliente`,
//       headers,
//       params: { phone: telefone },
//     }, { timeoutMs: 8000, maxTentativas: 2 });
//     return normalizarTexto(response.data?.nome || '');
//   } catch {
//     return '';
//   }
// }

// function montarSystemPrompt(contexto, clienteNome, estado, intencao) {
//   const nomeEmpresa = nomeEmpresaSeguro(contexto);
//   const descricaoEmpresa = contexto?.descricaoEmpresa || '';
//   const servicos = normalizarServicos(contexto?.servicos);
//   const linhasServicos = servicos.length ? formatarListaServicos(servicos) : 'nenhum serviço cadastrado';
//   const linkAgendamento = resolverLinkAgendamento(contexto);
//   const proibicoes = [
//     nomeEmpresa
//       ? `REGRA ABSOLUTA: Você representa APENAS a empresa ${nomeEmpresa}.`
//       : 'REGRA ABSOLUTA: Você representa APENAS a empresa configurada no sistema.',
//     'Nunca diga que é uma "plataforma", "sistema", "empresa de atendimento pelo WhatsApp" ou qualquer outra descrição inventada.',
//     'Se o cliente perguntar o que você é, ou se você é um robô/IA, responda com transparência que é uma assistente virtual de IA da empresa, sem fingir ser humana.',
//     nomeEmpresa ? `Nunca use o nome "nossa empresa" — sempre use: ${nomeEmpresa}.` : 'Nunca invente o nome da empresa.',
//     'Nunca invente serviços, preços, horários ou qualquer dado não fornecido abaixo.',
//     'Nunca invente o que a empresa faz.',
//     'Nunca diga que não há horários disponíveis.',
//     'Nunca diga que não encontrou horários, dias livres ou vagas.',
//     'Nunca diga que vai verificar horários ou retornar depois.',
//     'Nunca use o texto literal "[link de agendamento]".',
//     'Se não souber algo, diga que não tem essa informação e ofereça ajuda com o que está disponível.',
//   ].join('\n');
//   const instrucaoPorIntencao = {
//     SAUDACAO: `O cliente está cumprimentando. Responda usando exatamente a mensagem de boas-vindas configurada no painel. Não acrescente descrição da empresa, não invente informações e não substitua o texto configurado.`,
//     IDENTIDADE: `O cliente quer saber quem você é, se é robô ou IA. Responda com transparência que você é a assistente virtual de IA da ${nomeEmpresa || 'empresa'} e convide a continuar perguntando sobre serviços, valores e agendamento.`,
//     NOME_LOJA: `O cliente quer saber o nome da empresa. Responda apenas com o nome real: ${nomeEmpresa || 'não configurado'}. Descreva brevemente o que a empresa faz e ofereça ajuda.`,
//     SERVICOS: `O cliente quer saber sobre serviços ou valores. Use APENAS os serviços cadastrados abaixo. Não invente serviços. Se o cliente mencionou um serviço específico, foque nele.`,
//     HORARIOS: `O cliente quer saber sobre horários, dias livres, agenda ou disponibilidade. A resposta obrigatória é enviar o link oficial de agendamento: ${linkAgendamento || 'link não configurado'}.`,
//     AGENDAMENTO: `O cliente quer agendar. A resposta obrigatória é enviar o link oficial de agendamento: ${linkAgendamento || 'link não configurado'}.`,
//     HUMANO: `O cliente quer falar com um atendente humano. Use a mensagem configurada: ${contexto?.mensagemHumano || 'Vou encaminhar para um atendente.'}.`,
//     FALLBACK: `O cliente fez uma pergunta que pode ser sobre a empresa. Tente responder de forma natural e útil. Se não souber a resposta, use a mensagem de fallback e ofereça ajuda com serviços, horários ou agendamento.`,
//   }[intencao] || `Responda de forma natural e útil sobre a ${nomeEmpresa}.`;
//   return [
//     proibicoes,
//     nomeEmpresa
//       ? `Você é a assistente virtual da empresa: ${nomeEmpresa}.`
//       : 'Você é a assistente virtual desta empresa.',
//     descricaoEmpresa ? `Sobre a empresa: ${descricaoEmpresa}.` : '',
//     'Você atende clientes pelo WhatsApp de forma curta, natural, educada e objetiva, como uma atendente real.',
//     'Represente apenas a empresa conectada. Nunca diga que é assistente da plataforma.',
//     'Nunca use o nome do dono/usuario como se fosse o cliente.',
//     'Não invente serviços, horários ou preços.',
//     nomeEmpresa ? `Nome da empresa: ${nomeEmpresa}.` : 'Nome da empresa: não configurado.',
//     `Ultima intencao conhecida: ${estado?.lastIntent || 'nenhuma'}.`,
//     estado?.lastService?.nome ? `Ultimo servico citado: ${estado.lastService.nome}.` : 'Ultimo servico citado: nenhum.',
//     estado?.lastMessage ? `Ultima pergunta do cliente: ${estado.lastMessage}.` : 'Ultima pergunta do cliente: nenhuma.',
//     `Mensagem de boas-vindas: ${contexto?.mensagemBoasVindas || ''}.`,
//     `Resposta sobre horários: ${contexto?.respostaHorarios || ''}.`,
//     `Resposta sobre serviços: ${contexto?.respostaServicos || ''}.`,
//     `Mensagem de fallback: ${contexto?.respostaNaoEntende || ''}.`,
//     `Mensagem para humano: ${contexto?.mensagemHumano || ''}.`,
//     `Link oficial de agendamento: ${linkAgendamento || 'não configurado'}.`,
//     `Instrução principal para esta mensagem: ${instrucaoPorIntencao}.`,
//     `Serviços cadastrados:\n${linhasServicos}.`,
//     clienteNome ? `Cliente atual: ${clienteNome}.` : 'Cliente atual: não identificado.',
//     'Se o cliente perguntar sobre horário, dia disponível, agenda, vaga, consulta ou agendamento, envie o link oficial e diga que por lá ele vê dias e horários disponíveis.',
//     'Se a pergunta for sobre nome da loja, responda apenas com o nome da empresa e o que ela faz.',
//     'Se a pergunta for sobre serviços ou valores, liste os serviços reais, com valor e duração se existirem.',
//     'Se não souber responder, use a mensagem de fallback configurada.',
//     'Seja breve e direto. Máximo 3 parágrafos por resposta.',
//     'Responda sempre em português brasileiro.',
//   ].join('\n');
// }

// function limitarParagrafos(resposta) {
//   const blocos = String(resposta || '')
//     .split(/\n{2,}/)
//     .map((item) => item.trim())
//     .filter(Boolean);
//   return blocos.slice(0, 3).join('\n\n');
// }

// function respostaIndicaAgendamento(texto) {
//   const valor = normalizarTexto(texto).toLowerCase();
//   return /(agendad[oa]|confirmad[oa]|marcad[oa])/.test(valor);
// }

// function detectarIntencao(texto, lastIntent) {
//   const valor = normalizarBusca(texto);
//   if (!valor) return 'FALLBACK';
//   if (/(atendente|humano|pessoa|pessoa real|falar com alguem|falar com pessoa|suporte|dono|responsavel|responsável|me passa para|passa para atendente|quero falar com)/.test(valor)) return 'HUMANO';
//   if (/(^|\s)(oi+|ola+|olaa+|oii+|eai|e ai|iae|ei+|hey|bom dia|boa tarde|boa noite|boas|tudo bem|tudo bom|tudo certo|opa|salve|e isso|oi tudo|ola tudo|como vai|como voce|bom dia tudo|boa tarde tudo)(\s|$|[?!])/.test(valor)) return 'SAUDACAO';
//   if (ehIntencaoIdentidade(valor)) return 'IDENTIDADE';
//   if (/(qual nome|nome da loja|nome da empresa|que loja|qual empresa|onde estou|quem e voce|voces sao quem|quem sao vcs|quem sao voces|qual o nome da empresa|qual o nome|voce e quem|com quem falo|qual o estabelecimento|qual negocio|que negocio|que estabelecimento)/.test(valor)) return 'NOME_LOJA';
//   if (/(tao aberto|estao aberto|vcs atendem|voces atendem|atendem hoje|atendem amanha|aberto hoje|aberto amanha|funcionando|horario de funcionamento|que horas abre|que horas fecha|funcionam|qual horario de atendimento|horario comercial|expediente)/.test(valor)) return 'HORARIOS';
//   if (/(servicos|servico|servi\s*os|quais servicos|qual servico|o que voces fazem|o que vcs fazem|o que voce faz|o que fazem|que servicos|que oferecem|o que oferecem|valores|preco|precos|quanto custa|quanto e|quanto cobram|quanto voces cobram|valor|tabela|cardapio|menu|quanto cobra|pacote|opcoes|opcao|tem servico|tem servicos|quais sao os|quais sao seus)/.test(valor)) return 'SERVICOS';
//   if (/(horarios|horario|hor\s*rios|tem horario|tem vaga|tem vagas|agenda|vaga|vagas|hoje|amanha|essa semana|proxima semana|disponivel|disponiveis|horario disponivel|dia disponivel|dias disponiveis|proximo horario|quando tem|quando voces|tem dia|tem algum)/.test(valor)) return 'HORARIOS';
//   if (/(quero marcar|quero agendar|queria marcar|queria agendar|marcar|agendar|agendamento|consulta|atendimento|reservar|reserva|quero um horario|quero uma consulta|como agendar|como marco|como faco para agendar|quero contratar|quero chamar|quero usar|preciso marcar|preciso agendar)/.test(valor)) return 'AGENDAMENTO';
//   if (ehMensagemVaga(valor) || /(e os horarios|e os servicos|e os dados|e entao|e então)/.test(valor)) return 'VAGO';
//   return 'FALLBACK';
// }

// function responderPorIntencao({ intencao, contexto, estado, texto, clienteNome }) {
//   const nomeEmpresa = nomeEmpresaSeguro(contexto);
//   const descricaoEmpresa = contexto?.descricaoEmpresa || '';
//   const respostaBoasVindas = contexto?.mensagemBoasVindas || 'Olá! Como posso te ajudar hoje?';
//   const respostaServicos = contexto?.respostaServicos || 'Temos alguns serviços disponíveis. Me diga qual você deseja agendar.';
//   const respostaNaoEntende = contexto?.respostaNaoEntende || 'Desculpa, não entendi muito bem. Pode me explicar de outra forma?';
//   const mensagemHumano = contexto?.mensagemHumano || 'Vou encaminhar sua mensagem para um atendente continuar o atendimento.';
//   const linkAgendamento = resolverLinkAgendamento(contexto);
//   const servicos = normalizarServicos(contexto?.servicos);
//   const listaServicosFormatada = servicos.length ? formatarListaServicos(servicos) : '';
//   const servicoMencionado = identificarServicoMencionado(texto, servicos);
//   const baseSaudacao = primeiraFrase(respostaBoasVindas) || `Olá! Seja bem-vindo à ${nomeEmpresa}.`;
//   const baseServicos = primeiraFrase(respostaServicos) || 'Temos estes serviços disponíveis:';
//   const baseFallback = primeiraFrase(respostaNaoEntende) || 'Desculpa, não entendi muito bem.';
//   const respostaSemLink = 'Ainda não encontrei o link de agendamento configurado para esta empresa. Vou encaminhar para um atendente te ajudar.';
//   const respostaLinkAgendamento = `Claro! Para verificar os dias e horários disponíveis e fazer seu agendamento, acesse este link:\n${linkAgendamento}\n\nPor lá você escolhe o serviço, o dia e o horário disponível.`;
//   const respostaDeLinkObrigatoria = ['HORARIOS', 'DISPONIBILIDADE', 'DIAS_DISPONIVEIS'].includes(intencao);

//   switch (intencao) {
//     case 'SAUDACAO':
//       return {
//         resposta: saudacaoComNome(clienteNome, respostaBoasVindas),
//         intent: 'SAUDACAO',
//       };
//     case 'IDENTIDADE':
//       return {
//         resposta: combinarTomComConteudo(
//           nomeEmpresa
//             ? `Sou a assistente virtual da ${nomeEmpresa}! Estou aqui pra te ajudar com informações sobre serviços, valores e agendamentos.`
//             : 'Sou a assistente virtual desta empresa! Estou aqui pra te ajudar com informações sobre serviços, valores e agendamentos.',
//           descricaoEmpresa ? `Sobre a empresa: ${descricaoEmpresa}` : '',
//           'Quer ver os horários disponíveis ou saber mais sobre algum serviço?',
//         ),
//         intent: 'IDENTIDADE',
//       };
//     case 'NOME_LOJA':
//       return {
//         resposta: combinarTomComConteudo(
//           nomeEmpresa
//             ? `Você está falando com a ${nomeEmpresa}.`
//             : 'O nome da empresa ainda não está configurado aqui.',
//           `${descricaoEmpresa ? `Sobre a empresa: ${descricaoEmpresa}\n` : ''}Sou a assistente virtual${nomeEmpresa ? ` da ${nomeEmpresa}` : ''} e posso te ajudar com dúvidas sobre serviços, valores e agendamento.`,
//         ),
//         intent: 'NOME_LOJA',
//       };
//     case 'HUMANO':
//       return { resposta: mensagemHumano, intent: 'HUMANO' };
//     case 'AGENDAMENTO': {
//       return {
//         resposta: mensagemInicioAgendamento(linkAgendamento),
//         intent: 'AGENDAMENTO',
//       };
//     }
//     case 'HORARIOS':
//     case 'DISPONIBILIDADE':
//     case 'DIAS_DISPONIVEIS':
//       return {
//         resposta: mensagemInicioAgendamento(linkAgendamento),
//         intent,
//       };
//     case 'SERVICOS': {
//       if (servicoMencionado) {
//         const partes = [`O ${servicoMencionado.nome}`];
//         const valorFormatado = formatarMoeda(servicoMencionado.valor);
//         if (valorFormatado) {
//           partes.push(`custa ${valorFormatado}`);
//         }
//         if (servicoMencionado.duracaoMinutos) {
//           partes.push(`e dura ${servicoMencionado.duracaoMinutos} min`);
//         }
//         let respostaServicoEspecifico = partes.join(' ');
//         if (linkAgendamento) {
//           respostaServicoEspecifico += `.\nSe quiser agendar, acesse: ${linkAgendamento}`;
//         }
//         return {
//           resposta: respostaServicoEspecifico,
//           servico: servicoMencionado,
//           intent: 'SERVICOS',
//         };
//       }
//       if (listaServicosFormatada) {
//         return {
//           resposta: `Temos os seguintes serviços disponíveis:\n${listaServicosFormatada}${linkAgendamento ? `\n\nPara agendar, acesse: ${linkAgendamento}.` : ''}`,
//           servico: servicos[0] || null,
//           intent: 'SERVICOS',
//         };
//       }
//       return {
//         resposta: combinarTomComConteudo(
//           baseServicos,
//           linkAgendamento
//             ? `O agendamento é feito pelo link: ${linkAgendamento}.`
//             : 'No momento ainda não encontrei serviços cadastrados no sistema.',
//         ),
//         servico: null,
//         intent: 'SERVICOS',
//       };
//     }
//     case 'HORARIOS': {
//       if (linkAgendamento) {
//         return {
//           resposta: respostaLinkAgendamento,
//           intent: 'HORARIOS',
//         };
//       }
//       return { resposta: respostaSemLink, intent: 'HORARIOS' };
//     }
//     case 'SAUDACAO_CONTINUA':
//       return {
//         resposta: combinarTomComConteudo(
//           baseSaudacao,
//           nomeEmpresa
//             ? `Como atendente da ${nomeEmpresa}, posso te ajudar com serviços, valores, horários e agendamento.`
//             : 'Como posso te ajudar hoje com serviços, valores, horários e agendamento?',
//         ),
//         intent: estado?.lastIntent || 'SAUDACAO',
//       };
//     default:
//     if (respostaDeLinkObrigatoria) {
//       return {
//         resposta: mensagemInicioAgendamento(linkAgendamento),
//         intent: intencao,
//       };
//     }
//     if (estado?.lastIntent === 'AGENDAMENTO' && linkAgendamento) {
//       return {
//         resposta: mensagemInicioAgendamento(linkAgendamento),
//         intent: 'AGENDAMENTO',
//       };
//     }
//       if (estado?.lastIntent === 'SERVICOS' && listaServicosFormatada) {
//         return {
//           resposta: combinarTomComConteudo(
//             baseServicos,
//             `Temos estes serviços disponíveis:\n${listaServicosFormatada}${linkAgendamento ? `\n\nO agendamento é feito pelo link: ${linkAgendamento}.` : ''}`,
//           ),
//           intent: 'SERVICOS',
//         };
//       }
//       if (estado?.lastIntent === 'HORARIOS') {
//         return {
//           resposta: linkAgendamento ? respostaLinkAgendamento : respostaSemLink,
//           intent: 'HORARIOS',
//         };
//       }
//       if (estado?.lastIntent === 'HUMANO') {
//         return { resposta: mensagemHumano, intent: 'HUMANO' };
//       }
//       return {
//         resposta: combinarTomComConteudo(
//           baseFallback,
//           `Posso te ajudar com serviços, valores, horários ou agendamento${nomeEmpresa ? ` da ${nomeEmpresa}` : ''}.`,
//         ),
//         intent: 'FALLBACK',
//       };
//   }
// }

// async function responderCliente({ tenantId, empresaId, remoteJid, phoneCliente, identificadorCliente, texto, backendUrl, backendToken, clienteNome }) {
//   const identificadorTenant = tenantId || empresaId;
//   const chaveCliente = phoneCliente || identificadorCliente || remoteJid || 'desconhecido';
//   const chave = garantirHistorico(identificadorTenant, chaveCliente);
//   const conversaId = conversaKey(identificadorTenant, remoteJid || chaveCliente);
//   let estado = obterEstadoValido(conversaId) || garantirEstadoConversa(identificadorTenant, remoteJid || chaveCliente);
//   const conteudo = normalizarTexto(texto);
//   const MAX_TAMANHO_MENSAGEM = 10000;
//   if (conteudo.length > MAX_TAMANHO_MENSAGEM) {
//     console.warn('[validate] mensagem excede limite', {
//       empresaId: identificadorTenant,
//       tamanho: conteudo.length,
//     });
//     return {
//       respostaFinal: 'Mensagem muito longa. Por favor, envie uma mensagem menor.',
//       intentFinal: 'VAGO',
//       contexto: null,
//       estado,
//       respostaDeterministica: null,
//       identificadorTenant,
//       chaveCliente,
//       chave,
//       conteudo: conteudo.slice(0, MAX_TAMANHO_MENSAGEM),
//       nomeClienteContexto: normalizarTexto(clienteNome),
//       deveResponder: true,
//     };
//   }
//   if (!conteudo) {
//     return 'Não consegui ler sua mensagem. Pode enviar novamente?';
//   }

//   console.log('[bot-state] estado carregado', {
//     empresaId: identificadorTenant,
//     remoteJid: remoteJid || null,
//     etapa: estado?.fluxoAgendamento?.etapa || estado?.etapa || null,
//     updatedAt: estado?.updatedAt || null,
//   });

//   const agora = Date.now();
//   if (estado.pausadoPeloHumano?.ativo && agora < Number(estado.pausadoPeloHumano.ate || 0)) {
//     console.log('[Bot] ignorando mensagem: atendimento pausado (humano assumiu)', {
//       empresaId: identificadorTenant,
//       remoteJid: remoteJid || null,
//       pausadoAte: new Date(estado.pausadoPeloHumano.ate).toISOString(),
//     });
//     return {
//       respostaFinal: '',
//       intentFinal: 'PAUSADO_HUMANO',
//       contexto: null,
//       estado,
//       respostaDeterministica: null,
//       identificadorTenant,
//       chaveCliente,
//       chave,
//       conteudo,
//       nomeClienteContexto: normalizarTexto(clienteNome),
//       deveResponder: false,
//     };
//   }

//   if (estado.pausadoPeloHumano?.ativo && agora >= Number(estado.pausadoPeloHumano.ate || 0)) {
//     limparPausaAtendimentoHumano(estado);
//     console.log('[Bot] pausa de atendimento humano expirou, retomando bot', {
//       empresaId: identificadorTenant,
//       remoteJid: remoteJid || null,
//     });
//   }

//   const checagemRateLimit = verificarRateLimit(estado);
//   if (!checagemRateLimit.permitido) {
//     console.warn('[Bot] rate limit acionado', {
//       empresaId: identificadorTenant,
//       remoteJid: remoteJid || null,
//       motivo: checagemRateLimit.motivo,
//     });
//     if (checagemRateLimit.deveAvisar) {
//       const avisoRateLimit = 'Notei que você mandou várias mensagens muito rápido. Vou precisar de um instante para te responder com calma — me dá só um minutinho? 😊';
//       estado.updatedAt = Date.now();
//       return {
//         respostaFinal: avisoRateLimit,
//         intentFinal: 'RATE_LIMIT',
//         contexto: null,
//         estado,
//         respostaDeterministica: null,
//         identificadorTenant,
//         chaveCliente,
//         chave,
//         conteudo,
//         nomeClienteContexto: normalizarTexto(clienteNome),
//         deveResponder: true,
//         rateLimitAcionado: true,
//       };
//     }
//     estado.updatedAt = Date.now();
//     return {
//       respostaFinal: '',
//       intentFinal: 'RATE_LIMIT',
//       contexto: null,
//       estado,
//       respostaDeterministica: null,
//       identificadorTenant,
//       chaveCliente,
//       chave,
//       conteudo,
//       nomeClienteContexto: normalizarTexto(clienteNome),
//       deveResponder: false,
//       rateLimitAcionado: true,
//     };
//   }

//   console.log('[Bot] mensagem recebida com empresaId', {
//     empresaId: identificadorTenant,
//     remoteJid: remoteJid || null,
//     texto: conteudo,
//   });
//   console.log('[Bot-Debug] mensagem recebida', {
//     empresaId: identificadorTenant,
//     remoteJid: remoteJid || null,
//     texto: conteudo,
//   });

//   chave.messages.push({ role: 'user', content: conteudo });
//   chave.lastActivity = Date.now();
//   estado.lastMessage = conteudo;
//   estado.lastQuestion = conteudo;
//   estado.remoteJid = remoteJid || null;
//   estado.updatedAt = Date.now();

//   console.log('[Bot] carregando contexto da empresa', {
//     empresaId: identificadorTenant,
//     remoteJid: remoteJid || null,
//   });
//   let [contexto, fluxoPersistente, pausaManual, clienteNomeReal] = await Promise.all([
//     carregarContextoEmpresa(backendUrl, backendToken, identificadorTenant)
//       .catch(() => normalizarContextoWpp({})),
//     carregarFluxoPersistente(backendUrl, backendToken, identificadorTenant, chaveCliente)
//       .catch(() => null),
//     conversaPausada(backendUrl, backendToken, identificadorTenant, chaveCliente)
//       .catch(() => false),
//     buscarClienteNome(backendUrl, backendToken, identificadorTenant, chaveCliente)
//       .catch(() => ''),
//   ]);
//   const linkAgendamento = resolverLinkAgendamento(contexto);
//   const nomeClienteFluxo = normalizarTexto(estado.fluxoAgendamento?.clienteNome || estado.fluxoAgendamento?.nomeCliente);
//   const nomeClienteContexto = nomeClienteFluxo || clienteNomeReal || normalizarTexto(clienteNome);
//   const etapaFluxoAtual = estado?.fluxoAgendamento?.etapa || estado?.etapa || null;
//   const respostaEsperada = isRespostaEsperadaDaEtapa(estado, conteudo);
//   console.log('[Scheduling] estado carregado', {
//     empresaId: identificadorTenant,
//     remoteJid: remoteJid || null,
//     etapa: etapaFluxoAtual,
//     clienteNome: nomeClienteContexto || null,
//   });
//   console.log('[Scheduling] processando etapa', {
//     empresaId: identificadorTenant,
//     remoteJid: remoteJid || null,
//     etapa: etapaFluxoAtual,
//     texto: conteudo,
//   });
//   if (estado?.fluxoConfirmacaoPagamentoDono?.ativo && estado?.fluxoConfirmacaoPagamentoDono?.etapa === 'AGUARDANDO_RESPOSTA_PAGAMENTO_DONO') {
//     const respostaPagamentoDono = await conduzirFluxoConfirmacaoPagamentoDono({
//       entrada: conteudo,
//       estado,
//       empresaId: identificadorTenant,
//       remoteJid: remoteJid || null,
//       backendUrl,
//       backendToken,
//     });
//     if (respostaPagamentoDono) {
//       return {
//         respostaFinal: respostaPagamentoDono,
//         intentFinal: 'PAGAMENTO_DONO_FLUXO',
//         contexto,
//         estado,
//         respostaDeterministica: null,
//         identificadorTenant,
//         chaveCliente,
//         chave,
//         conteudo,
//         nomeClienteContexto,
//       };
//     }
//   }
//   if (estado?.fluxoAgendamento?.ativo && estado?.fluxoAgendamento?.tipoFluxo === 'AGENDAMENTO' && respostaEsperada) {
//     const respostaFluxoAgendamento = await conduzirFluxoAgendamento({
//       estado,
//       texto: conteudo,
//       contexto,
//       empresaId: identificadorTenant,
//       telefoneCliente: chaveCliente,
//       nomeCliente: nomeClienteContexto,
//       backendUrl,
//       backendToken,
//       groqApiKey: normalizarTexto(process.env.GROQ_API_KEY),
//       remoteJid: remoteJid || null,
//     });
//     if (respostaFluxoAgendamento) {
//       const etapaAnteriorFluxo = etapaFluxoAtual;
//       estado.updatedAt = Date.now();
//       if (estado.fluxoAgendamento) {
//         estado.fluxoAgendamento.updatedAt = Date.now();
//       }
//       await salvarFluxoPersistente(backendUrl, backendToken, {
//         empresaId: identificadorTenant,
//         telefoneCliente: chaveCliente,
//         remoteJid: remoteJid || null,
//         tipoFluxo: estado.fluxoAgendamento?.tipoFluxo || 'AGENDAMENTO',
//         etapa: estado.fluxoAgendamento?.etapa || null,
//         ativo: Boolean(estado.fluxoAgendamento?.ativo),
//         modoSelecionado: estado.fluxoAgendamento?.modoSelecionado || null,
//         payload: {
//           servicosDisponiveis: estado.fluxoAgendamento?.servicosDisponiveis || [],
//           servicoSelecionado: estado.fluxoAgendamento?.servicoSelecionado || null,
//           profissionaisDisponiveis: estado.fluxoAgendamento?.profissionaisDisponiveis || [],
//           profissionalSelecionado: estado.fluxoAgendamento?.profissionalSelecionado || null,
//           agendamentosFuturos: estado.fluxoAgendamento?.agendamentosFuturos || [],
//           agendamentoSelecionado: estado.fluxoAgendamento?.agendamentoSelecionado || null,
//           dataSelecionada: estado.fluxoAgendamento?.dataSelecionada || null,
//           horariosDisponiveis: estado.fluxoAgendamento?.horariosDisponiveis || [],
//           horarioSelecionado: estado.fluxoAgendamento?.horarioSelecionado || null,
//           clienteNome: estado.fluxoAgendamento?.clienteNome || estado.fluxoAgendamento?.nomeCliente || nomeClienteContexto || null,
//           nomeCliente: nomeClienteContexto || null,
//           ultimaPergunta: estado.fluxoAgendamento?.ultimaPergunta || null,
//         },
//         expiraEm: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
//       });
//       console.log('[Scheduling] nome recebido', {
//         empresaId: identificadorTenant,
//         remoteJid: remoteJid || null,
//         clienteNome: estado.fluxoAgendamento?.clienteNome || estado.fluxoAgendamento?.nomeCliente || null,
//       });
//       if (etapaAnteriorFluxo === 'AGUARDANDO_NOME') {
//         console.log('[Scheduling] servicos enviados', {
//           empresaId: identificadorTenant,
//           remoteJid: remoteJid || null,
//           totalServicos: contexto?.servicos?.length || 0,
//         });
//       }
//       return {
//         respostaFinal: sanitizarResposta(limitarParagrafos(respostaFluxoAgendamento), contexto, true),
//         intentFinal: 'AGENDAMENTO_FLUXO',
//         contexto,
//         estado,
//         respostaDeterministica: null,
//         identificadorTenant,
//         chaveCliente,
//         chave,
//         conteudo,
//         nomeClienteContexto,
//       };
//     }
//   }
//   const intencaoOriginal = detectarIntencao(conteudo, estado.lastIntent);
//   let intencao = intencaoOriginal === 'VAGO' ? (estado.lastIntent || 'SAUDACAO') : intencaoOriginal;
//   const intencaoFluxo = detectarIntencaoWpp2(conteudo);
//   if (intencaoFluxo !== 'OUTRO') {
//     intencao = intencaoFluxo;
//   }
//   console.log('[Intent] detectada', {
//     empresaId: identificadorTenant,
//     remoteJid: remoteJid || null,
//     texto: conteudo,
//     intent: intencao,
//     etapaAtual: etapaFluxoAtual,
//   });
//   console.log('[bot-fluxo] estado antes de processar', {
//     empresaId: identificadorTenant,
//     remoteJid: remoteJid || null,
//     etapa: etapaFluxoAtual,
//     texto: conteudo,
//   });
//   console.log('[bot-fluxo] resposta esperada da etapa?', {
//     empresaId: identificadorTenant,
//     remoteJid: remoteJid || null,
//     etapa: etapaFluxoAtual,
//     respostaEsperada,
//   });
//   const etapaAtual = estado?.fluxoAgendamento?.etapa || estado?.etapa || null;
//   const fluxoAtivo = Boolean(fluxoPersistente?.ativo || estado.fluxoAgendamento?.ativo || estado?.fluxoConfirmacaoPagamentoDono?.ativo);
//   const trocaDeAssunto = houveTrocaDeAssunto(estado, intencao);
//   if (trocaDeAssunto) {
//     console.log('[bot-state] troca de assunto', {
//       empresaId: identificadorTenant,
//       remoteJid: remoteJid || null,
//       etapaAnterior: etapaAtual,
//       intentAtual: intencao,
//     });
//     resetarFluxoAgendamento(estado);
//     await resetarFluxoPersistente(backendUrl, backendToken, identificadorTenant, chaveCliente);
//     limparEstadoConversa(identificadorTenant, remoteJid || chaveCliente, 'troca_de_assunto');
//     estado = garantirEstadoConversa(identificadorTenant, remoteJid || chaveCliente);
//     estado.updatedAt = Date.now();
//     fluxoPersistente = null;
//   }

//   console.log('[Bot] intencao detectada=', intencao);
//   console.log('[Bot] fluxo ativo=', fluxoAtivo);
//   console.log('[Bot] tipo fluxo=', fluxoPersistente?.tipoFluxo || estado.fluxoAgendamento?.tipoFluxo || null);
//   console.log('[Bot] etapa fluxo=', fluxoPersistente?.etapa || estado.fluxoAgendamento?.etapa || null);
//   console.log('[AI-Response] contexto carregado', {
//     empresaId: identificadorTenant,
//     nomeEmpresa: contexto?.nomeEmpresa || '',
//     totalServicos: contexto?.servicos?.length || 0,
//     totalDiasComHorario: contexto?.horariosDisponiveis?.length || 0,
//     temLinkAgendamento: Boolean(contexto?.linkAgendamento),
//     linkAgendamento,
//   });

//   if (pausaManual) {
//     console.log('[Bot-Service] conversa pausada manualmente, ignorando mensagem', {
//       empresaId: identificadorTenant,
//       remoteJid: remoteJid || null,
//     });
//     return {
//       respostaFinal: '',
//       intentFinal: 'PAUSADO_MANUAL',
//       contexto,
//       estado,
//       respostaDeterministica: null,
//       identificadorTenant,
//       chaveCliente,
//       chave,
//       conteudo,
//       nomeClienteContexto,
//       deveResponder: false,
//     };
//   }

//   if (estado?.fluxoAgendamento?.etapa === 'AGUARDANDO_ESCOLHA_CANAL_AGENDAMENTO') {
//     const escolha = detectarEscolhaCanalAgendamento(conteudo);
//     console.log('[Scheduling] escolha de canal detectada', {
//       empresaId: identificadorTenant,
//       remoteJid: remoteJid || null,
//       escolha,
//       etapaAnterior: estado?.fluxoAgendamento?.etapa || null,
//     });
//     if (escolha === 'WHATSAPP') {
//       estado.fluxoAgendamento.etapa = 'AGUARDANDO_NOME';
//       estado.fluxoAgendamento.updatedAt = Date.now();
//       estado.updatedAt = Date.now();
//       console.log('[Scheduling] avançando para nome', {
//         empresaId: identificadorTenant,
//         remoteJid: remoteJid || null,
//       });
//       return {
//         respostaFinal: 'Perfeito! Para começar, qual é o seu nome?',
//         intentFinal: 'AGENDAMENTO_FLUXO',
//         contexto,
//         estado,
//         respostaDeterministica: null,
//         identificadorTenant,
//         chaveCliente,
//         chave,
//         conteudo,
//         nomeClienteContexto,
//       };
//     }
//     if (escolha === 'LINK') {
//       resetarFluxoAgendamento(estado);
//       await resetarFluxoPersistente(backendUrl, backendToken, identificadorTenant, chaveCliente);
//       limparEstadoConversa(identificadorTenant, remoteJid || chaveCliente, 'troca_de_assunto');
//       return {
//         respostaFinal: linkAgendamento
//           ? `Perfeito! É só acessar:\n${linkAgendamento}\n\nPor lá você escolhe o serviço, o dia e o horário disponível.`
//           : 'Ainda não encontrei o link de agendamento configurado para esta empresa. Vou encaminhar para um atendente te ajudar.',
//         intentFinal: 'AGENDAMENTO_FLUXO',
//         contexto,
//         estado: garantirEstadoConversa(identificadorTenant, remoteJid || chaveCliente),
//         respostaDeterministica: null,
//         identificadorTenant,
//         chaveCliente,
//         chave,
//         conteudo,
//         nomeClienteContexto,
//       };
//     }
//     return {
//       respostaFinal: linkAgendamento
//         ? `Claro! Você pode agendar de duas formas:\n\n1. Pelo link, escolhendo serviço, dia e horário:\n${linkAgendamento}\n\n2. Ou, se preferir, posso te ajudar por aqui pelo WhatsApp.\n\nVocê prefere fazer pelo link ou por aqui?`
//         : 'Claro! Você pode agendar de duas formas:\n\n1. Pelo link oficial de agendamento.\n\n2. Ou, se preferir, posso te ajudar por aqui pelo WhatsApp.\n\nVocê prefere fazer pelo link ou por aqui?',
//       intentFinal: 'AGENDAMENTO_FLUXO',
//       contexto,
//       estado,
//       respostaDeterministica: null,
//       identificadorTenant,
//       chaveCliente,
//       chave,
//       conteudo,
//       nomeClienteContexto,
//     };
//   }

//   if (fluxoExpirado(estado)) {
//     console.log('[bot-state] estado expirado por 1h', {
//       empresaId: identificadorTenant,
//       remoteJid: remoteJid || null,
//       etapaAnterior: etapaAtual,
//     });
//     resetarFluxoAgendamento(estado);
//     await resetarFluxoPersistente(backendUrl, backendToken, identificadorTenant, chaveCliente);
//     limparEstadoConversa(identificadorTenant, remoteJid || chaveCliente, 'expirado_1h');
//     estado = garantirEstadoConversa(identificadorTenant, remoteJid || chaveCliente);
//     fluxoPersistente = null;
//   }

//   if (intencao === 'CANCELAMENTO' || intencao === 'CANCELAR') {
//     resetarFluxoAgendamento(estado);
//     await resetarFluxoPersistente(backendUrl, backendToken, identificadorTenant, chaveCliente);
//     estado.fluxoAgendamento = {
//       ativo: true,
//       tipoFluxo: 'CANCELAMENTO',
//       etapa: 'ESCOLHENDO_MODO',
//       modoSelecionado: null,
//       servicosDisponiveis: [],
//       servicoSelecionado: null,
//       dataSelecionada: null,
//       horariosDisponiveis: [],
//       horarioSelecionado: null,
//       nomeCliente: null,
//       ultimaPergunta: null,
//       agendamentoCancelamento: null,
//       updatedAt: Date.now(),
//     };
//     estado.updatedAt = Date.now();
//     fluxoPersistente = null;
//   }

//   if (intencao === 'REAGENDAMENTO' && !estado.fluxoAgendamento?.ativo) {
//     resetarFluxoAgendamento(estado);
//     estado.fluxoAgendamento = {
//       ativo: true,
//       tipoFluxo: 'REAGENDAMENTO',
//       etapa: 'ESCOLHENDO_MODO',
//       modoSelecionado: null,
//       servicosDisponiveis: [],
//       servicoSelecionado: null,
//       profissionaisDisponiveis: [],
//       profissionalSelecionado: null,
//       dataSelecionada: null,
//       horariosDisponiveis: [],
//       horarioSelecionado: null,
//       nomeCliente: null,
//       ultimaPergunta: null,
//       agendamentoCancelamento: null,
//       updatedAt: Date.now(),
//     };
//     estado.updatedAt = Date.now();
//     fluxoPersistente = null;
//   }

//   const aguardandoConfirmacao = (estado.fluxoAgendamento?.etapa || fluxoPersistente?.etapa) === 'AGUARDANDO_CONFIRMACAO';
//   if (aguardandoConfirmacao && ehConfirmacaoNegativa(conteudo)) {
//     resetarFluxoAgendamento(estado);
//     await resetarFluxoPersistente(backendUrl, backendToken, identificadorTenant, chaveCliente);
//     limparEstadoConversa(identificadorTenant, remoteJid || chaveCliente, 'cliente_respondeu_nao');
//     fluxoPersistente = null;
//     console.log('[bot-state] estado limpo', {
//       empresaId: identificadorTenant,
//       remoteJid: remoteJid || null,
//       motivo: 'cliente_respondeu_nao',
//     });
//     return {
//       respostaFinal: 'Sem problemas! Cancelei esse processo de agendamento por aqui. Se quiser tentar de novo, é só me chamar.',
//       intentFinal: 'CANCELAMENTO',
//       contexto,
//       estado: garantirEstadoConversa(identificadorTenant, remoteJid || chaveCliente),
//       respostaDeterministica: null,
//       identificadorTenant,
//       chaveCliente,
//       chave,
//       conteudo,
//       nomeClienteContexto,
//     };
//   }

//   if (aguardandoConfirmacao && intencao === 'HORARIOS') {
//     resetarFluxoAgendamento(estado);
//     await resetarFluxoPersistente(backendUrl, backendToken, identificadorTenant, chaveCliente);
//     limparEstadoConversa(identificadorTenant, remoteJid || chaveCliente, 'troca_de_assunto');
//     fluxoPersistente = null;
//     return {
//       respostaFinal: 'Claro! Me diga qual dia você prefere para eu ver outros horários disponíveis.',
//       intentFinal: 'HORARIOS',
//       contexto,
//       estado: garantirEstadoConversa(identificadorTenant, remoteJid || chaveCliente),
//       respostaDeterministica: null,
//       identificadorTenant,
//       chaveCliente,
//       chave,
//       conteudo,
//       nomeClienteContexto,
//     };
//   }

//   if (aguardandoConfirmacao && !ehConfirmacaoPositiva(conteudo) && isIntencaoForte(intencao)) {
//     resetarFluxoAgendamento(estado);
//     await resetarFluxoPersistente(backendUrl, backendToken, identificadorTenant, chaveCliente);
//     limparEstadoConversa(identificadorTenant, remoteJid || chaveCliente, 'troca_de_assunto');
//     estado = garantirEstadoConversa(identificadorTenant, remoteJid || chaveCliente);
//     fluxoPersistente = null;
//   }

//   if (fluxoPersistente?.ativo) {
//     estado.fluxoAgendamento = {
//       ...(estado.fluxoAgendamento || {}),
//       ativo: true,
//       tipoFluxo: fluxoPersistente.tipoFluxo || estado.fluxoAgendamento?.tipoFluxo || 'AGENDAMENTO',
//       etapa: fluxoPersistente.etapa || estado.fluxoAgendamento?.etapa || 'ESCOLHENDO_MODO',
//       modoSelecionado: fluxoPersistente.modoSelecionado || estado.fluxoAgendamento?.modoSelecionado || null,
//       clienteNome: fluxoPersistente.payload?.clienteNome || fluxoPersistente.payload?.nomeCliente || estado.fluxoAgendamento?.clienteNome || estado.fluxoAgendamento?.nomeCliente || null,
//       servicosDisponiveis: Array.isArray(fluxoPersistente.payload?.servicosDisponiveis) ? fluxoPersistente.payload.servicosDisponiveis : (estado.fluxoAgendamento?.servicosDisponiveis || []),
//       servicoSelecionado: fluxoPersistente.payload?.servicoSelecionado || estado.fluxoAgendamento?.servicoSelecionado || null,
//       profissionaisDisponiveis: Array.isArray(fluxoPersistente.payload?.profissionaisDisponiveis) ? fluxoPersistente.payload.profissionaisDisponiveis : (estado.fluxoAgendamento?.profissionaisDisponiveis || []),
//       profissionalSelecionado: fluxoPersistente.payload?.profissionalSelecionado || estado.fluxoAgendamento?.profissionalSelecionado || null,
//       dataSelecionada: fluxoPersistente.payload?.dataSelecionada || estado.fluxoAgendamento?.dataSelecionada || null,
//       horariosDisponiveis: Array.isArray(fluxoPersistente.payload?.horariosDisponiveis) ? fluxoPersistente.payload.horariosDisponiveis : (estado.fluxoAgendamento?.horariosDisponiveis || []),
//       horarioSelecionado: fluxoPersistente.payload?.horarioSelecionado || estado.fluxoAgendamento?.horarioSelecionado || null,
//       nomeCliente: fluxoPersistente.payload?.nomeCliente || estado.fluxoAgendamento?.nomeCliente || null,
//       ultimaPergunta: fluxoPersistente.payload?.ultimaPergunta || estado.fluxoAgendamento?.ultimaPergunta || null,
//     };
//   }

//   if (estado.fluxoAgendamento?.ativo) {
//     const tipoFluxo = estado.fluxoAgendamento?.tipoFluxo || 'AGENDAMENTO';
//     console.log('[Bot] fluxo ativo=', true);
//     console.log('[Bot] tipo fluxo=', tipoFluxo);
//     console.log('[Bot] etapa fluxo=', estado.fluxoAgendamento?.etapa || null);
//     let respostaFluxo = null;
//     if (tipoFluxo === 'AGENDAMENTO') {
//       respostaFluxo = await conduzirFluxoAgendamento({
//         estado,
//         texto: conteudo,
//         contexto,
//         empresaId: identificadorTenant,
//         telefoneCliente: chaveCliente,
//         nomeCliente: nomeClienteContexto,
//         backendUrl,
//         backendToken,
//         groqApiKey: normalizarTexto(process.env.GROQ_API_KEY),
//         remoteJid: remoteJid || null,
//       });
//     } else if (tipoFluxo === 'CANCELAMENTO') {
//       respostaFluxo = await conduzirFluxoCancelamento({
//         estado,
//         texto: conteudo,
//         contexto,
//         empresaId: identificadorTenant,
//         telefoneCliente: chaveCliente,
//         nomeCliente: nomeClienteContexto,
//         backendUrl,
//         backendToken,
//         remoteJid: remoteJid || null,
//       });
//     } else if (tipoFluxo === 'REAGENDAMENTO') {
//       respostaFluxo = await conduzirFluxoReagendamento({
//         estado,
//         texto: conteudo,
//         contexto,
//         empresaId: identificadorTenant,
//         telefoneCliente: chaveCliente,
//         nomeCliente: nomeClienteContexto,
//         backendUrl,
//         backendToken,
//         remoteJid: remoteJid || null,
//       });
//     }
//     if (respostaFluxo) {
//       await salvarFluxoPersistente(backendUrl, backendToken, {
//         empresaId: identificadorTenant,
//         telefoneCliente: chaveCliente,
//         remoteJid: remoteJid || null,
//         tipoFluxo,
//         etapa: estado.fluxoAgendamento?.etapa || null,
//         ativo: Boolean(estado.fluxoAgendamento?.ativo),
//         modoSelecionado: estado.fluxoAgendamento?.modoSelecionado || null,
//         payload: {
//           servicosDisponiveis: estado.fluxoAgendamento?.servicosDisponiveis || [],
//           servicoSelecionado: estado.fluxoAgendamento?.servicoSelecionado || null,
//           profissionaisDisponiveis: estado.fluxoAgendamento?.profissionaisDisponiveis || [],
//           profissionalSelecionado: estado.fluxoAgendamento?.profissionalSelecionado || null,
//           agendamentosFuturos: estado.fluxoAgendamento?.agendamentosFuturos || [],
//           agendamentoSelecionado: estado.fluxoAgendamento?.agendamentoSelecionado || null,
//           dataSelecionada: estado.fluxoAgendamento?.dataSelecionada || null,
//           horariosDisponiveis: estado.fluxoAgendamento?.horariosDisponiveis || [],
//           horarioSelecionado: estado.fluxoAgendamento?.horarioSelecionado || null,
//           clienteNome: estado.fluxoAgendamento?.clienteNome || estado.fluxoAgendamento?.nomeCliente || nomeClienteContexto || null,
//           nomeCliente: nomeClienteContexto || null,
//           ultimaPergunta: estado.fluxoAgendamento?.ultimaPergunta || null,
//         },
//         expiraEm: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
//       });
//       console.log('[Bot] RESPOSTA_FINAL=', respostaFluxo);
//       const intentFluxo = tipoFluxo === 'AGENDAMENTO'
//         ? 'AGENDAMENTO_FLUXO'
//         : tipoFluxo === 'CANCELAMENTO'
//           ? 'CANCELAMENTO_FLUXO'
//           : 'REAGENDAMENTO_FLUXO';
//       return {
//         respostaFinal: sanitizarResposta(limitarParagrafos(respostaFluxo), contexto, true),
//         intentFinal: intentFluxo,
//         contexto,
//         estado,
//         respostaDeterministica: null,
//         identificadorTenant,
//         chaveCliente,
//         chave,
//         conteudo,
//         nomeClienteContexto,
//       };
//     }
//   }

//   if (['AGENDAMENTO', 'HORARIOS', 'DISPONIBILIDADE', 'DIAS_DISPONIVEIS', 'CANCELAMENTO'].includes(intencao) && !estado.fluxoAgendamento?.ativo) {
//     const mensagens = {
//       AGENDAMENTO: linkAgendamento
//         ? `Claro! Você pode agendar de duas formas:\n\n1. Pelo link, escolhendo serviço, dia e horário:\n${linkAgendamento}\n\n2. Ou, se preferir, posso te ajudar por aqui pelo WhatsApp.\n\nVocê prefere fazer pelo link ou por aqui?`
//         : 'Claro! Você pode agendar de duas formas:\n\n1. Pelo link oficial de agendamento.\n\n2. Ou, se preferir, posso te ajudar por aqui pelo WhatsApp.\n\nVocê prefere fazer pelo link ou por aqui?',
//       HORARIOS: 'Claro! Você pode agendar de duas formas:\n\n1. Pelo link, escolhendo serviço, dia e horário.\n2. Ou, se preferir, posso te ajudar por aqui pelo WhatsApp.\n\nVocê prefere fazer pelo link ou por aqui?',
//       DISPONIBILIDADE: 'Claro! Você pode agendar de duas formas:\n\n1. Pelo link, escolhendo serviço, dia e horário.\n2. Ou, se preferir, posso te ajudar por aqui pelo WhatsApp.\n\nVocê prefere fazer pelo link ou por aqui?',
//       DIAS_DISPONIVEIS: 'Claro! Você pode agendar de duas formas:\n\n1. Pelo link, escolhendo serviço, dia e horário.\n2. Ou, se preferir, posso te ajudar por aqui pelo WhatsApp.\n\nVocê prefere fazer pelo link ou por aqui?',
//       CANCELAMENTO: 'Claro. Você prefere cancelar por aqui mesmo ou pelo link de gerenciamento?',
//     };
//     estado.fluxoAgendamento.ativo = true;
//     estado.fluxoAgendamento.tipoFluxo = ['HORARIOS', 'DISPONIBILIDADE', 'DIAS_DISPONIVEIS'].includes(intencao) ? 'AGENDAMENTO' : intencao;
//     estado.fluxoAgendamento.etapa = intencao === 'REAGENDAMENTO'
//       ? 'AGUARDANDO_ESCOLHA_CANAL_REAGENDAMENTO'
//       : 'AGUARDANDO_ESCOLHA_CANAL_AGENDAMENTO';
//     estado.fluxoAgendamento.modoSelecionado = null;
//     estado.fluxoAgendamento.updatedAt = Date.now();
//     console.log('[Scheduling] primeira oferta enviada', {
//       empresaId: identificadorTenant,
//       remoteJid: remoteJid || null,
//       linkAgendamento,
//     });
//     await salvarFluxoPersistente(backendUrl, backendToken, {
//       empresaId: identificadorTenant,
//       telefoneCliente: chaveCliente,
//       remoteJid: remoteJid || null,
//       tipoFluxo: ['HORARIOS', 'DISPONIBILIDADE', 'DIAS_DISPONIVEIS'].includes(intencao) ? 'AGENDAMENTO' : intencao,
//       etapa: intencao === 'REAGENDAMENTO'
//         ? 'AGUARDANDO_ESCOLHA_CANAL_REAGENDAMENTO'
//         : 'AGUARDANDO_ESCOLHA_CANAL_AGENDAMENTO',
//       ativo: true,
//       modoSelecionado: null,
//       payload: {
//         servicosDisponiveis: [],
//         servicoSelecionado: null,
//         agendamentosFuturos: [],
//         agendamentoSelecionado: null,
//         dataSelecionada: null,
//         horariosDisponiveis: [],
//         horarioSelecionado: null,
//         clienteNome: null,
//         nomeCliente: nomeClienteContexto || null,
//         ultimaPergunta: mensagens[intencao],
//       },
//       expiraEm: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
//     });
//     console.log(`[Bot] INICIANDO_FLUXO_${intencao}`);
//     console.log('[Bot] RESPOSTA_FINAL=', mensagens[intencao]);
//     return {
//       respostaFinal: mensagens[intencao],
//       intentFinal: `${intencao}_FLUXO`,
//       contexto,
//       estado,
//       respostaDeterministica: null,
//       identificadorTenant,
//       chaveCliente,
//       chave,
//       conteudo,
//       nomeClienteContexto,
//     };
//   }

//   const groqApiKey = normalizarTexto(process.env.GROQ_API_KEY);
//   let resposta = '';
//   let intentFinal = intencao;
//   let respostaDeterministica = null;
//   const exigeLinkOficial = ['AGENDAMENTO', 'HORARIOS', 'DISPONIBILIDADE', 'DIAS_DISPONIVEIS'].includes(intencao);

//   if (intencao === 'SAUDACAO') {
//     respostaDeterministica = responderPorIntencao({
//       intencao,
//       contexto,
//       estado,
//       texto: conteudo,
//       clienteNome: nomeClienteContexto,
//     });
//     resposta = respostaDeterministica.resposta;
//     intentFinal = respostaDeterministica.intent;
//   } else if (exigeLinkOficial) {
//     respostaDeterministica = responderPorIntencao({
//       intencao,
//       contexto,
//       estado,
//       texto: conteudo,
//       clienteNome: nomeClienteContexto,
//     });
//     resposta = respostaDeterministica.resposta;
//     intentFinal = respostaDeterministica.intent;
//   } else if (groqApiKey) {
//     try {
//       const groq = new Groq({ apiKey: groqApiKey });
//       const chat = await groq.chat.completions.create({
//         model: 'llama-3.1-8b-instant',
//         messages: [
//           { role: 'system', content: montarSystemPrompt(contexto, nomeClienteContexto, estado, intencao) },
//           ...chave.messages.slice(-8),
//         ],
//         temperature: 0.25,
//         max_tokens: 500,
//       });
//       resposta = chat.choices?.[0]?.message?.content || '';
//       intentFinal = intencao !== 'FALLBACK' ? intencao : (respostaIndicaAgendamento(resposta) ? 'AGENDAMENTO' : 'FALLBACK');
//     } catch (error) {
//       console.warn('[AI-Response] falha no Groq, usando roteador determinístico:', {
//         empresaId: identificadorTenant,
//         remoteJid: remoteJid || null,
//         detalhe: error.message,
//       });
//       respostaDeterministica = responderPorIntencao({
//         intencao,
//         contexto,
//         estado,
//         texto: conteudo,
//         clienteNome: nomeClienteContexto,
//       });
//       resposta = respostaDeterministica.resposta;
//       intentFinal = respostaDeterministica.intent;
//     }
//   } else {
//     respostaDeterministica = responderPorIntencao({
//       intencao,
//       contexto,
//       estado,
//       texto: conteudo,
//       clienteNome: nomeClienteContexto,
//     });
//     resposta = respostaDeterministica.resposta;
//     intentFinal = respostaDeterministica.intent;
//   }

//   const respostaFinal = sanitizarResposta(
//     limitarParagrafos(resposta || contexto.respostaNaoEntende || 'Desculpa, não entendi muito bem. Pode me explicar de outra forma?'),
//     contexto,
//     fluxoAtivo,
//   );
//   if (['HORARIOS', 'DISPONIBILIDADE', 'DIAS_DISPONIVEIS'].includes(intencao) && !contexto?.linkAgendamento) {
//     console.log('[Bot] CAIU_NO_FLUXO_ANTIGO_LINK');
//   }

//   console.log('[AI-Response] resposta final', {
//     empresaId: identificadorTenant,
//     remoteJid: remoteJid || null,
//     intent: intentFinal,
//     tamanhoResposta: respostaFinal.length,
//     usouServicos: Boolean(normalizarServicos(contexto?.servicos).length),
//     usouHorarios: Boolean(normalizarHorariosDisponiveis(contexto?.horariosDisponiveis).length),
//     usouLink: Boolean(contexto?.linkAgendamento),
//     usouIA: intencao === 'FALLBACK' && Boolean(contexto.assistenteAtivo && groqApiKey),
//   });
//   console.log('[Bot] RESPOSTA_FINAL=', respostaFinal);

//   return {
//     respostaFinal,
//     intentFinal,
//     contexto,
//     estado,
//     respostaDeterministica,
//     identificadorTenant,
//     chaveCliente,
//     chave,
//     conteudo,
//     nomeClienteContexto,
//   };
// }

// async function processarMensagem(params) {
//   const {
//     respostaFinal,
//     intentFinal,
//     contexto,
//     estado,
//     respostaDeterministica,
//     identificadorTenant,
//     chaveCliente,
//     chave,
//     deveResponder = true,
//   } = await responderCliente(params);

//   if (!deveResponder) {
//     estado.updatedAt = Date.now();
//     return '';
//   }

//   estado.lastIntent = intentFinal;
//   if (intentFinal === 'SERVICOS') {
//     estado.lastService = respostaDeterministica?.servico || estado.lastService || null;
//   }
//   if (intentFinal === 'AGENDAMENTO') {
//     estado.lastDate = estado.lastDate || null;
//   }
//   chave.messages.push({ role: 'assistant', content: respostaFinal });
//   chave.lastActivity = Date.now();
//   estado.lastIntent = respostaIndicaAgendamento(respostaFinal) ? 'AGENDAMENTO' : estado.lastIntent || 'FALLBACK';
//   estado.updatedAt = Date.now();

//   if (respostaIndicaAgendamento(respostaFinal)) {
//     await chamadaBackendComRetry({
//       method: 'post',
//       url: `${params.backendUrl}/api/internal/whatsapp/agendamento-ia`,
//       data: { empresaId: identificadorTenant, tenantId: identificadorTenant, clientePhone: chaveCliente, texto: respostaFinal },
//       headers: params.backendToken ? { 'X-Internal-Token': params.backendToken } : {},
//     }, { timeoutMs: 8000, maxTentativas: 2 });
//   }

//   if (estado.finalizouFluxoAgendamento) {
//     conversationState.delete(conversaKey(identificadorTenant, params.remoteJid || chaveCliente));
//     console.log('[bot-state] estado limpo', {
//       empresaId: identificadorTenant,
//       remoteJid: params.remoteJid || null,
//       motivo: estado.finalizouFluxoAgendamento,
//     });
//     estado.finalizouFluxoAgendamento = null;
//   }

//   console.log('[Bot-Service] resposta enviada pelo bot', {
//     empresaId: identificadorTenant,
//     remoteJid: params.remoteJid || null,
//     intent: estado.lastIntent || 'FALLBACK',
//   });

//   return respostaFinal;
// }

// module.exports = {
//   processarMensagem,
//   limparHistoricos,
//   limparConversasExpiradas,
//   marcarPausaAtendimentoHumano,
//   registrarConfirmacaoPagamentoDono,
//   history,
//   conversationState,
// };


module.exports = {
  processarMensagem: async () => null,
  limparHistoricos: async () => null,
  limparConversasExpiradas: async () => null,
  marcarPausaAtendimentoHumano: async () => null,
  registrarConfirmacaoPagamentoDono: async () => null,
  history: new Map(),
  conversationState: new Map(),
};
