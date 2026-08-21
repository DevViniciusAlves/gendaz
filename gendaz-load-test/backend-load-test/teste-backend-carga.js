import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 100 },
    { duration: '2m', target: 100 },
    { duration: '1m', target: 200 },
    { duration: '2m', target: 200 },
    { duration: '1m', target: 300 },
    { duration: '2m', target: 300 },
    { duration: '1m', target: 400 },
    { duration: '2m', target: 400 },
    { duration: '1m', target: 500 },
    { duration: '3m', target: 500 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000'],
  },
};

const BASE_URL = __ENV.GENDAZ_BASE_URL || 'https://stage.gendaz.site';
const EMPRESA_ID = Number(__ENV.GENDAZ_EMPRESA_ID || '1');
const EMPRESA_SLUG = __ENV.GENDAZ_EMPRESA_SLUG || 'gendaz-pro';

const USERS = [
  { email: __ENV.GENDAZ_EMAIL_1, senha: __ENV.GENDAZ_PASSWORD_1 },
  { email: __ENV.GENDAZ_EMAIL_2, senha: __ENV.GENDAZ_PASSWORD_2 },
];

let sessaoInicializada = false;
let autenticado = false;
let csrfToken = null;

let clienteId = null;
let servicoId = null;
let profissionalId = null;

function jsonHeaders() {
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

function checkApi(response, name, allowedStatuses = [200]) {
  return check(response, {
    [`${name} status ok`]: (r) => allowedStatuses.includes(r.status),
    [`${name} não retornou HTML`]: (r) => !isHtml(r),
  });
}

function getCsrfToken() {
  const response = http.get(`${BASE_URL}/api/auth/csrf`, {
    headers: { Accept: 'application/json', Origin: BASE_URL },
  });

  if (!checkApi(response, 'CSRF')) {
    return null;
  }

  try {
    const token = response.json('token');
    if (!token) return null;

    http.cookieJar().set(BASE_URL, 'XSRF-TOKEN', token, {
      path: '/',
      secure: true,
    });
    return token;
  } catch (e) {
    return null;
  }
}

function login() {
  const user = USERS[(__VU - 1) % USERS.length];
  if (!user.email || !user.senha) return false;

  csrfToken = getCsrfToken();
  if (!csrfToken) return false;

  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: user.email, senha: user.senha }),
    {
      headers: {
        ...jsonHeaders(),
        'X-XSRF-TOKEN': csrfToken,
      },
    }
  );

  return checkApi(response, 'LOGIN');
}

function amanha() {
  return new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

function carregarDadosBase() {
  const clientes = http.get(`${BASE_URL}/api/clientes/empresa/${EMPRESA_ID}`, {
    headers: jsonHeaders(),
  });
  const servicos = http.get(`${BASE_URL}/api/servicos/empresa/${EMPRESA_ID}`, {
    headers: jsonHeaders(),
  });
  const profissionais = http.get(`${BASE_URL}/api/profissionais/empresa/${EMPRESA_ID}`, {
    headers: jsonHeaders(),
  });
  const agenda = http.get(`${BASE_URL}/api/agendamentos/empresa/${EMPRESA_ID}`, {
    headers: jsonHeaders(),
  });

  checkApi(clientes, 'CLIENTES');
  checkApi(servicos, 'SERVICOS');
  checkApi(profissionais, 'PROFISSIONAIS');
  checkApi(agenda, 'AGENDA');

  try {
    const lista = clientes.json();
    if (Array.isArray(lista) && lista.length) clienteId = lista[0].id;
  } catch (e) {}

  try {
    const lista = servicos.json();
    if (Array.isArray(lista) && lista.length) servicoId = lista[0].id;
  } catch (e) {}

  try {
    const lista = profissionais.json();
    if (Array.isArray(lista) && lista.length) profissionalId = lista[0].id;
  } catch (e) {}
}

function criarCliente() {
  const response = http.post(
    `${BASE_URL}/api/clientes`,
    JSON.stringify({
      nome: `Cliente VU ${__VU} ITER ${__ITER}`,
      telefone: `+5565999${String(__VU).padStart(3, '0')}${String(__ITER).padStart(2, '0')}`,
      email: `cliente.vu${__VU}.it${__ITER}@example.com`,
      observacoes: 'TESTE DE CARGA',
      empresaId: EMPRESA_ID,
    }),
    {
      headers: {
        ...jsonHeaders(),
        'X-XSRF-TOKEN': csrfToken || getCsrfToken(),
      },
    }
  );

  check(response, {
    'CRIAR_CLIENTE status ok': (r) => r.status === 200 || r.status === 400 || r.status === 409,
    'CRIAR_CLIENTE não retornou HTML': (r) => !isHtml(r),
  });

  if (response.status === 200) {
    try {
      const data = response.json();
      if (data?.id) clienteId = data.id;
    } catch (e) {}
  }
}

function criarAgendamento() {
  if (!clienteId || !servicoId) return;

  const response = http.post(
    `${BASE_URL}/api/agendamentos`,
    JSON.stringify({
      clienteId,
      servicoId,
      profissionalId,
      empresaId: EMPRESA_ID,
      data: amanha(),
      horaInicio: '09:00',
      cupomCodigo: null,
      observacoes: 'TESTE DE CARGA BACKEND',
    }),
    {
      headers: {
        ...jsonHeaders(),
        'X-XSRF-TOKEN': csrfToken || getCsrfToken(),
      },
    }
  );

  check(response, {
    'CRIAR_AGENDAMENTO status ok': (r) => r.status === 200 || r.status === 400 || r.status === 409,
    'CRIAR_AGENDAMENTO não retornou HTML': (r) => !isHtml(r),
  });
}

function testarRotasLeitura() {
  http.get(`${BASE_URL}/api/health`, { headers: { Accept: 'application/json' } });
  http.get(`${BASE_URL}/api/agendamentos/empresa/${EMPRESA_ID}`, { headers: jsonHeaders() });
  http.get(`${BASE_URL}/api/agendamentos/data?empresaId=${EMPRESA_ID}&data=${amanha()}`, { headers: jsonHeaders() });
  http.get(
    `${BASE_URL}/api/agendamentos/horarios-disponiveis?empresaId=${EMPRESA_ID}&profissionalId=${profissionalId || 1}&servicoId=${servicoId || 1}&data=${amanha()}`,
    { headers: jsonHeaders() }
  );
  http.get(`${BASE_URL}/api/clientes/empresa/${EMPRESA_ID}`, { headers: jsonHeaders() });
  http.get(`${BASE_URL}/api/servicos/empresa/${EMPRESA_ID}`, { headers: jsonHeaders() });
  http.get(`${BASE_URL}/api/profissionais/empresa/${EMPRESA_ID}`, { headers: jsonHeaders() });
}

export default function () {
  if (!sessaoInicializada) {
    autenticado = login();
    if (autenticado) {
      carregarDadosBase();
    }
    sessaoInicializada = true;
  }

  testarRotasLeitura();

  if (autenticado) {
    if (__ITER % 3 === 0) {
      criarCliente();
      carregarDadosBase();
    }

    if (__ITER % 2 === 0) {
      criarAgendamento();
    }
  }

  sleep(1);
}
