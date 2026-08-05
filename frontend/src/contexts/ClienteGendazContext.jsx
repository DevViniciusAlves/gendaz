import { createContext, useState, useEffect, useCallback, useRef } from 'react'
import clienteApi from '../api/clienteApi.js'
import { meuGendazPromocoesApi } from '../api/meuGendazPromocoesApi.js'

export const ClienteGendazContext = createContext()

const STORAGE_PREFIX = 'meu_gendaz_session'

function storageKey(slug) {
  const normalizado = String(slug || '').trim().toLowerCase()
  return `${STORAGE_PREFIX}_${normalizado || 'default'}`
}

function lerSessaoPersistida(slug) {
  if (typeof window === 'undefined' || !window.sessionStorage) return null
  try {
    const raw = window.sessionStorage.getItem(storageKey(slug))
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function salvarSessaoPersistida(slug, dados) {
  if (typeof window === 'undefined' || !window.sessionStorage) return
  try {
    if (!dados) {
      window.sessionStorage.removeItem(storageKey(slug))
      return
    }
    window.sessionStorage.setItem(storageKey(slug), JSON.stringify(dados))
  } catch {
    // cache apenas de conveniência; nao pode quebrar o fluxo principal
  }
}

export function ClienteGendazProvider({ children, slug }) {
  const cacheInicial = lerSessaoPersistida(slug)
  const [cliente, setCliente] = useState(cacheInicial?.cliente || null)
  const [perfilPendente, setPerfilPendente] = useState(Boolean(cacheInicial?.perfilPendente))
  const [perfilAcesso, setPerfilAcesso] = useState(cacheInicial?.perfilAcesso || null)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)
  const [agendamentos, setAgendamentos] = useState(cacheInicial?.agendamentos || [])
  const [dashboard, setDashboard] = useState(cacheInicial?.dashboard || null)
  const [beneficios, setBeneficios] = useState(cacheInicial?.beneficios || { promocoes: [], cupons: [] })
  const [configuracoes, setConfiguracoes] = useState(cacheInicial?.configuracoes || null)
  const [servicos, setServicos] = useState(cacheInicial?.servicos || [])
  const [profissionais, setProfissionais] = useState(cacheInicial?.profissionais || [])
  const sincronizandoRef = useRef(null)

  const limparEstadoSessao = useCallback(() => {
    setCliente(null)
    setPerfilPendente(false)
    setPerfilAcesso(null)
    setDashboard(null)
    setAgendamentos([])
    setBeneficios({ promocoes: [], cupons: [] })
    setConfiguracoes(null)
    setServicos([])
    setProfissionais([])
    salvarSessaoPersistida(slug, null)
  }, [slug])

  const sincronizarDados = useCallback(async ({ exigirSessao = false } = {}) => {
    if (sincronizandoRef.current) {
      return sincronizandoRef.current
    }

    const promessa = (async () => {
    try {
      setCarregando(true)
      setErro(null)

      const perfilRes = await clienteApi.get('/meu-gendaz/perfil', { skipMeuGendazLogout: !exigirSessao })

      if (perfilRes?.data?.cadastroPendente) {
        setPerfilPendente(true)
        setPerfilAcesso(perfilRes.data)
        setCliente(null)
        setDashboard(null)
        setAgendamentos([])
        setBeneficios({ promocoes: [], cupons: [] })
        setConfiguracoes(null)
        setServicos([])
        setProfissionais([])
        salvarSessaoPersistida(slug, {
          cliente: null,
          perfilPendente: true,
          perfilAcesso: perfilRes.data,
          dashboard: null,
          agendamentos: [],
          beneficios: { promocoes: [], cupons: [] },
          configuracoes: null,
          servicos: [],
          profissionais: [],
        })
        setCarregando(false)
        return
      }

      const dadosPerfil = perfilRes.data
      setPerfilPendente(false)
      setPerfilAcesso(dadosPerfil)
      setCliente(dadosPerfil)
      setConfiguracoes({
        notificacoes: { email: true, sms: false, push: true },
        compartilharHistorico: false,
      })

      const [dashboardRes, agendamentosRes, servRes, profRes] = await Promise.allSettled([
        clienteApi.get('/meu-gendaz/dashboard', { skipMeuGendazLogout: !exigirSessao }),
        clienteApi.get('/meu-gendaz/agendamentos/proximos', { skipMeuGendazLogout: !exigirSessao }),
        clienteApi.get('/meu-gendaz/servicos', { skipMeuGendazLogout: !exigirSessao }),
        clienteApi.get('/meu-gendaz/profissionais', { skipMeuGendazLogout: !exigirSessao }),
      ])

      const respostas = [dashboardRes, agendamentosRes, servRes, profRes]
      const houve401 = respostas.some((resultado) => (
        resultado.status === 'rejected' && resultado.reason?.response?.status === 401
      ))

      if (houve401) {
        if (exigirSessao) {
          limparEstadoSessao()
          window.dispatchEvent(new CustomEvent('meu-gendaz:logout'))
        }
        setCarregando(false)
        return
      }

      if (dashboardRes.status === 'fulfilled') {
        setDashboard(dashboardRes.value.data)
      }

      if (agendamentosRes.status === 'fulfilled') {
        const data = agendamentosRes.value.data
        setAgendamentos(Array.isArray(data) ? data : data?.agendamentos || [])
      }

      if (servRes.status === 'fulfilled') {
        setServicos(Array.isArray(servRes.value.data) ? servRes.value.data : [])
      }

      if (profRes.status === 'fulfilled') {
        setProfissionais(Array.isArray(profRes.value.data) ? profRes.value.data : [])
      }

      salvarSessaoPersistida(slug, {
        cliente: dadosPerfil,
        perfilPendente: false,
        perfilAcesso: dadosPerfil,
        dashboard: dashboardRes.status === 'fulfilled' ? dashboardRes.value.data : null,
        agendamentos: agendamentosRes.status === 'fulfilled'
          ? (Array.isArray(agendamentosRes.value.data) ? agendamentosRes.value.data : agendamentosRes.value.data?.agendamentos || [])
          : [],
        beneficios: beneficios,
        configuracoes: {
          notificacoes: { email: true, sms: false, push: true },
          compartilharHistorico: false,
        },
        servicos: servRes.status === 'fulfilled' ? (Array.isArray(servRes.value.data) ? servRes.value.data : []) : [],
        profissionais: profRes.status === 'fulfilled' ? (Array.isArray(profRes.value.data) ? profRes.value.data : []) : [],
      })

      await carregarBeneficios()
    } catch (err) {
      if (err.response?.status === 401) {
        if (exigirSessao) {
          limparEstadoSessao()
          window.dispatchEvent(new CustomEvent('meu-gendaz:logout'))
        }
        setCarregando(false)
        return
      }
      setErro(err.response?.data?.mensagem || err.message || 'Erro ao carregar dados.')
    } finally {
      setCarregando(false)
    }
    })()

    sincronizandoRef.current = promessa
    try {
      return await promessa
    } finally {
      sincronizandoRef.current = null
    }
  }, [limparEstadoSessao, slug])

  useEffect(() => {
    if (!slug) return undefined
    clienteApi.defaults.headers.common['X-Meu-Gendaz-Slug'] = slug
    void sincronizarDados({ exigirSessao: false })
    return () => {
      delete clienteApi.defaults.headers.common['X-Meu-Gendaz-Slug']
    }
  }, [slug, sincronizarDados])

  useEffect(() => {
    salvarSessaoPersistida(slug, {
      cliente,
      perfilPendente,
      perfilAcesso,
      dashboard,
      agendamentos,
      beneficios,
      configuracoes,
      servicos,
      profissionais,
    })
  }, [slug, cliente, perfilPendente, perfilAcesso, dashboard, agendamentos, beneficios, configuracoes, servicos, profissionais])

  useEffect(() => {
    const lidarComLogout = () => {
      limparEstadoSessao()
      setErro(null)
      setCarregando(false)
      if (slug) {
        window.location.href = `/meu-gendaz/${slug}`
      }
    }

    window.addEventListener('meu-gendaz:logout', lidarComLogout)
    return () => window.removeEventListener('meu-gendaz:logout', lidarComLogout)
  }, [limparEstadoSessao, slug])

  useEffect(() => {
    if (!cliente) return undefined

    const intervalDashboard = setInterval(async () => {
      try {
        const { data } = await clienteApi.get('/meu-gendaz/dashboard')
        setDashboard(data)
      } catch { /* silencioso */ }
    }, 5 * 60 * 1000)

    const intervalAgendamentos = setInterval(async () => {
      try {
        const { data } = await clienteApi.get('/meu-gendaz/agendamentos/proximos')
        setAgendamentos(Array.isArray(data) ? data : data?.agendamentos || [])
      } catch { /* silencioso */ }
    }, 5 * 60 * 1000)

    return () => {
      clearInterval(intervalDashboard)
      clearInterval(intervalAgendamentos)
    }
  }, [cliente])

  const carregarBeneficios = useCallback(async () => {
    const [promosRes, cuponsRes, notifRes] = await Promise.allSettled([
      meuGendazPromocoesApi.listar(),
      meuGendazPromocoesApi.usados(),
      meuGendazPromocoesApi.notificacoes(),
    ])
    setBeneficios({
      promocoes: promosRes.status === 'fulfilled' ? promosRes.value : [],
      cupons: cuponsRes.status === 'fulfilled' ? cuponsRes.value : [],
      notificacoes: notifRes.status === 'fulfilled' ? (notifRes.value?.notificacoes || []) : [],
    })
  }, [])

  useEffect(() => {
    if (typeof window === 'undefined') return undefined

    const lidarAtualizacao = () => {
      void carregarBeneficios()
    }

    const lidarStorage = (event) => {
      if (event.key === 'gendaz-promocoes-refresh') {
        void carregarBeneficios()
      }
    }

    window.addEventListener('gendaz:promocoes-atualizadas', lidarAtualizacao)
    window.addEventListener('storage', lidarStorage)

    let canal = null
    if (typeof BroadcastChannel !== 'undefined') {
      canal = new BroadcastChannel('gendaz-promocoes')
      canal.onmessage = () => {
        void carregarBeneficios()
      }
    }

    return () => {
      window.removeEventListener('gendaz:promocoes-atualizadas', lidarAtualizacao)
      window.removeEventListener('storage', lidarStorage)
      if (canal) canal.close()
    }
  }, [carregarBeneficios])

  useEffect(() => {
    if (!cliente) return undefined

    let ativo = true
    let timer = null

    const atualizarBeneficios = async () => {
      if (!ativo) return
      try {
        await carregarBeneficios()
      } catch {
        /* silencioso */
      }
    }

    const lidarFocus = () => {
      void atualizarBeneficios()
    }

    const lidarVisibilidade = () => {
      if (document.visibilityState === 'visible') {
        void atualizarBeneficios()
      }
    }

    window.addEventListener('focus', lidarFocus)
    document.addEventListener('visibilitychange', lidarVisibilidade)
    timer = setInterval(() => {
      void atualizarBeneficios()
    }, 15 * 1000)

    return () => {
      ativo = false
      if (timer) clearInterval(timer)
      window.removeEventListener('focus', lidarFocus)
      document.removeEventListener('visibilitychange', lidarVisibilidade)
    }
  }, [cliente, carregarBeneficios])

  const criarAgendamento = useCallback(async (dados) => {
    const { data } = await clienteApi.post('/meu-gendaz/agendamentos/criar', dados)
    const { data: ags } = await clienteApi.get('/meu-gendaz/agendamentos/proximos')
    setAgendamentos(Array.isArray(ags) ? ags : ags?.agendamentos || [])
    await carregarBeneficios()
    return data
  }, [carregarBeneficios])

  const reagendar = useCallback(async (agendamentoId, novosDados) => {
    const { data } = await clienteApi.patch(`/meu-gendaz/agendamentos/${agendamentoId}/reagendar`, novosDados)
    const { data: ags } = await clienteApi.get('/meu-gendaz/agendamentos/proximos')
    setAgendamentos(Array.isArray(ags) ? ags : ags?.agendamentos || [])
    return data
  }, [])

  const cancelarAgendamento = useCallback(async (agendamentoId, motivo) => {
    await clienteApi.delete(`/meu-gendaz/agendamentos/${agendamentoId}/cancelar`, {
      data: { motivo },
    })
    const { data: ags } = await clienteApi.get('/meu-gendaz/agendamentos/proximos')
    setAgendamentos(Array.isArray(ags) ? ags : ags?.agendamentos || [])
  }, [])

  const carregarHistorico = useCallback(async (pagina = 1, limite = 10) => {
    const { data } = await clienteApi.get('/meu-gendaz/agendamentos/historico', {
      params: { pagina, limite },
    })
    return data
  }, [])

  const buscarHorarios = useCallback(async (servicoId, profissionalId, data) => {
    const { data: horarios } = await clienteApi.get('/meu-gendaz/horarios-disponiveis', {
      params: { servicoId, profissionalId, data },
    })
    return horarios
  }, [])

  const usarCupom = useCallback(async (cupomCodigo) => {
    if (typeof window !== 'undefined' && slug && cupomCodigo) {
      window.location.href = `/meu-gendaz/${slug}/agenda?cupom=${encodeURIComponent(cupomCodigo)}`
    }
    return { mensagem: 'Cupom selecionado.' }
  }, [slug])

  const marcarPromocaoLida = useCallback(async (promocaoId) => {
    await meuGendazPromocoesApi.marcarLida(promocaoId)
    await carregarBeneficios()
  }, [carregarBeneficios])

  const enviarMensagemIA = useCallback(async (mensagem, historico = []) => {
    const { data } = await clienteApi.post('/meu-gendaz/ia', {
      pergunta: mensagem,
      historico,
    })
    return data
  }, [])

  const carregarPreferenciasIA = useCallback(async () => {
    return { profissionalFavorito: null, servicoFavorito: null, diasPreferidos: [], horarioPreferido: null, frequencia: null }
  }, [])

  const atualizarPerfil = useCallback(async (dados) => {
    const { data } = await clienteApi.patch('/meu-gendaz/perfil', dados)
    setPerfilPendente(false)
    setPerfilAcesso(data)
    setCliente((prev) => ({ ...(prev || {}), ...data }))
    return data
  }, [])

  const atualizarNotificacoes = useCallback(async (dados) => {
    const { data } = await clienteApi.patch('/meu-gendaz/notificacoes', dados)
    setConfiguracoes((prev) => ({ ...prev, notificacoes: dados }))
    return data
  }, [])

  const atualizarPrivacidade = useCallback(async (dados) => {
    const { data } = await clienteApi.patch('/meu-gendaz/privacidade', dados)
    setConfiguracoes((prev) => ({ ...prev, ...dados }))
    return data
  }, [])

  const logout = useCallback(async () => {
    try {
      await clienteApi.post('/meu-gendaz/auth/logout')
    } catch { /* ignora */ }
    limparEstadoSessao()
  }, [limparEstadoSessao])

  const value = {
    cliente,
    cadastroPendente: perfilPendente,
    perfilPendente,
    perfilAcesso,
    dashboard,
    agendamentos,
    beneficios,
    configuracoes,
    servicos,
    profissionais,
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
    marcarPromocaoLida,
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

function detectarIntencaoLocal(texto) {
  const t = texto.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  if (/cancelar|remover|desmarcar|desistir/.test(t)) return 'cancelar'
  if (/reagendar|remarcar|mudar.*agendamento|trocar.*horario|trocar.*data/.test(t)) return 'reagendar'
  if (/agendar|marcar|reservar|quero|gostaria|solicitar/.test(t)) return 'agendar'
  if (/listar|quais.*servicos|servicos.*disponiveis|o que.*oferecem|precos|valores|tabela/.test(t)) return 'listar_servicos'
  if (/profissionais|quem.*atende|barbeiro|cabeleireiro|equipe|funcionarios/.test(t)) return 'listar_profissionais'
  if (/horarios|horario.*disponivel|funcionamento|aberto/.test(t)) return 'listar_horarios'
  if (/proximo|meus.*agendamentos|agendamentos.*futuros|quando.*proximo/.test(t)) return 'meus_agendamentos'
  if (/historico|passado|anterior|ultimos/.test(t)) return 'historico'
  if (/promo|cupom|desconto|beneficio|oferta/.test(t)) return 'promocoes'
  if (/quem.*voce|o que.*faz|como.*funciona|ajuda|help/.test(t)) return 'sobre'
  if (/obrigad|valeu|thanks/.test(t)) return 'agradecimento'
  if (/oi|ola|bom dia|boa tarde|boa noite|hey/.test(t)) return 'saudacao'
  return 'geral'
}

function gerarRespostaLocal(intencao, texto, contexto) {
  const { cliente, agendamentos, servicos, profissionais, beneficios } = contexto
  const nome = cliente?.nome || 'cliente'

  switch (intencao) {
    case 'saudacao': {
      const hora = new Date().getHours()
      const periodo = hora < 12 ? 'Bom dia' : hora < 18 ? 'Boa tarde' : 'Boa noite'
      return {
        resposta: `${periodo}, ${nome}!  Como posso ajudá-lo? Posso agendar, reagendar, cancelar, listar serviços ou responder dúvidas.`,
        sugestoes: ['Quero agendar', 'Ver meus agendamentos', 'Quais serviços vocês têm?'],
      }
    }
    case 'sobre': {
      return {
        resposta: `Sou a assistente virtual do estabelecimento! Posso ajudar com:\n\n• Agendar serviços\n• Reagendar compromissos\n• Cancelar agendamentos\n• Listar serviços e preços\n• Consultar promoções\n\nBasta me dizer o que precisa!`,
        sugestoes: ['Quero agendar', 'Ver serviços', 'Ver promoções'],
      }
    }
    case 'agradecimento': {
      return { resposta: `Por nada, ${nome}!  Estou sempre aqui quando precisar.` }
    }
    case 'listar_servicos': {
      if (!servicos || servicos.length === 0) {
        return {
          resposta: `${nome}, no momento não consigo listar os serviços. Acesse a aba **Agenda** para ver todos os serviços disponíveis.`,
          sugestoes: ['Ir para Agenda'],
        }
      }
      const lista = servicos.map((s, i) => `${i + 1}. ${s.nome || s.titulo} — R$ ${Number(s.valor || 0).toFixed(2)}`).join('\n')
      return {
        resposta: `Serviços disponíveis:\n\n${lista}\n\nQuer agendar algum?`,
        sugestoes: servicos.slice(0, 3).map((s) => `Agendar ${s.nome || s.titulo}`),
      }
    }
    case 'listar_profissionais': {
      if (!profissionais || profissionais.length === 0) {
        return {
          resposta: `${nome}, não consigo listar os profissionais agora. Ao agendar, você poderá escolher o profissional.`,
          sugestoes: ['Ir para Agenda'],
        }
      }
      const lista = profissionais.map((p, i) => `${i + 1}. ${p.nome}`).join('\n')
      return {
        resposta: `Nossa equipe:\n\n${lista}\n\nQuer agendar com algum deles?`,
        sugestoes: profissionais.slice(0, 3).map((p) => `Agendar com ${p.nome}`),
      }
    }
    case 'meus_agendamentos': {
      if (!agendamentos || agendamentos.length === 0) {
        return {
          resposta: `${nome}, você não possui agendamentos futuros. Que tal agendar um novo serviço?`,
          sugestoes: ['Quero agendar', 'Ver serviços'],
        }
      }
      const lista = agendamentos.map((a, i) =>
        `${i + 1}. ${a.servicoNome || a.servico || 'Serviço'} — ${a.data ? new Date(a.data + 'T12:00:00').toLocaleDateString('pt-BR') : '?'} às ${a.horaInicio || a.hora || '?'} com ${a.profissionalNome || a.profissional || '?'} [${a.status}]`
      ).join('\n')
      return {
        resposta: `Seus próximos agendamentos:\n\n${lista}\n\nPrecisa reagendar ou cancelar algum?`,
        sugestoes: ['Reagendar', 'Cancelar'],
      }
    }
    case 'cancelar': {
      if (!agendamentos || agendamentos.length === 0) {
        return { resposta: `${nome}, você não possui agendamentos para cancelar.` }
      }
      return {
        resposta: `Para cancelar, acesse a aba **Agenda**, clique em "Cancelar" no agendamento desejado e confirme.`,
        sugestoes: ['Ir para Agenda'],
      }
    }
    case 'reagendar': {
      if (!agendamentos || agendamentos.length === 0) {
        return { resposta: `${nome}, você não possui agendamentos para reagendar.` }
      }
      return {
        resposta: `Para reagendar, acesse a aba **Agenda**, clique em "Reagendar" e escolha nova data/horário.`,
        sugestoes: ['Ir para Agenda'],
      }
    }
    case 'promocoes': {
      const promos = beneficios?.promocoes || []
      if (!promos || promos.length === 0) {
        return {
          resposta: `${nome}, no momento não há promoções ativas. Acesse a aba **Benefícios** para ficar por dentro!`,
          sugestoes: ['Ir para Benefícios'],
        }
      }
      const lista = promos.map((p, i) =>
        `${i + 1}. ${p.titulo} — ${p.desconto}% OFF${p.cupom ? ` (Cupom: ${p.cupom})` : ''}\n   ${p.descricao}`
      ).join('\n\n')
      return {
        resposta: `Promoções disponíveis:\n\n${lista}\n\nQuer agendar aproveitando alguma promoção?`,
        sugestoes: ['Quero agendar', 'Ir para Benefícios'],
      }
    }
    case 'agendar': {
      return {
        resposta: `${nome}, vou te ajudar a agendar! \n\nPara criar um novo agendamento:\n1. Acesse a aba **Agenda**\n2. Clique em **"Novo agendamento"**\n3. Escolha serviço, profissional, data e horário\n4. Confirme!\n\nQuer que eu te leve para lá?`,
        sugestoes: ['Ir para Agenda', 'Ver serviços'],
      }
    }
    case 'historico': {
      return {
        resposta: `${nome}, para ver seu histórico, acesse a aba **Histórico** na sidebar.`,
        sugestoes: ['Ir para Histórico'],
      }
    }
    case 'listar_horarios': {
      return {
        resposta: `${nome}, os horários dependem do serviço e profissional. Vá na aba **Agenda**, clique em "Novo agendamento" e selecione serviço, profissional e data para ver os horários disponíveis.`,
        sugestoes: ['Ir para Agenda', 'Ver meus agendamentos'],
      }
    }
    default: {
      return {
        resposta: `${nome}, posso ajudar com:\n\n• **Agendar** um serviço\n• **Reagendar** compromisso\n• **Cancelar** agendamento\n• **Serviços** e preços\n• **Promoções** e cupons\n\nÉ só me dizer o que precisa!`,
        sugestoes: ['Quero agendar', 'Ver meus agendamentos', 'Serviços e preços'],
      }
    }
  }
}
