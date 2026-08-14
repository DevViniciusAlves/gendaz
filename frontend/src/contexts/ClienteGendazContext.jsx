import { createContext, useState, useEffect, useCallback, useRef } from 'react'
import clienteApi from '../api/clienteApi.js'
import { meuGendazPromocoesApi } from '../api/meuGendazPromocoesApi.js'

export const ClienteGendazContext = createContext()

const BENEFICIOS_FRESHNESS_MS = 5000

function isIosBrowser() {
  if (typeof navigator === 'undefined') return false
  const ua = navigator.userAgent.toLowerCase()
  return /iphone|ipod|ipad/.test(ua)
}

function isErroTransitorio(err) {
  if (!err?.response) return true
  const status = err.response.status
  return status === 0 || status >= 500 || status === 429
}

async function tentarComRetry(requester, tentativas = 2, esperaMs = 600) {
  let ultimoErro = null
  for (let tentativa = 1; tentativa <= tentativas; tentativa++) {
    try {
      return await requester()
    } catch (err) {
      ultimoErro = err
      const transitório = isErroTransitorio(err)
      if (tentativa < tentativas && transitório) {
        await new Promise((resolve) => setTimeout(resolve, esperaMs * tentativa))
        continue
      }
      throw err
    }
  }
  throw ultimoErro
}

