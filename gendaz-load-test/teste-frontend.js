import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 10 },
    { duration: '20s', target: 10 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000'],
  },
};

const BASE_URL = __ENV.GENDAZ_BASE_URL || 'https://stage.gendaz.site';
const SLUG = __ENV.GENDAZ_SLUG || __ENV.GENDAZ_EMPRESA_SLUG || 'gendaz-pro';

function isHtml(response) {
  return String(response.body || '').toLowerCase().includes('<!doctype html>');
}

function checkPage(response, name) {
  return check(response, {
    [`${name} status 200`]: (r) => r.status === 200,
    [`${name} retornou HTML`]: (r) => isHtml(r),
  });
}

export default function () {
  const sistema = http.get(`${BASE_URL}/sistema`, {
    headers: { Accept: 'text/html,application/xhtml+xml' },
  });
  checkPage(sistema, 'SISTEMA');

  const sistemaDashboard = http.get(`${BASE_URL}/sistema/dashboard`, {
    headers: { Accept: 'text/html,application/xhtml+xml' },
  });
  checkPage(sistemaDashboard, 'SISTEMA_DASHBOARD');

  const meuGendaz = http.get(`${BASE_URL}/meu-gendaz/${SLUG}`, {
    headers: { Accept: 'text/html,application/xhtml+xml' },
  });
  checkPage(meuGendaz, 'MEU_GENDAZ');

  const meuGendazDashboard = http.get(`${BASE_URL}/meu-gendaz/${SLUG}/dashboard`, {
    headers: { Accept: 'text/html,application/xhtml+xml' },
  });
  checkPage(meuGendazDashboard, 'MEU_GENDAZ_DASHBOARD');

  const meuGendazAgenda = http.get(`${BASE_URL}/meu-gendaz/${SLUG}/agenda`, {
    headers: { Accept: 'text/html,application/xhtml+xml' },
  });
  checkPage(meuGendazAgenda, 'MEU_GENDAZ_AGENDA');

  sleep(1);
}
