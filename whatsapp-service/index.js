/*
╔════════════════════════════════════════╗
║  ⚠️  DESATIVADO - FUNCIONALIDADE WhatsApp ║
║  Código comentado. Remova comentários  ║
║  para reativar.                        ║
╚════════════════════════════════════════╝
*/

require('dotenv').config();
const axios = require('axios');
const express = require('express');
// ⚠️ DESATIVADO - Imports WhatsApp comentados
// const {
//   conectarEmpresa,
//   statusEmpresa,
//   desconectarEmpresa,
//   enviarMensagemEmpresa,
//   enviarMensagemParaProprioNumeroEmpresa,
//   limparSessaoEmpresa,
//   backendHttp,
//   restaurarSessoesPersistidas,
// } = require('./whatsapp');
const {
  registrarConfirmacaoPagamentoDono,
  limparConversasExpiradas,
} = require('./ia');

const app = express();
const port = Number(process.env.PORT || 3000);
const backendToken = String(process.env.WHATSAPP_INTERNAL_TOKEN || process.env.BACKEND_INTERNAL_TOKEN || '').trim();

app.use(express.json({ limit: '1mb' }));

// ⚠️ DESATIVADO - Rota desativada
// app.get('/health', (_req, res) => {
//   res.status(200).json({
//     status: 'UP',
//     service: 'whatsapp-service',
//     uptimeSeconds: Math.floor(process.uptime()),
//     timestamp: new Date().toISOString(),
//   });
// });

// ⚠️ DESATIVADO - Middleware de autenticacao desativado
// app.use((req, res, next) => {
//   const requestPath = String(req.path || req.originalUrl || '').split('?')[0].replace(/\/+$/, '') || '/';
//   if (!backendToken || (req.method === 'GET' && requestPath === '/health')) return next();
//   const received = String(req.header('X-Internal-Token') || '').trim();
//   if (received !== backendToken) {
//     return res.status(401).json({ message: 'Serviço de WhatsApp não autorizado.' });
//   }
//   return next();
// });

// ⚠️ DESATIVADO - Rota desativada
// app.post('/connect', async (req, res) => {
//   try {
//     const phone = req.body?.phone || req.body?.phoneNumber || '';
//     const response = await conectarEmpresa(req.body?.empresaId, phone);
//     return res.status(200).json({
//       pairingCode: response.code || response.pairingCode || null,
//       status: response.status,
//       statusLabel: response.statusLabel,
//       message: response.message,
//       phoneNumber: response.phoneNumber || phone,
//       expiresAt: response.expiresAt || null,
//     });
//   } catch (error) {
//     console.warn('[Bot-Service] connect failed:', error.message);
//     const message = String(error.message || '').includes('Connection Failure')
//       ? 'Não foi possível gerar o código de pareamento. Verifique se o número informado possui WhatsApp ativo.'
//       : (error.message || 'Não foi possível conectar o WhatsApp.');
//     return res.status(400).json({ message });
//   }
// });

// ⚠️ DESATIVADO - Rota desativada
// app.get('/status', async (_req, res) => {
//   try {
//     const response = await statusEmpresa();
//     const conectado = response.status === 'CONNECTED';
//     const sessionError = response.status === 'SESSION_ERROR';
//     console.log('[Bot-Service] GET /status ->', {
//       status: response.status,
//       conectado,
//       phoneNumber: response.phoneNumber || null,
//     });
//     return res.status(200).json({
//       conectado,
//       numero: response.phoneNumber || null,
//       status: response.status,
//       statusLabel: response.statusLabel,
//       pairingCode: response.code || null,
//       code: response.code || null,
//       expiresAt: response.expiresAt || null,
//       connected: conectado,
//       numeroConectado: response.phoneNumber || null,
//       message: sessionError
//         ? 'Sessão do WhatsApp inválida. Desconecte e conecte novamente.'
//         : response.message,
//     });
//   } catch (error) {
//     return res.status(400).json({ message: error.message || 'Não foi possível consultar o status.' });
//   }
// });

// ⚠️ DESATIVADO - Rota desativada
// app.get('/status/:empresaId', async (req, res) => {
//   try {
//     const response = await statusEmpresa(req.params.empresaId);
//     const conectado = response.status === 'CONNECTED';
//     console.log('[Bot-Service] GET /status/:empresaId ->', {
//       empresaId: req.params.empresaId,
//       status: response.status,
//       conectado,
//       phoneNumber: response.phoneNumber || null,
//     });
//     return res.status(200).json({
//       ...response,
//       conectado,
//       connected: conectado,
//       numero: response.phoneNumber || null,
//       numeroConectado: response.phoneNumber || null,
//       pairingCode: response.code || response.pairingCode || null,
//       expiresAt: response.expiresAt || null,
//     });
//   } catch (error) {
//     return res.status(400).json({ message: error.message || 'Não foi possível consultar o status.' });
//   }
// });

