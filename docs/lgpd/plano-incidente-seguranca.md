# gendaz — Plano de Resposta a Incidentes de Segurança e Privacidade

Documento interno de apoio. Não substitui parecer jurídico. Não inventa prazos legais de
notificação; a decisão considera o contexto real do incidente e a regra vigente na data.

E-mail de privacidade/incidentes: `contato@gendaz.site`

## 1. Objetivo e escopo

Responder de forma coordenada a incidentes de segurança da informação ou de privacidade de
dados pessoais que afetem a plataforma gendaz: acesso não autorizado, vazamento, perda,
alteração ou divulgação indevida de dados.

## 2. Papéis (funções, não nomes)

| Função | Responsabilidade |
|--------|------------------|
| Canal de privacidade | Primeiro ponto de contato e registro de suspeitas |
| Equipe técnica (desenvolvimento/ops) | Detecção, contenção, triagem e correção |
| Direção | Decisões de comunicação externa e de retenção pós-incidente |

Não há DPO/encarregado nomeado; a definição formal é **BLOQUEIO DE DECISÃO**.

## 3. Fases

### 3.1 Detectar
- Registrar suspeita: data/hora, canal, sintoma (erro, alerta, relato, artefato).
- Conservar evidência bruta sem alterar (logs, traces, screenshots, e-mails).

### 3.2 Conter
- Revogar sessões e tokens; bloquear acesso ao recurso afetado; isolar dados quando possível.
- Subsistemas já possuem controles acionáveis: sessões (`admin_sessions`), brute-force
  (`bloqueado_ate`), rate-limit persistente e revogação de acesso do Meu Gendaz.

### 3.3 Investigar
- Mapear categorias de dados e titulares possivelmente afetados (ver
  `docs/lgpd/registro-operacoes-tratamento.md` e `docs/lgpd/matriz-retencao.md`).
- Identificar processos/terceiros envolvidos (Stripe, Resend, Groq, Neon, Render).
- Verificar trilhas: `audit_logs` (ações admin), `ip_tracking` (30 dias), logs de integração
  (bytes/URLs sanitizadas — sem payload persistido).

### 3.4 Avaliar risco
- Gravidade, probabilidade de dano e impacto para titulares (não inventar critérios de
  notificação; a decisão é contextual).

### 3.5 Registrar decisão
- Documentar ações, quem fez o quê, horário, justificativa e estado posterior.

### 3.6 Escalar e comunicar
- Escalar à direção e ao canal de privacidade.
- Avaliar comunicação à ANPD e aos titulares conforme a regra vigente e a avaliação de risco.
- Em caso de incidente com processador/terceiro, comunicar o terceiro e exigir relatório.

### 3.7 Recuperar e corrigir
- Corrigir a causa; revisar controles; aplicar retenção mínima necessária pós-incidente.

## 4. Salvaguardas existentes (referência técnica)

- Senhas com hash; OTP com HMAC-SHA256 (secret) e uso único; tokens de sessão hasheados.
- Isolamento entre tenants; rotas `/api/lgpd/**` exigem autenticação e perfil `DONO`.
- Rate-limit persistente e limpeza automática (OTP, IP, audit, insights, pagamentos vencidos).
- Auditoria de tráfego externo sem payload (apenas bytes/URLs sanitizadas).

## 5. Pendências (BLOQUEIO DE DECISÃO / VERIFICAÇÃO EXTERNA)
- Nomeação formal de encarregado/DPO.
- Definição de prazos formais de notificação e de retenção de evidências pós-incidente.
- Contratos/ACs com provedores (Stripe, Resend, Groq, Neon, Render) validando cláusulas de
  incidente e transferência internacional.