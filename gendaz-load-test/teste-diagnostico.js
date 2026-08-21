import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '15s', target: 20 },
    { duration: '30s', target: 20 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.10'],
  },
};

const BASE_URL = __ENV.GENDAZ_BASE_URL || 'https://stage.gendaz.site';
const EMPRESA_ID = Number(__ENV.GENDAZ_EMPRESA_ID || '1');
const EMPRESA_SLUG = __ENV.GENDAZ_EMPRESA_SLUG || String(EMPRESA_ID);

const USERS = [
  { email: __ENV.GENDAZ_EMAIL_1, senha: __ENV.GENDAZ_PASSWORD_1 },
  { email: __ENV.GENDAZ_EMAIL_2, senha: __ENV.GENDAZ_PASSWORD_2 },
];

let inicializado = false;
let autenticado = false;

let clienteId = null;
let servicoId = null;
let profissionalId = null;
let publicServicoId = null;
let publicProfissionalId = null;

function headersJson() {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    Origin: BASE_URL,
    Referer: `${BASE_URL}/`,
  };
}

function isHtml(response) {
  return String(response.body || '').toLowerCase().includes('<!doctype html>');
}

function logResposta(nome, response) {
  console.log(
    `${nome}: status=${response.status} ct=${response.headers['Content-Type'] || ''} html=${isHtml(response)}`
  );
}

function getCsrf() {
  const response = http.get(`${BASE_URL}/api/auth/csrf`, {
    headers: { Accept: 'application/json', Origin: BASE_URL },
  });
  logResposta('CSRF', response);

  if (response.status !== 200 || isHtml(response)) return null;

  let token = null;
  try {
    token = response.json('token');
  } catch (e) {
    return null;
  }

  if (!token) return null;

  http.cookieJar().set(BASE_URL, 'XSRF-TOKEN', token, {
    path: '/',
    secure: true,
  });

  return token;
}

function login() {
  const user = USERS[(__VU - 1) % USERS.length];
  if (!user.email || !user.senha) return false;

  const csrf = getCsrf();
  if (!csrf) return false;

  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: user.email, senha: user.senha }),
    { headers: { ...headersJson(), 'X-XSRF-TOKEN': csrf } }
  );
  logResposta('LOGIN', response);

  return response.status === 200 && !isHtml(response);
}

function getJson(url, name) {
  const response = http.get(url, { headers: headersJson() });
  logResposta(name, response);
  return response;
}

function loadPublic() {
  const response = getJson(`${BASE_URL}/api/agendamento-publico/${EMPRESA_SLUG}`, 'PUBLICO_EMPRESA');
  if (response.status !== 200 || isHtml(response)) return;

  try {
    const data = response.json();
    const servicos = data?.servicos || [];
    const profissionais = data?.profissionais || [];
    if (servicos.length) publicServicoId = servicos[0].id;
    if (profissionais.length) publicProfissionalId = profissionais[0].id;
  } catch (e) {}
}

function loadPrivate() {
  if (!autenticado) return;

  const clientes = getJson(`${BASE_URL}/api/clientes/empresa/${EMPRESA_ID}`, 'CLIENTES');
  const servicos = getJson(`${BASE_URL}/api/servicos/empresa/${EMPRESA_ID}`, 'SERVICOS');
  const profissionais = getJson(`${BASE_URL}/api/profissionais/empresa/${EMPRESA_ID}`, 'PROFISSIONAIS');
  const agenda = getJson(`${BASE_URL}/api/agendamentos/empresa/${EMPRESA_ID}`, 'AGENDA_PRIVADA');

  try {
    const x = clientes.json();
    if (Array.isArray(x) && x.length) clienteId = x[0].id;
  } catch (e) {}
  try {
    const x = servicos.json();
    if (Array.isArray(x) && x.length) servicoId = x[0].id;
  } catch (e) {}
  try {
    const x = profissionais.json();
    if (Array.isArray(x) && x.length) profissionalId = x[0].id;
  } catch (e) {}

  void agenda;
}

function horariosPublicos() {
  if (!publicServicoId) return;
  const data = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  const url =
    `${BASE_URL}/api/agendamento-publico/${EMPRESA_SLUG}/horarios?servicoId=${publicServicoId}&data=${data}` +
    (publicProfissionalId ? `&profissionalId=${publicProfissionalId}` : '');
  getJson(url, 'HORARIOS_PUBLICOS');
}

function horariosPrivados() {
  if (!autenticado || !servicoId || !profissionalId) return;
  const data = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  getJson(
    `${BASE_URL}/api/agendamentos/horarios-disponiveis?empresaId=${EMPRESA_ID}&profissionalId=${profissionalId}&servicoId=${servicoId}&data=${data}`,
    'HORARIOS_PRIVADOS'
  );
}

function criarAgendamentoPrivado() {
  if (!autenticado || !clienteId || !servicoId || !profissionalId) return;

  const csrf = getCsrf();
  if (!csrf) return;

  const response = http.post(
    `${BASE_URL}/api/agendamentos`,
    JSON.stringify({
      clienteId,
      servicoId,
      profissionalId,
      empresaId: EMPRESA_ID,
      data: new Date(Date.now() + 86400000).toISOString().slice(0, 10),
      horaInicio: '09:00',
      cupomCodigo: null,
      observacoes: 'TESTE DE CARGA - DIAGNOSTICO',
    }),
    { headers: { ...headersJson(), 'X-XSRF-TOKEN': csrf } }
  );
  logResposta('CRIAR_AGENDAMENTO', response);
}

function criarAgendamentoPublico() {
  if (!publicServicoId) return;

  const telefone = `65999${String(__VU).padStart(3, '0')}${String(__ITER).padStart(2, '0')}`;
  const response = http.post(
    `${BASE_URL}/api/agendamento-publico/${EMPRESA_SLUG}/agendar`,
    JSON.stringify({
      servicoId: publicServicoId,
      profissionalId: publicProfissionalId,
      data: new Date(Date.now() + 86400000).toISOString().slice(0, 10),
      horaInicio: '09:00',
      cupomCodigo: null,
      clienteNome: `Teste VU ${__VU}`,
      clienteTelefone: telefone,
      clienteEmail: `teste.vu${__VU}@example.com`,
      observacao: 'TESTE DE CARGA - DIAGNOSTICO',
    }),
    { headers: headersJson() }
  );
  logResposta('AGENDAR_PUBLICO', response);
}

export default function () {
  if (!inicializado) {
    logResposta('HEALTH', http.get(`${BASE_URL}/api/health`, { headers: { Accept: 'application/json' } }));
    loadPublic();
    autenticado = login();
    if (autenticado) loadPrivate();
    inicializado = true;
  }

  loadPublic();
  horariosPublicos();

  if (autenticado) {
    loadPrivate();
    horariosPrivados();
  }

  if (__ITER === 0) {
    criarAgendamentoPrivado();
    criarAgendamentoPublico();
  }

  sleep(1);
}
