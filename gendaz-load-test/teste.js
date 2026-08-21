import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 100 },
    { duration: '1m', target: 100 },

    { duration: '30s', target: 200 },
    { duration: '1m', target: 200 },

    { duration: '30s', target: 300 },
    { duration: '1m', target: 300 },

    { duration: '30s', target: 400 },
    { duration: '1m', target: 400 },

    { duration: '30s', target: 500 },
    { duration: '2m', target: 500 },

    { duration: '30s', target: 0 },
  ],

  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.05'],
  },
};

const BASE_URL = 'https://stage.gendaz.site';

const EMPRESA_ID = Number(__ENV.GENDAZ_EMPRESA_ID || '1');
const EMPRESA_SLUG = __ENV.GENDAZ_EMPRESA_SLUG || String(EMPRESA_ID);

const USERS = [
  {
    email: __ENV.GENDAZ_EMAIL_1,
    senha: __ENV.GENDAZ_PASSWORD_1,
  },
  {
    email: __ENV.GENDAZ_EMAIL_2,
    senha: __ENV.GENDAZ_PASSWORD_2,
  },
];

// Cada VU mantém seus próprios dados entre as iterações.
let sessaoInicializada = false;
let autenticado = false;

let clienteId = null;
let servicoId = null;
let profissionalId = null;

let publicServicoId = null;
let publicProfissionalId = null;

let publicData = null;
let privateData = null;

function jsonHeaders() {
  return {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Origin': 'https://stage.gendaz.site',
    'Referer': 'https://stage.gendaz.site/',
  };
}

function isHtml(response) {
  return (response.body || '')
    .toLowerCase()
    .includes('<!doctype html>');
}

function checkApiResponse(response, name) {
  return check(response, {
    [`${name} status 200`]: (r) => r.status === 200,
    [`${name} não retornou HTML`]: (r) => !isHtml(r),
  });
}

function getCsrfToken() {
  const response = http.get(
    `${BASE_URL}/api/auth/csrf`,
    {
      headers: {
        Accept: 'application/json',
        Origin: 'https://stage.gendaz.site',
      },
    }
  );

  const ok = check(response, {
    'CSRF status 200': (r) => r.status === 200,
    'CSRF retornou JSON': (r) =>
      !isHtml(r) &&
      String(r.headers['Content-Type'] || '')
        .toLowerCase()
        .includes('application/json'),
  });

  if (!ok) {
    return null;
  }

  let token = null;

  try {
    token = response.json('token');
  } catch (e) {
    console.error('Não foi possível ler o token CSRF');
    return null;
  }

  if (!token) {
    console.error('Backend respondeu sem token CSRF');
    return null;
  }

  // Garante o cookie que o Spring espera.
  const jar = http.cookieJar();

  jar.set(
    BASE_URL,
    'XSRF-TOKEN',
    token,
    {
      path: '/',
      secure: true,
    }
  );

  return token;
}

function login() {
  const user = USERS[(__VU - 1) % USERS.length];

  if (!user.email || !user.senha) {
    console.error('Credenciais não configuradas para o VU');
    return false;
  }

  const csrfToken = getCsrfToken();

  if (!csrfToken) {
    return false;
  }

  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({
      email: user.email,
      senha: user.senha,
    }),
    {
      headers: {
        ...jsonHeaders(),
        'X-XSRF-TOKEN': csrfToken,
      },
    }
  );

  const ok = check(response, {
    'LOGIN status 200': (r) => r.status === 200,
    'LOGIN não retornou HTML': (r) => !isHtml(r),
  });

  if (!ok) {
    console.error(
      `Login falhou. VU=${__VU} status=${response.status}`
    );
    return false;
  }

  return true;
}

