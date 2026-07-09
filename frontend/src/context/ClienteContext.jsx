import { createContext, useContext, useMemo, useState } from 'react'

const ClienteContext = createContext(null)

const portalInicial = {
  cliente: {
    nome: 'João',
    telefone: '+55 (65) 99999-9999',
    email: 'joao@exemplo.com',
  },
  dashboard: {
    ultimoAtendimento: 'Corte e barba em 03/07/2026',
    proximoAtendimento: '10/07/2026 às 18:00',
    sugestoes: [
      'Seu próximo corte está perto do intervalo ideal.',
      'A IA recomenda repetir o mesmo horário da última visita.',
    ],
    notificacoes: [
      'Promoção de hidratação válida até sexta-feira.',
      'Seu cupom de aniversário está disponível.',
    ],
    recompensas: 'Você tem 120 pontos acumulados.',
  },
  agendamentos: [
    {
      id: 1,
      servico: 'Corte masculino',
      profissional: 'Marcos',
      data: '2026-07-10',
      hora: '18:00',
      status: 'Confirmado',
    },
    {
      id: 2,
      servico: 'Barba',
      profissional: 'Marcos',
      data: '2026-07-17',
      hora: '19:00',
      status: 'Pendente',
    },
  ],
  historico: [
    {
      id: 11,
      servico: 'Corte masculino',
      profissional: 'Marcos',
      data: '2026-07-03',
      valor: 55,
      observacao: 'Cliente preferiu manter lateral baixa.',
    },
    {
      id: 12,
      servico: 'Barba',
      profissional: 'Marcos',
      data: '2026-06-20',
      valor: 35,
      observacao: 'Finalização com toalha quente.',
    },
  ],
  beneficios: {
    promocoes: [
      {
        id: 1,
        titulo: '10% em hidratação',
        descricao: 'Válido até domingo para clientes frequentes.',
      },
      {
        id: 2,
        titulo: 'Combo corte + barba',
        descricao: 'Economize no pacote com retorno em 30 dias.',
      },
    ],
    cupons: [
      {
        id: 1,
        codigo: 'GENDAZ10',
        descricao: '10% de desconto no próximo agendamento.',
      },
    ],
  },
  assistente: {
    mensagens: [
      { id: 1, origem: 'ia', texto: 'Olá, João. Quer agendar novamente seu corte de sexta?' },
    ],
    preferencias: {
      profissionalFavorito: 'Marcos',
      servicoFavorito: 'Corte masculino',
      diasPreferidos: 'Sexta-feira',
      horariosPreferidos: '18h',
    },
  },
  configuracoes: {
    receberNotificacoes: true,
    privacidadeCompartilhada: false,
  },
}

export function ClienteProvider({ children }) {
  const [portal, setPortal] = useState(portalInicial)

  const value = useMemo(() => ({
    portal,
    setPortal,
    atualizarCliente(partial) {
      setPortal((atual) => ({
        ...atual,
        cliente: {
          ...atual.cliente,
          ...partial,
        },
      }))
    },
    atualizarConfiguracoes(partial) {
      setPortal((atual) => ({
        ...atual,
        configuracoes: {
          ...atual.configuracoes,
          ...partial,
        },
      }))
    },
    adicionarMensagem(texto) {
      setPortal((atual) => ({
        ...atual,
        assistente: {
          ...atual.assistente,
          mensagens: [
            ...atual.assistente.mensagens,
            { id: Date.now(), origem: 'cliente', texto },
            { id: Date.now() + 1, origem: 'ia', texto: 'Posso seguir com o próximo horário disponível.' },
          ],
        },
      }))
    },
  }), [portal])

  return <ClienteContext.Provider value={value}>{children}</ClienteContext.Provider>
}

export function useCliente() {
  return useContext(ClienteContext)
}
