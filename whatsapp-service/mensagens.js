/*
╔════════════════════════════════════════╗
║  ⚠️  DESATIVADO - FUNCIONALIDADE WhatsApp ║
║  Código comentado. Remova comentários  ║
║  para reativar.                        ║
╚════════════════════════════════════════╝
*/

/*
const textoBase = (valor) => String(valor || '').trim();

function preencher(template, dados) {
  return template.replace(/\{\{(\w+)\}\}/g, (_, chave) => {
    const valor = dados?.[chave];
    return valor === null || valor === undefined || valor === '' ? '' : String(valor);
  });
}

function montarMensagem(tipo, dados = {}) {
  const payload = {
    nome: textoBase(dados.nome) || 'Cliente',
    servico: textoBase(dados.servico) || 'serviço',
    profissional: textoBase(dados.profissional) || 'equipe',
    data: textoBase(dados.data) || 'em breve',
    hora: textoBase(dados.hora) || '--:--',
  };

  const templates = {
    CONFIRMACAO: `Olá, {{nome}}! ✅ Seu agendamento foi confirmado.\n\n📋 *Serviço:* {{servico}}\n👤 *Profissional:* {{profissional}}\n📅 *Data:* {{data}} às {{hora}}\n\nQualquer dúvida é só chamar!`,
    LEMBRETE: `Olá, {{nome}}! ⏰ Lembrando do seu horário em 1 hora.\n\n*{{servico}}* com {{profissional}} às {{hora}}.\n\nTe esperamos! 😊`,
    CANCELAMENTO: `Olá, {{nome}}. ❌ Seu agendamento de *{{servico}}* no dia {{data}} às {{hora}} foi cancelado.\n\nPara reagendar, entre em contato.`,
    REMARCACAO: `Olá, {{nome}}! 🔄 Seu agendamento foi remarcado.\n\n📅 *Novo horário:* {{data}} às {{hora}}\n📋 *Serviço:* {{servico}} com {{profissional}}`,
  };

  const template = templates[String(tipo || '').toUpperCase()] || templates.CONFIRMACAO;
  return preencher(template, payload);
}

module.exports = {
  montarMensagem,
};
*/

module.exports = { montarMensagem: async () => null };