function carregarDadosPublicos() {
  const response = http.get(
    `${BASE_URL}/api/agendamento-publico/${EMPRESA_SLUG}`,
    {
      headers: {
        Accept: 'application/json',
        Origin: 'https://stage.gendaz.site',
      },
    }
  );

  if (!checkApiResponse(response, 'PUBLICO_EMPRESA')) {
    return;
  }

  try {
    publicData = response.json();

    const servicos = publicData?.servicos || [];
    const profissionais = publicData?.profissionais || [];

    if (servicos.length > 0) {
      publicServicoId = servicos[0].id;
    }

    if (profissionais.length > 0) {
      publicProfissionalId = profissionais[0].id;
    }
  } catch (e) {
    console.error('Erro lendo resposta pública');
  }
}

function carregarDadosPrivados() {
  if (!autenticado) {
    return;
  }

  const responses = http.batch([
    {
      method: 'GET',
      url: `${BASE_URL}/api/clientes/empresa/${EMPRESA_ID}`,
      params: {
        headers: jsonHeaders(),
      },
    },
    {
      method: 'GET',
      url: `${BASE_URL}/api/servicos/empresa/${EMPRESA_ID}`,
      params: {
        headers: jsonHeaders(),
      },
    },
    {
      method: 'GET',
      url: `${BASE_URL}/api/profissionais/empresa/${EMPRESA_ID}`,
      params: {
        headers: jsonHeaders(),
      },
    },
  ]);

  const clientes = responses[0];
  const servicos = responses[1];
  const profissionais = responses[2];

  checkApiResponse(clientes, 'CLIENTES');
  checkApiResponse(servicos, 'SERVICOS');
  checkApiResponse(profissionais, 'PROFISSIONAIS');

  try {
    const listaClientes = clientes.json();
    if (Array.isArray(listaClientes) && listaClientes.length > 0) {
      clienteId = listaClientes[0].id;
    }
  } catch (e) {}

  try {
    const listaServicos = servicos.json();
    if (Array.isArray(listaServicos) && listaServicos.length > 0) {
      servicoId = listaServicos[0].id;
    }
  } catch (e) {}

  try {
    const listaProfissionais = profissionais.json();
    if (Array.isArray(listaProfissionais) && listaProfissionais.length > 0) {
      profissionalId = listaProfissionais[0].id;
    }
  } catch (e) {}
}

function testarAgendaPrivada() {
  if (!autenticado) {
    return;
  }

  const response = http.get(
    `${BASE_URL}/api/agendamentos/empresa/${EMPRESA_ID}`,
    {
      headers: jsonHeaders(),
    }
  );

  checkApiResponse(response, 'AGENDA_PRIVADA');
}

function testarClientes() {
  if (!autenticado) {
    return;
  }

  const response = http.get(
    `${BASE_URL}/api/clientes/empresa/${EMPRESA_ID}`,
    {
      headers: jsonHeaders(),
    }
  );

  checkApiResponse(response, 'CLIENTES');
}

function testarServicos() {
  if (!autenticado) {
    return;
  }

  const response = http.get(
    `${BASE_URL}/api/servicos/empresa/${EMPRESA_ID}`,
    {
      headers: jsonHeaders(),
    }
  );

  checkApiResponse(response, 'SERVICOS');
}

function testarProfissionais() {
  if (!autenticado) {
    return;
  }

  const response = http.get(
    `${BASE_URL}/api/profissionais/empresa/${EMPRESA_ID}`,
    {
      headers: jsonHeaders(),
    }
  );

  checkApiResponse(response, 'PROFISSIONAIS');
}

function amanha() {
  const data = new Date(Date.now() + 24 * 60 * 60 * 1000);
  return data.toISOString().slice(0, 10);
}

function testarHorariosPublicos() {
  if (!publicServicoId) {
    return;
  }

  const data = amanha();

  let url =
    `${BASE_URL}/api/agendamento-publico/${EMPRESA_SLUG}/horarios` +
    `?servicoId=${publicServicoId}` +
    `&data=${data}`;

  if (publicProfissionalId) {
    url += `&profissionalId=${publicProfissionalId}`;
  }

  const response = http.get(url, {
    headers: {
      Accept: 'application/json',
      Origin: 'https://stage.gendaz.site',
    },
  });

  checkApiResponse(response, 'HORARIOS_PUBLICOS');
}

