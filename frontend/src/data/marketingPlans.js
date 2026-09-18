// Fonte única de verdade para os planos públicos de marketing.
// Usado pela Home e pelas landing pages de segmento (SegmentLandingPage).
// NÃO alterar valores aqui sem alinhar marketing + produto.

export const marketingPlans = [
  {
    nome: 'Plano Básico',
    subtitulo: 'Agenda simples',
    preco: 'R$ 29,90/mês',
    extra: '7 dias grátis',
    descrição: 'Para organizar sua agenda, clientes e serviços de forma prática e eficiente.',
    beneficios: [
      'Financeiro - Pagamentos automatizados - Relatórios',
      'Histórico ilimitado',
      'Agendamentos ilimitados',
      'Confirmação de agendamentos',
    ],
    naoInclui: [
      'CRM integrado',
      'Insights',
      'Até 3 usuários',
      'Financeiro completo',
    ],
    cta: 'Assinar Básico',
  },
  {
    nome: 'Plano Pro',
    subtitulo: 'Gestão completa com financeiro',
    preco: 'R$ 79,90/mês',
    extra: '7 dias grátis',
    descrição: 'Para gerenciar sua agenda, equipe, pagamentos e insights com inteligência.',
    beneficios: [
      'Tudo do Plano Básico +',
      'Até 3 usuários na conta',
      'CRM integrado',
      'Insights com GendazIA no controle',
      'Financeiro completo: caixa, despesas pagamentos automatizados',
    ],
    cta: 'Assinar Pro',
    destaque: true,
  },
  {
    nome: 'Plano Plus',
    subtitulo: 'Mais capacidade para sua equipe',
    preco: 'R$ 109,90/mês',
    extra: '7 dias grátis',
    descrição: 'Para equipes maiores com maior necessidade de gerenciamento e acesso.',
    beneficios: [
      'Tudo do Plano Pro +',
      'Até 7 usuários na conta',
      'CRM integrado',
      'Insights com GendazIA no controle',
      'Financeiro completo: caixa, despesas pagamentos automatizados',
    ],
    cta: 'Assinar Plus',
  },
  {
    nome: 'Plano Enterprise',
    subtitulo: 'Escalabilidade máxima',
    preco: 'R$ 149,90/mês',
    extra: '7 dias grátis',
    descrição: 'Para operações robustas com gerenciamento extensivo de usuários.',
    beneficios: [
      'Tudo do Plano Plus +',
      'Até 15 usuários na conta',
      'CRM integrado',
      'Insights com GendazIA no controle',
      'Financeiro completo: caixa, despesas pagamentos automatizados',
    ],
    cta: 'Assinar Enterprise',
  },
]

export function buildPlanSignupUrl(plano) {
  return `/criar-conta?plano=${encodeURIComponent(plano.nome)}&preco=${encodeURIComponent(plano.preco)}`
}