// ⚠️ DESATIVADO - Rota desativada
// app.post('/disconnect', async (_req, res) => {
//   try {
//     const response = await desconectarEmpresa();
//     return res.status(200).json(response);
//   } catch (error) {
//     return res.status(400).json({ message: error.message || 'Não foi possível desconectar o WhatsApp.' });
//   }
// });

// ⚠️ DESATIVADO - Rota desativada
// app.post('/disconnect/:empresaId', async (req, res) => {
//   try {
//     const response = await desconectarEmpresa(req.params.empresaId);
//     return res.status(200).json(response);
//   } catch (error) {
//     return res.status(400).json({ message: error.message || 'Não foi possível desconectar o WhatsApp.' });
//   }
// });

// ⚠️ DESATIVADO - Rota desativada
// app.delete('/session/:empresaId', async (req, res) => {
//   try {
//     const response = await limparSessaoEmpresa(req.params.empresaId);
//     return res.status(200).json(response);
//   } catch (error) {
//     return res.status(400).json({ message: error.message || 'Não foi possível limpar a sessão do WhatsApp.' });
//   }
// });

// ⚠️ DESATIVADO - Rota desativada
// app.post('/send', async (req, res) => {
//   try {
//     const phone = req.body?.phone || req.body?.to || '';
//     const message = req.body?.message || '';
//     const response = await enviarMensagemEmpresa(req.body?.empresaId, phone, message);
//     return res.status(200).json(response);
//   } catch (error) {
//     return res.status(400).json({ message: error.message || 'Não foi possível enviar a mensagem.' });
//   }
// });

// ⚠️ DESATIVADO - Rota desativada
// app.post('/api/whatsapp/enviar-lembrete', async (req, res) => {
//   console.log('[endpoint] POST /api/whatsapp/enviar-lembrete chamado', {
//     body: req.body,
//     timestamp: new Date().toISOString(),
//   });
//   try {
//     const empresaId = Number(req.body?.empresaId);
//     const agendamentoId = Number(req.body?.agendamentoId);
//     const tipo = String(req.body?.tipo || 'LEMBRETE_CLIENTE').trim().toUpperCase();
//     const telefone = String(req.body?.telefone || '').trim();
//     if (!empresaId || !agendamentoId) {
//       return res.status(400).json({ sucesso: false, erro: 'VALIDACAO', mensagem: 'empresaId e agendamentoId sao obrigatorios.' });
//     }
//     if (!telefone) {
//       return res.status(400).json({ sucesso: false, erro: 'TELEFONE_INVALIDO', mensagem: 'Telefone inválido.' });
//     }
//
//     const mensagem = String(req.body?.mensagem || '').trim();
//     if (!mensagem) {
//       return res.status(400).json({ sucesso: false, erro: 'MENSAGEM_INVALIDA', mensagem: 'Mensagem obrigatoria.' });
//     }
//
//     const envio = await enviarMensagemEmpresa(empresaId, telefone, mensagem);
//     try {
//       await backendHttp.post('/api/internal/whatsapp/marcar-lembrete-enviado', {
//         agendamentoId,
//         tipo,
//       });
//     } catch (error) {
//       console.warn('[Reminder] falha ao marcar enviado no backend:', error.message);
//       return res.status(502).json({
//         sucesso: false,
//         erro: 'FALHA_BAIXO_NIVEL',
//         mensagem: 'Mensagem enviada, mas nao foi possivel confirmar no backend.',
//       });
//     }
//
//     console.log('[Reminder] enviado com sucesso', {
//       empresaId,
//       agendamentoId,
//       tipo,
//       telefone,
//     });
//
//     return res.status(200).json({ sucesso: true, status: envio?.status || 'enviado' });
//   } catch (error) {
//     console.error('[Reminder] erro ao enviar lembrete:', error.message);
//     return res.status(500).json({ sucesso: false, erro: 'ERRO_INTERNO', mensagem: error.message || 'Falha ao enviar lembrete.' });
//   }
// });