export function ClienteGendazProvider({ children, slug }) {
  const [cliente, setCliente] = useState(null)
  const [perfilPendente, setPerfilPendente] = useState(false)
  const [perfilAcesso, setPerfilAcesso] = useState(null)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)
  const [agendamentos, setAgendamentos] = useState([])
  const [dashboard, setDashboard] = useState(null)
  const [beneficios, setBeneficios] = useState({ promocoes: [], cupons: [] })
  const [configuracoes, setConfiguracoes] = useState(null)
  const [servicos, setServicos] = useState([])
  const [profissionais, setProfissionais] = useState([])
  const sincronizandoRef = useRef(null)
  const beneficiosEmAndamentoRef = useRef(null)
  const beneficiosAtualizadoEmRef = useRef(0)


  const limparEstadoSessao = useCallback(() => {
    setCliente(null)
    setPerfilPendente(false)
    setPerfilAcesso(null)
    setDashboard(null)
    setAgendamentos([])
    setBeneficios({ promocoes: [], cupons: [] })
    beneficiosAtualizadoEmRef.current = 0
    setConfiguracoes(null)
    setServicos([])
    setProfissionais([])
  }, [slug])

  const sincronizarDados = useCallback(async ({ exigirSessao = false } = {}) => {
    if (sincronizandoRef.current) {
      return sincronizandoRef.current
    }

    const promessa = (async () => {
    try {
      setCarregando(true)
      setErro(null)

      const perfilRes = await tentarComRetry(() => clienteApi.get('/meu-gendaz/perfil', {
        skipMeuGendazLogout: !exigirSessao,
      }))

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

      await carregarBeneficios()
    } catch (err) {
      if (err?.response?.status === 401) {
        if (exigirSessao) {
          limparEstadoSessao()
          window.dispatchEvent(new CustomEvent('meu-gendaz:logout'))
        }
        setCarregando(false)
        return
      }
      setErro(err?.response?.data?.mensagem || err?.message || 'Erro ao carregar dados.')
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
    let cancelado = false
    const delayInicial = isIosBrowser() ? 1400 : 0

    const executarSincronizacaoInicial = async () => {
      await new Promise((resolve) => window.setTimeout(resolve, delayInicial))
      if (cancelado) return
      await sincronizarDados({ exigirSessao: false })
    }

    void executarSincronizacaoInicial()
    return () => {
      cancelado = true
      delete clienteApi.defaults.headers.common['X-Meu-Gendaz-Slug']
    }
  }, [slug, sincronizarDados])

  useEffect(() => {
    const lidarComLogout = () => {
      limparEstadoSessao()
      setErro(null)
      setCarregando(false)
      if (slug) {
        window.history.replaceState({}, '', `/meu-gendaz/${slug}`)
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

  const carregarBeneficios = useCallback(async ({ usarCacheRecente = false } = {}) => {
    if (beneficiosEmAndamentoRef.current) {
      return beneficiosEmAndamentoRef.current
    }

    if (usarCacheRecente && Date.now() - beneficiosAtualizadoEmRef.current < BENEFICIOS_FRESHNESS_MS) {
      return
    }

    const promessa = Promise.allSettled([
      meuGendazPromocoesApi.listar(),
      meuGendazPromocoesApi.usados(),
      meuGendazPromocoesApi.notificacoes(),
    ]).then(([promosRes, cuponsRes, notifRes]) => {
      setBeneficios((prev) => ({
        promocoes: promosRes.status === 'fulfilled' && Array.isArray(promosRes.value) ? promosRes.value : prev.promocoes || [],
        cupons: cuponsRes.status === 'fulfilled' && Array.isArray(cuponsRes.value) ? cuponsRes.value : prev.cupons || [],
        notificacoes: notifRes.status === 'fulfilled' ? (Array.isArray(notifRes.value?.notificacoes) ? notifRes.value.notificacoes : prev.notificacoes || []) : prev.notificacoes || [],
      }))
      beneficiosAtualizadoEmRef.current = Date.now()
    }).finally(() => {
      beneficiosEmAndamentoRef.current = null
    })

    beneficiosEmAndamentoRef.current = promessa
    return promessa
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

    const atualizarBeneficios = async () => {
      if (!ativo) return
      try {
        await carregarBeneficios({ usarCacheRecente: true })
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

    return () => {
      ativo = false
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
      await clienteApi.post('/meu-gendaz/auth/logout', null, { skipMeuGendazLogout: true })
    } finally {
      limparEstadoSessao()
    }
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
        resposta: `${periodo}, ${nome}!  Como posso ajudÃ¡-lo? Posso agendar, reagendar, cancelar, listar serviÃ§os ou responder dÃºvidas.`,
        sugestoes: ['Quero agendar', 'Ver meus agendamentos', 'Quais serviÃ§os vocÃªs tÃªm?'],
      }
    }
    case 'sobre': {
      return {
        resposta: `Sou a assistente virtual do estabelecimento! Posso ajudar com:\n\nâ€¢ Agendar serviÃ§os\nâ€¢ Reagendar compromissos\nâ€¢ Cancelar agendamentos\nâ€¢ Listar serviÃ§os e preÃ§os\nâ€¢ Consultar promoÃ§Ãµes\n\nBasta me dizer o que precisa!`,
        sugestoes: ['Quero agendar', 'Ver serviÃ§os', 'Ver promoÃ§Ãµes'],
      }
    }
    case 'agradecimento': {
      return { resposta: `Por nada, ${nome}!  Estou sempre aqui quando precisar.` }
    }
    case 'listar_servicos': {
      if (!servicos || servicos.length === 0) {
        return {
          resposta: `${nome}, no momento nÃ£o consigo listar os serviÃ§os. Acesse a aba **Agenda** para ver todos os serviÃ§os disponÃ­veis.`,
          sugestoes: ['Ir para Agenda'],
        }
      }
      const lista = servicos.map((s, i) => `${i + 1}. ${s.nome || s.titulo} â€” R$ ${Number(s.valor || 0).toFixed(2)}`).join('\n')
      return {
        resposta: `ServiÃ§os disponÃ­veis:\n\n${lista}\n\nQuer agendar algum?`,
        sugestoes: servicos.slice(0, 3).map((s) => `Agendar ${s.nome || s.titulo}`),
      }
    }
    case 'listar_profissionais': {
      if (!profissionais || profissionais.length === 0) {
        return {
          resposta: `${nome}, nÃ£o consigo listar os profissionais agora. Ao agendar, vocÃª poderÃ¡ escolher o profissional.`,
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
          resposta: `${nome}, vocÃª nÃ£o possui agendamentos futuros. Que tal agendar um novo serviÃ§o?`,
          sugestoes: ['Quero agendar', 'Ver serviÃ§os'],
        }
      }
      const lista = agendamentos.map((a, i) =>
        `${i + 1}. ${a.servicoNome || a.servico || 'ServiÃ§o'} â€” ${a.data ? new Date(a.data + 'T12:00:00').toLocaleDateString('pt-BR') : '?'} Ã s ${a.horaInicio || a.hora || '?'} com ${a.profissionalNome || a.profissional || '?'} [${a.status}]`
      ).join('\n')
      return {
        resposta: `Seus prÃ³ximos agendamentos:\n\n${lista}\n\nPrecisa reagendar ou cancelar algum?`,
        sugestoes: ['Reagendar', 'Cancelar'],
      }
    }
    case 'cancelar': {
      if (!agendamentos || agendamentos.length === 0) {
        return { resposta: `${nome}, vocÃª nÃ£o possui agendamentos para cancelar.` }
      }
      return {
        resposta: `Para cancelar, acesse a aba **Agenda**, clique em "Cancelar" no agendamento desejado e confirme.`,
        sugestoes: ['Ir para Agenda'],
      }
    }
    case 'reagendar': {
      if (!agendamentos || agendamentos.length === 0) {
        return { resposta: `${nome}, vocÃª nÃ£o possui agendamentos para reagendar.` }
      }
      return {
        resposta: `Para reagendar, acesse a aba **Agenda**, clique em "Reagendar" e escolha nova data/horÃ¡rio.`,
        sugestoes: ['Ir para Agenda'],
      }
    }
    case 'promocoes': {
      const promos = beneficios?.promocoes || []
      if (!promos || promos.length === 0) {
        return {
          resposta: `${nome}, no momento nÃ£o hÃ¡ promoÃ§Ãµes ativas. Acesse a aba **BenefÃ­cios** para ficar por dentro!`,
          sugestoes: ['Ir para BenefÃ­cios'],
        }
      }
      const lista = promos.map((p, i) =>
        `${i + 1}. ${p.titulo} â€” ${p.desconto}% OFF${p.cupom ? ` (Cupom: ${p.cupom})` : ''}\n   ${p.descricao}`
      ).join('\n\n')
      return {
        resposta: `PromoÃ§Ãµes disponÃ­veis:\n\n${lista}\n\nQuer agendar aproveitando alguma promoÃ§Ã£o?`,
        sugestoes: ['Quero agendar', 'Ir para BenefÃ­cios'],
      }
    }
    case 'agendar': {
      return {
        resposta: `${nome}, vou te ajudar a agendar! \n\nPara criar um novo agendamento:\n1. Acesse a aba **Agenda**\n2. Clique em **"Novo agendamento"**\n3. Escolha serviÃ§o, profissional, data e horÃ¡rio\n4. Confirme!\n\nQuer que eu te leve para lÃ¡?`,
        sugestoes: ['Ir para Agenda', 'Ver serviÃ§os'],
      }
    }
    case 'historico': {
      return {
        resposta: `${nome}, para ver seu histÃ³rico, acesse a aba **HistÃ³rico** na sidebar.`,
        sugestoes: ['Ir para HistÃ³rico'],
      }
    }
    case 'listar_horarios': {
      return {
        resposta: `${nome}, os horÃ¡rios dependem do serviÃ§o e profissional. VÃ¡ na aba **Agenda**, clique em "Novo agendamento" e selecione serviÃ§o, profissional e data para ver os horÃ¡rios disponÃ­veis.`,
        sugestoes: ['Ir para Agenda', 'Ver meus agendamentos'],
      }
    }
    default: {
      return {
        resposta: `${nome}, posso ajudar com:\n\nâ€¢ **Agendar** um serviÃ§o\nâ€¢ **Reagendar** compromisso\nâ€¢ **Cancelar** agendamento\nâ€¢ **ServiÃ§os** e preÃ§os\nâ€¢ **PromoÃ§Ãµes** e cupons\n\nÃ‰ sÃ³ me dizer o que precisa!`,
        sugestoes: ['Quero agendar', 'Ver meus agendamentos', 'ServiÃ§os e preÃ§os'],
      }
    }
  }
}


