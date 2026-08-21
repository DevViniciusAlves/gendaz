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
const SLUG = __ENV.GENDAZ_SLUG || __ENV.GENDAZ_EMPRESA_SLUG || 'gendaz-pro';

function isHtml(response) {
  return String(response.body || '').toLowerCase().includes('<!doctype html>');
}

function checkHtmlResponse(response, name) {
  return check(response, {
    [`${name} status 200`]: (r) => r.status === 200,
    [`${name} retornou HTML`]: (r) => isHtml(r),
  });
}

export default function () {
  const headers = { Accept: 'text/html,application/xhtml+xml' };

  checkHtmlResponse(http.get(`${BASE_URL}/sistema`, { headers }), 'SISTEMA');
  checkHtmlResponse(http.get(`${BASE_URL}/sistema/dashboard`, { headers }), 'SISTEMA_DASHBOARD');
  checkHtmlResponse(http.get(`${BASE_URL}/meu-gendaz/${SLUG}`, { headers }), 'MEU_GENDAZ');
  checkHtmlResponse(http.get(`${BASE_URL}/meu-gendaz/${SLUG}/dashboard`, { headers }), 'MEU_GENDAZ_DASHBOARD');
  checkHtmlResponse(http.get(`${BASE_URL}/meu-gendaz/${SLUG}/agenda`, { headers }), 'MEU_GENDAZ_AGENDA');

  sleep(1);
}