function testarHorariosPrivados() {
  if (
    !autenticado ||
    !servicoId ||
    !profissionalId
  ) {
    return;
  }

  const data = amanha();

  const response = http.get(
    `${BASE_URL}/api/agendamentos/horarios-disponiveis` +
    `?empresaId=${EMPRESA_ID}` +
    `&profissionalId=${profissionalId}` +
    `&servicoId=${servicoId}` +
    `&data=${data}`,
    {
      headers: jsonHeaders(),
    }
  );

  checkApiResponse(response, 'HORARIOS_PRIVADOS');
}

function criarAgendamentoPrivado() {
  if (
    !autenticado ||
    !clienteId ||
    !servicoId ||
    !profissionalId
  ) {
    return;
  }

  const csrfToken = getCsrfToken();

  if (!csrfToken) {
    return;
  }

  const response = http.post(
    `${BASE_URL}/api/agendamentos`,
    JSON.stringify({
      clienteId: clienteId,
      servicoId: servicoId,
      profissionalId: profissionalId,
      empresaId: EMPRESA_ID,
      data: amanha(),
      horaInicio: '09:00',
      cupomCodigo: null,
      observacoes: 'TESTE DE CARGA - STAGE',
    }),
    {
      headers: {
        ...jsonHeaders(),
        'X-XSRF-TOKEN': csrfToken,
      },
    }
  );

  check(response, {
    'AGENDAMENTO status válido': (r) =>
      r.status === 200 ||
      r.status === 409 ||
      r.status === 400,

    'AGENDAMENTO não retornou HTML': (r) =>
      !isHtml(r),
  });
}

function criarAgendamentoPublico() {
  if (!publicServicoId) {
    return;
  }

  const csrfPhone = String(
    `65999${String(__VU).padStart(3, '0')}${String(__ITER).padStart(2, '0')}`
  );

  const response = http.post(
    `${BASE_URL}/api/agendamento-publico/${EMPRESA_SLUG}/agendar`,
    JSON.stringify({
      servicoId: publicServicoId,
      profissionalId: publicProfissionalId,
      data: amanha(),
      horaInicio: '09:00',
      cupomCodigo: null,

      clienteNome: `Teste VU ${__VU}`,
      clienteTelefone: csrfPhone,
      clienteEmail: `teste.vu${__VU}@example.com`,
      observacao: 'TESTE DE CARGA - STAGE',
    }),
    {
      headers: {
        ...jsonHeaders(),
      },
    }
  );

  check(response, {
    'AGENDAMENTO PUBLICO resposta válida': (r) =>
      r.status === 200 ||
      r.status === 400 ||
      r.status === 409 ||
      r.status === 429,

    'AGENDAMENTO PUBLICO não retornou HTML': (r) =>
      !isHtml(r),
  });
}

export default function () {

  // Inicialização de cada usuário virtual.
  if (!sessaoInicializada) {

    // Health
    const health = http.get(
      `${BASE_URL}/api/health`,
      {
        headers: {
          Accept: 'application/json',
        },
      }
    );

    checkApiResponse(health, 'HEALTH');

    // Público
    carregarDadosPublicos();

    // Login
    autenticado = login();

    // Dados autenticados
    if (autenticado) {
      carregarDadosPrivados();
    }

    sessaoInicializada = true;
  }

  // ==============================
  // FLUXO PÚBLICO
  // ==============================

  carregarDadosPublicos();

  testarHorariosPublicos();

  // ==============================
  // FLUXO AUTENTICADO
  // ==============================

  if (autenticado) {
    carregarDadosPrivados();

    testarAgendaPrivada();
    testarClientes();
    testarServicos();
    testarProfissionais();
    testarHorariosPrivados();
  }

  // ==============================
  // ESCRITAS
  // ==============================
  //
  // SOMENTE NA PRIMEIRA ITERAÇÃO
  // de cada VU para não criar milhares
  // de registros no STAGE.
  //
  if (__ITER === 0) {
    criarAgendamentoPrivado();
    criarAgendamentoPublico();
  }

  sleep(1);
}