// ⚠️ DESATIVADO - Rota desativada
// app.post('/payment-owner-reminder', async (req, res) => {
//   try {
//     const empresaId = Number(req.body?.empresaId);
//     const agendamentoId = Number(req.body?.agendamentoId);
//     const segundoLembrete = Boolean(req.body?.segundoLembrete);
//     const mensagem = String(req.body?.mensagem || '').trim();
//     if (!empresaId || !agendamentoId) {
//       return res.status(400).json({
//         success: false,
//         erro: 'VALIDACAO',
//         message: 'empresaId e agendamentoId sao obrigatorios.',
//       });
//     }
//     if (!mensagem) {
//       return res.status(400).json({
//         success: false,
//         erro: 'VALIDACAO',
//         message: 'Mensagem obrigatoria.',
//       });
//     }
//
//     console.log('[bot-pagamento-dono] lembrete recebido', {
//       empresaId,
//       agendamentoId,
//       segundoLembrete,
//     });
//
//     const response = await enviarMensagemParaProprioNumeroEmpresa(empresaId, mensagem);
//     registrarConfirmacaoPagamentoDono({
//       empresaId,
//       telefone: response.phone,
//       remoteJid: response.remoteJid,
//       agendamentoId,
//       protocolo: String(req.body?.protocolo || '').trim(),
//       clienteNome: req.body?.clienteNome || '',
//       clienteTelefone: req.body?.clienteTelefone || '',
//       servicoNome: req.body?.servicoNome || '',
//       profissionalNome: req.body?.profissionalNome || '',
//       data: req.body?.data || null,
//       horario: req.body?.horario || null,
//       segundoLembrete,
//     });
//
//     console.log('[bot-pagamento-dono] enviado para proprio numero', {
//       empresaId,
//       agendamentoId,
//       segundoLembrete,
//     });
//
//     return res.status(200).json({ success: true, status: 'enviado' });
//   } catch (error) {
//     console.warn('[Bot-Service] payment owner reminder failed:', error.message);
//     return res.status(400).json({ success: false, message: error.message || 'Não foi possível enviar a confirmação de pagamento.' });
//   }
// });

// ⚠️ DESATIVADO - Rota desativada
// app.post('/webhook/agendamento', async (_req, res) => {
//   res.status(200).json({ status: 'ok' });
// });

// ⚠️ DESATIVADO - Keep-alive e cleanup comentados
// const BACKEND_URL = (process.env.BACKEND_URL || process.env.BACKEND_JAVA_URL || 'http://localhost:8080').replace(/\/+$/, '');
// const PUBLIC_WHATSAPP_URL = (process.env.RENDER_EXTERNAL_URL || process.env.PUBLIC_WHATSAPP_URL || '').replace(/\/+$/, '');
// const KEEP_ALIVE_INTERVAL_MS = 5 * 60 * 1000;
//
// async function pingKeepAlive() {
//   const targets = [
//     { name: 'backend', url: `${BACKEND_URL}/health` },
//   ];
//
//   if (PUBLIC_WHATSAPP_URL) {
//     targets.push({ name: 'whatsapp-service', url: `${PUBLIC_WHATSAPP_URL}/health` });
//   } else {
//     targets.push({ name: 'whatsapp-service', url: `http://127.0.0.1:${port}/health` });
//   }
//
//   for (const target of targets) {
//     try {
//       const response = await axios.get(target.url, { timeout: 15000, validateStatus: () => true });
//       if (response.status >= 200 && response.status < 300) {
//         console.log(`[keep-alive] ${target.name} ping ok status=${response.status}`, new Date().toISOString());
//       } else {
//         console.warn(`[keep-alive] ${target.name} ping falhou status=${response.status} url=${target.url}`);
//       }
//     } catch (error) {
//       console.warn(`[keep-alive] ${target.name} ping erro url=${target.url} detalhe=${error.message}`);
//     }
//   }
// }
//
// console.log('[cleanup] iniciado cleanup automático de conversas (TTL: 24h)');
// setInterval(() => {
//   try {
//     limparConversasExpiradas();
//   } catch (error) {
//     console.warn('[cleanup] falha ao executar limpeza automática de conversas:', error.message);
//   }
// }, 60 * 60 * 1000).unref?.();
//
// setInterval(() => {
//   pingKeepAlive().catch((error) => {
//     console.warn('[keep-alive] falha no ping agendado:', error.message);
//   });
// }, KEEP_ALIVE_INTERVAL_MS).unref?.();

// ⚠️ DESATIVADO - server.listen comentado
// app.listen(port, () => {
//   console.log(`[Bot-Service] running on port ${port}`);
//   pingKeepAlive().catch((error) => {
//     console.warn('[keep-alive] falha no ping inicial:', error.message);
//   });
//   restaurarSessoesPersistidas().catch((error) => {
//     console.warn('[Bot-Service] falha ao restaurar sessoes ativas no boot:', error.message);
//   });
// });




