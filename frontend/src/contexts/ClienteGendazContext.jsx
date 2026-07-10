import { createContext, useState, useEffect, useCallback } from 'react'
import clienteApi from '../api/clienteApi.js'

export const ClienteGendazContext = createContext()

export function ClienteGendazProvider({ children }) {
  const [cliente, setCliente] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [agendamentos, setAgendamentos] = useState([])
  const [dashboard, setDashboard] = useState(null)
  const [beneficios, setBeneficios] = useState({ promocoes: [], cupons: [] })
  const [configuracoes, setConfiguracoes] = useState(null)

  const sincronizarDados = useCallback(async () => {
    const token = localStorage.getItem('clienteToken')
    if (!token) {
      setCarregando(false)
      return
    }
    try {
      setCarregando(true)
      setErro(null)

      const [perfilRes, dashboardRes, agendamentosRes] = await Promise.allSettled([
        clienteApi.get('/clientes/perfil'),
        clienteApi.get('/clientes/dashboard'),
        clienteApi.get('/clientes/agendamentos/proximos'),
      ])

      if (perfilRes.status === 'fulfilled') {
        const dados = perfilRes.value.data
        setCliente(dados.cliente || dados)
        setConfiguracoes(dados.configuracoes || { email: dados.email, telefone: dados.telefone })
      }

      if (dashboardRes.status === 'fulfilled') {
        setDashboard(dashboardRes.value.data)
      }

      if (agendamentosRes.status === 'fulfilled') {
        const data = agendamentosRes.value.data
        setAgendamentos(Array.isArray(data) ? data : data?.agendamentos || [])
      }
    } catch (err) {
      setErro(err.response?.data?.mensagem || err.message || 'Erro ao carregar dados.')
    } finally {
      setCarregando(false)
    }
  }, [])

  useEffect(() => {
    const token = localStorage.getItem('clienteToken')
    if (token) {
      sincronizarDados()
    } else {
      setCarregando(false)
    }
  }, [sincronizarDados])

  useEffect(() => {
    if (!cliente) return

    const intervalDashboard = setInterval(async () => {
      try {
        const { data } = await clienteApi.get('/clientes/dashboard')
        setDashboard(data)
      } catch { /* silencioso */ }
    }, 5 * 60 * 1000)

    const intervalAgendamentos = setInterval(async () => {
      try {
        const { data } = await clienteApi.get('/clientes/agendamentos/proximos')
        setAgendamentos(Array.isArray(data) ? data : data?.agendamentos || [])
      } catch { /* silencioso */ }
    }, 5 * 60 * 1000)

    return () => {
      clearInterval(intervalDashboard)
      clearInterval(intervalAgendamentos)
    }
  }, [cliente])

  const criarAgendamento = useCallback(async (dados) => {
    const { data } = await clienteApi.post('/clientes/agendamentos/criar', dados)
    const token = localStorage.getItem('clienteToken')
    if (token) {
      const { data: ags } = await clienteApi.get('/clientes/agendamentos/proximos')
      setAgendamentos(Array.isArray(ags) ? ags : ags?.agendamentos || [])
    }
    return data
  }, [])

  const reagendar = useCallback(async (agendamentoId, novosDados) => {
    const { data } = await clienteApi.patch(`/clientes/agendamentos/${agendamentoId}/reagendar`, novosDados)
    const token = localStorage.getItem('clienteToken')
    if (token) {
      const { data: ags } = await clienteApi.get('/clientes/agendamentos/proximos')
      setAgendamentos(Array.isArray(ags) ? ags : ags?.agendamentos || [])
    }
    return data
  }, [])

  const cancelarAgendamento = useCallback(async (agendamentoId, motivo) => {
    await clienteApi.delete(`/clientes/agendamentos/${agendamentoId}/cancelar`, {
      data: { motivo },
    })
    const token = localStorage.getItem('clienteToken')
    if (token) {
      const { data: ags } = await clienteApi.get('/clientes/agendamentos/proximos')
      setAgendamentos(Array.isArray(ags) ? ags : ags?.agendamentos || [])
    }
  }, [])

  const carregarHistorico = useCallback(async (pagina = 1, limite = 10) => {
    const { data } = await clienteApi.get('/clientes/agendamentos/historico', {
      params: { pagina, limite },
    })
    return data
  }, [])

  const buscarHorarios = useCallback(async (servicoId, profissionalId, data) => {
    const { data: horarios } = await clienteApi.get('/clientes/horarios-disponiveis', {
      params: { servicoId, profissionalId, data },
    })
    return horarios
  }, [])

  const carregarBeneficios = useCallback(async () => {
    const [promosRes, cuponsRes] = await Promise.allSettled([
      clienteApi.get('/clientes/promocoes'),
      clienteApi.get('/clientes/cupons'),
    ])
    setBeneficios({
      promocoes: promosRes.status === 'fulfilled' ? promosRes.value.data : [],
      cupons: cuponsRes.status === 'fulfilled' ? cuponsRes.value.data : [],
    })
  }, [])

  const usarCupom = useCallback(async (cupomId) => {
    const { data } = await clienteApi.post(`/clientes/cupons/${cupomId}/usar`)
    await carregarBeneficios()
    return data
  }, [carregarBeneficios])

  const enviarMensagemIA = useCallback(async (mensagem, historicoChat = []) => {
    const { data } = await clienteApi.post('/clientes/ia/mensagem', {
      mensagem,
      historico: historicoChat,
    })
    return data
  }, [])

  const carregarPreferenciasIA = useCallback(async () => {
    const { data } = await clienteApi.get('/clientes/ia/preferencias')
    return data
  }, [])

  const atualizarPerfil = useCallback(async (dados) => {
    const { data } = await clienteApi.patch('/clientes/perfil', dados)
    setCliente((prev) => ({ ...prev, ...data }))
    return data
  }, [])

  const atualizarNotificacoes = useCallback(async (dados) => {
    const { data } = await clienteApi.patch('/clientes/notificacoes', dados)
    setConfiguracoes((prev) => ({ ...prev, ...data }))
    return data
  }, [])

  const atualizarPrivacidade = useCallback(async (dados) => {
    const { data } = await clienteApi.patch('/clientes/privacidade', dados)
    setConfiguracoes((prev) => ({ ...prev, ...data }))
    return data
  }, [])

  const logout = useCallback(async () => {
    try {
      await clienteApi.post('/clientes/auth/logout')
    } catch { /* ignora */ }
    localStorage.removeItem('clienteToken')
    localStorage.removeItem('clienteRefreshToken')
    setCliente(null)
    setDashboard(null)
    setAgendamentos([])
    setBeneficios({ promocoes: [], cupons: [] })
    setConfiguracoes(null)
  }, [])

  const value = {
    cliente,
    dashboard,
    agendamentos,
    beneficios,
    configuracoes,
    carregando,
    erro,
    sincronizarDados,
    criarAgendamento,
    reagendar,
    cancelarAgendamento,
    carregarHistorico,
    buscarHorarios,
    carregarBeneficios,
    usarCupom,
    enviarMensagemIA,
    carregarPreferenciasIA,
    atualizarPerfil,
    atualizarNotificacoes,
    atualizarPrivacidade,
    logout,
  }

  return (
    <ClienteGendazContext.Provider value={value}>
      {children}
    </ClienteGendazContext.Provider>
  )
}
