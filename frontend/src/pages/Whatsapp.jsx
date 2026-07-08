import { CalendarPlus, CheckCircle, Copy, CreditCard, ExternalLink, Info, RefreshCw, Send, SendHorizonal, ShieldCheck, WifiOff, XCircle } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import ChatLayout from '../components/ChatLayout.jsx'
import ChatMessage from '../components/ChatMessage.jsx'
import ClientPanel from '../components/ClientPanel.jsx'
import ConversationList from '../components/ConversationList.jsx'
import Modal from '../components/Modal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { aplicarMascara, padronizarTelefone, validarTelefone } from '../utils/phoneUtils.js'
import { todayIso } from '../services/localStore.js'

const statusLabels = {
  TODAS: 'Todas',
  ABERTA: 'Aberto',
  PENDENTE: 'Pendente',
  FINALIZADA: 'Finalizado',
}

const connectionCopy = {
  disconnected: {
    icon: WifiOff,
    title: 'Conectar WhatsApp',
    subtitle: 'Conecte o WhatsApp da sua empresa para ativar a assistente IA, respostas automaticas e lembretes de agendamento.',
  },
  pairing: {
    icon: Info,
    title: 'Digite o codigo no WhatsApp',
    subtitle: 'Use o codigo de pareamento de 8 caracteres para concluir a conexao no aplicativo.',
  },
  connected: {
    icon: CheckCircle,
    title: 'WhatsApp conectado',
    subtitle: 'Sua assistente ja pode responder clientes e auxiliar nos agendamentos.',
  },
}

const defaultAssistantSpeeches = {
  boasVindas: 'Olá! Seja bem-vindo. Como posso te ajudar hoje?',
  horarios: 'Claro! Vou verificar os horarios disponiveis para voce.',
  servicos: 'Temos alguns servicos disponiveis. Me diga qual voce deseja agendar.',
  naoEntendeu: 'Desculpa, nao entendi muito bem. Pode me explicar de outra forma?',
  humano: 'Vou encaminhar sua mensagem para um atendente continuar o atendimento.',
}

function formatarData(valor) {
  if (!valor) return '-'
  const data = new Date(valor)
  if (Number.isNaN(data.getTime())) return '-'
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(data)
}

function formatarCodigoPareamento(valor) {
  return String(valor || '').trim()
}

function codigoPareamentoValido(valor) {
  return String(valor || '').trim().length >= 8
}

function chaveEstadoWhatsapp(tenantId) {
  return `agendapro_whatsapp_step_${tenantId || 'default'}`
}

function chaveConexaoConfirmada(tenantId) {
  return `agendapro_whatsapp_connected_${tenantId || 'default'}`
}

function lerEstadoWhatsapp(tenantId) {
  try {
    const raw = sessionStorage.getItem(chaveEstadoWhatsapp(tenantId))
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return null
    return parsed
  } catch {
    return null
  }
}

function salvarEstadoWhatsapp(tenantId, estado) {
  try {
    sessionStorage.setItem(chaveEstadoWhatsapp(tenantId), JSON.stringify(estado))
  } catch {
    // sem persistencia local, segue com state
  }
}

function lerConexaoConfirmada(tenantId) {
  try {
    const raw = localStorage.getItem(chaveConexaoConfirmada(tenantId))
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return null
    return parsed
  } catch {
    return null
  }
}

function salvarConexaoConfirmada(tenantId, estado) {
  try {
    localStorage.setItem(chaveConexaoConfirmada(tenantId), JSON.stringify(estado))
  } catch {
    // ignore
  }
}

function limparEstadoWhatsapp(tenantId) {
  try {
    sessionStorage.removeItem(chaveEstadoWhatsapp(tenantId))
  } catch {
    // ignore
  }
}

function limparConexaoConfirmada(tenantId) {
  try {
    localStorage.removeItem(chaveConexaoConfirmada(tenantId))
  } catch {
    // ignore
  }
}

function expirouEstadoWhatsapp(estado) {
  if (!estado?.expiresAt) return false
  const prazo = new Date(estado.expiresAt)
  return Number.isNaN(prazo.getTime()) ? false : prazo.getTime() <= Date.now()
}

function carregarFalasAssistente() {
  return defaultAssistantSpeeches
}

export default function Whatsapp() {
  const { usuario } = useAuth()
  const tenantId = usuario?.empresaId
  const [data, , { reload }] = useLocalData('whatsapp')
  const [selectedId, setSelectedId] = useState(data.conversas[0]?.id || null)
  const [texto, setTexto] = useState('')
  const [filtro, setFiltro] = useState('TODAS')
  const [mensagens, setMensagens] = useState([])
  const [carregandoMensagens, setCarregandoMensagens] = useState(false)
  const [confirmacao, setConfirmacao] = useState(null)
  const [statusConexao, setStatusConexao] = useState(null)
  const [configConexao, setConfigConexao] = useState({
    iaHabilitada: false,
    notificacoesHabilitadas: false,
    ativo: false,
    numeroConectado: '',
    nomeEmpresa: '',
    descricaoEmpresa: '',
    assistenteAtivo: false,
    mensagemBoasVindas: defaultAssistantSpeeches.boasVindas,
    respostaHorarios: defaultAssistantSpeeches.horarios,
    respostaServicos: defaultAssistantSpeeches.servicos,
    respostaNaoEntende: defaultAssistantSpeeches.naoEntendeu,
    mensagemHumano: defaultAssistantSpeeches.humano,
    linkAgendamento: '',
    servicos: [],
    horariosDisponiveis: [],
  })
  const [loadingConnection, setLoadingConnection] = useState(true)
  const [connectionBusy, setConnectionBusy] = useState(false)
  const [connectionError, setConnectionError] = useState('')
  const [connectionNotice, setConnectionNotice] = useState('')
  const [phoneInput, setPhoneInput] = useState(data.empresa?.whatsappPhone || data.empresa?.telefone || '')
  const [aguardandoPairing, setAguardandoPairing] = useState(false)
  const [pairingCode, setPairingCode] = useState('')
  const [copiedPairingCode, setCopiedPairingCode] = useState(false)
  const [assistantSpeeches, setAssistantSpeeches] = useState(defaultAssistantSpeeches)
  const [conexaoConfirmada, setConexaoConfirmada] = useState(() => lerConexaoConfirmada(tenantId))
  const pairingTimeoutRef = useRef(null)
  const statusPollingRef = useRef(null)
  const statusRef = useRef(null)
  const pairingCodeRef = useRef('')
  const statusPollingBusyRef = useRef(false)
  const statusAtualConexao = String(statusConexao?.status || '').toUpperCase()
  const sessionError = statusAtualConexao === 'SESSION_ERROR'
  const pairingFailed = statusAtualConexao === 'PAIRING_FAILED'
  const pairingExpired = statusAtualConexao === 'PAIRING_EXPIRED'
  const estadoPersistido = useMemo(() => lerEstadoWhatsapp(tenantId), [tenantId])
  const statusConfirmadoPersistido = lerConexaoConfirmada(tenantId)
  const statusConexaoConfirmada = conexaoConfirmada || statusConfirmadoPersistido
  const aguardandoFluxoPareamento = aguardandoPairing
    || statusAtualConexao === 'WAITING_PAIRING'
    || statusAtualConexao === 'GENERATING_CODE'
  const conectadoServidor = Boolean(
    statusConexao?.conectado
    || statusConexao?.connected
    || statusConexao?.whatsappConectado
    || statusAtualConexao === 'CONNECTED'
  )
  const conectado = !aguardandoFluxoPareamento
    && !sessionError
    && !pairingFailed
    && !pairingExpired
    && (conectadoServidor || Boolean(statusConexaoConfirmada?.connected))
  const conversa = useMemo(
    () => data.conversas.find((item) => item.id === selectedId) || null,
    [data.conversas, selectedId],
  )
  const cliente = useMemo(
    () => data.clientes.find((item) => item.id === conversa?.clienteId) || null,
    [conversa?.clienteId, data.clientes],
  )
  const conversasFiltradas = useMemo(
    () => data.conversas.filter((item) => (filtro === 'TODAS' ? true : item.status === filtro)),
    [data.conversas, filtro],
  )
  const primeiroServico = data.servicos[0]
  const primeiroProfissional = data.profissionais[0]
  const agendamentoCliente = data.agendamentos.find((item) => item.clienteId === cliente?.id)

  async function carregarConexao() {
    if (!tenantId) return
    setLoadingConnection(true)
    setConnectionError('')
    try {
      const persistido = lerEstadoWhatsapp(tenantId)
      const persistidoValido = persistido && !expirouEstadoWhatsapp(persistido)
      const [status, config] = await Promise.all([
        appApi.getWhatsappStatus(tenantId),
        appApi.getWhatsappConfig(tenantId),
      ])
      statusRef.current = status
      setStatusConexao(status)
      const statusApi = String(status?.status || '').toUpperCase()
      const conectadoServidor = Boolean(
        status?.conectado
        || status?.connected
        || status?.whatsappConectado
        || statusApi === 'CONNECTED'
      )
      setConfigConexao({
        iaHabilitada: Boolean(config?.iaHabilitada),
        notificacoesHabilitadas: Boolean(config?.notificacoesHabilitadas),
        ativo: Boolean(config?.ativo),
        numeroConectado: config?.numeroConectado || status?.numero || '',
        nomeEmpresa: config?.nomeEmpresa || data.empresa?.nomeFantasia || '',
        descricaoEmpresa: config?.descricaoEmpresa || '',
        assistenteAtivo: Boolean(config?.assistenteAtivo ?? config?.iaHabilitada),
        mensagemBoasVindas: config?.mensagemBoasVindas || defaultAssistantSpeeches.boasVindas,
        respostaHorarios: config?.respostaHorarios || defaultAssistantSpeeches.horarios,
        respostaServicos: config?.respostaServicos || defaultAssistantSpeeches.servicos,
        respostaNaoEntende: config?.respostaNaoEntende || defaultAssistantSpeeches.naoEntendeu,
        mensagemHumano: config?.mensagemHumano || defaultAssistantSpeeches.humano,
        linkAgendamento: config?.linkAgendamento || '',
        servicos: Array.isArray(config?.servicos) ? config.servicos : [],
        horariosDisponiveis: Array.isArray(config?.horariosDisponiveis) ? config.horariosDisponiveis : [],
      })
      setPhoneInput(status?.numero || config?.numeroConectado || data.empresa?.whatsappPhone || data.empresa?.telefone || '')
      const pairingAtual = status?.pairingCode || status?.code || ''
      if (conectadoServidor) {
        const confirmado = {
          connected: true,
          conectado: true,
          whatsappConectado: true,
          status: 'CONNECTED',
          statusLabel: 'WhatsApp conectado',
          numeroConectado: status?.numero || status?.numeroConectado || status?.phoneNumber || config?.numeroConectado || '',
          phoneNumber: status?.numero || status?.numeroConectado || status?.phoneNumber || config?.numeroConectado || '',
          connectedAt: status?.connectedAt || new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }
        setConexaoConfirmada(confirmado)
        salvarConexaoConfirmada(tenantId, confirmado)
        clearPairingTimeout()
        clearStatusPolling()
        pairingCodeRef.current = ''
        setPairingCode('')
        setAguardandoPairing(false)
        limparEstadoWhatsapp(tenantId)
      } else if (codigoPareamentoValido(pairingAtual)) {
        pairingCodeRef.current = pairingAtual
        setPairingCode(pairingAtual)
        setAguardandoPairing(true)
        salvarEstadoWhatsapp(tenantId, {
          empresaId: tenantId,
          status: 'WAITING_PAIRING',
          phoneNumber: status?.numero || config?.numeroConectado || phoneInput || '',
          pairingCode: pairingAtual,
          expiresAt: status?.expiresAt || persistidoValido?.expiresAt || new Date(Date.now() + 75 * 1000).toISOString(),
        })
      } else if (persistidoValido && persistidoValido.status === 'WAITING_PAIRING' && codigoPareamentoValido(persistidoValido.pairingCode)) {
        pairingCodeRef.current = String(persistidoValido.pairingCode).trim()
        setPairingCode(String(persistidoValido.pairingCode).trim())
        setAguardandoPairing(true)
        if (persistidoValido.phoneNumber) {
          setPhoneInput(padronizarTelefone(persistidoValido.phoneNumber))
        }
      } else {
        clearStatusPolling()
        pairingCodeRef.current = ''
        setAguardandoPairing(false)
        if (!persistidoValido) {
          limparEstadoWhatsapp(tenantId)
        }
      }
      setAssistantSpeeches({
        boasVindas: config?.mensagemBoasVindas || defaultAssistantSpeeches.boasVindas,
        horarios: config?.respostaHorarios || defaultAssistantSpeeches.horarios,
        servicos: config?.respostaServicos || defaultAssistantSpeeches.servicos,
        naoEntendeu: config?.respostaNaoEntende || defaultAssistantSpeeches.naoEntendeu,
        humano: config?.mensagemHumano || defaultAssistantSpeeches.humano,
      })
    } catch (error) {
      if (statusConexaoConfirmada?.connected) {
        const fallback = {
          ...statusConexaoConfirmada,
          connected: true,
          conectado: true,
          whatsappConectado: true,
          status: 'CONNECTED',
          statusLabel: 'WhatsApp conectado',
          message: 'WhatsApp conectado anteriormente. Clique em atualizar para verificar novamente.',
        }
        statusRef.current = fallback
        setStatusConexao(fallback)
        setConexaoConfirmada(fallback)
        setConnectionNotice('WhatsApp conectado anteriormente. Clique em atualizar para verificar novamente.')
        clearPairingTimeout()
        clearStatusPolling()
        setAguardandoPairing(false)
        setPairingCode('')
        pairingCodeRef.current = ''
        return
      }
      setConnectionError(error.response?.data?.message || error.response?.data?.mensagem || 'Nao foi possivel carregar o status do WhatsApp.')
      if (statusAtualConexao === 'SESSION_ERROR' || statusAtualConexao === 'PAIRING_FAILED' || statusAtualConexao === 'PAIRING_EXPIRED') {
        setConnectionError(statusConexao?.message || 'A sessão do WhatsApp ficou inválida. Desconecte e conecte novamente para continuar.')
      }
    } finally {
      setLoadingConnection(false)
    }
  }

  function clearPairingTimeout() {
    if (pairingTimeoutRef.current) {
      clearTimeout(pairingTimeoutRef.current)
      pairingTimeoutRef.current = null
    }
  }

  function clearStatusPolling() {
    if (statusPollingRef.current) {
      clearInterval(statusPollingRef.current)
      statusPollingRef.current = null
    }
  }

  async function cancelarConexaoLocal(mensagem) {
    clearPairingTimeout()
    clearStatusPolling()
    statusRef.current = null
    pairingCodeRef.current = ''
    try {
      await appApi.desconectarWhatsapp(tenantId)
    } catch {
      // a limpeza local continua para evitar sessÃƒÂ£o presa no frontend
    }
    const desconectado = {
      status: 'DISCONNECTED',
      statusLabel: 'Desconectado',
      conectado: false,
      connected: false,
      whatsappConectado: false,
      numero: '',
      numeroConectado: '',
      phoneNumber: '',
      pairingCode: null,
      code: null,
      connectedAt: null,
      disconnectedAt: new Date().toISOString(),
    }
    setStatusConexao(desconectado)
    statusRef.current = desconectado
    setConexaoConfirmada(null)
    limparConexaoConfirmada(tenantId)
    setConfigConexao((atual) => ({ ...atual, ativo: false, numeroConectado: '' }))
    setAguardandoPairing(false)
    setPairingCode('')
    setConnectionBusy(false)
    limparEstadoWhatsapp(tenantId)
    if (mensagem) {
      setConnectionNotice(mensagem)
    }
  }

  useEffect(() => {
    carregarConexao()
  }, [tenantId])

  useEffect(() => {
    setConexaoConfirmada(lerConexaoConfirmada(tenantId))
  }, [tenantId])

  useEffect(() => () => {
    clearPairingTimeout()
    clearStatusPolling()
  }, [])

  useEffect(() => {
    if (!tenantId) return undefined
    const deveMonitorar = aguardandoPairing && !conectado && !sessionError && !pairingFailed && !pairingExpired
    if (!deveMonitorar) {
      clearStatusPolling()
      return undefined
    }
    if (!statusPollingRef.current) {
      statusPollingRef.current = window.setInterval(() => {
        if (statusPollingBusyRef.current) return
        void atualizarStatusManual(true)
      }, 3000)
    }
    return () => {
      clearStatusPolling()
    }
  }, [tenantId, aguardandoPairing, conectado, sessionError, pairingFailed, pairingExpired])

  useEffect(() => {
    if (!selectedId && data.conversas[0]?.id) {
      setSelectedId(data.conversas[0].id)
    }
  }, [data.conversas, selectedId])

  useEffect(() => {
    let ativo = true
    async function carregarMensagens() {
      if (!selectedId) {
        setMensagens([])
        return
      }
      setCarregandoMensagens(true)
      try {
        const lista = await appApi.carregarMensagensConversa(selectedId)
        if (ativo) setMensagens(lista)
      } catch (error) {
        if (ativo) setConnectionError(error.response?.data?.mensagem || 'Nao foi possivel carregar as mensagens da conversa.')
      } finally {
        if (ativo) setCarregandoMensagens(false)
      }
    }
    carregarMensagens()
    return () => {
      ativo = false
    }
  }, [selectedId])

  useEffect(() => {
    setPhoneInput(statusConexao?.numero || configConexao.numeroConectado || data.empresa?.whatsappPhone || data.empresa?.telefone || '')
  }, [statusConexao?.numero, configConexao.numeroConectado, data.empresa?.telefone, data.empresa?.whatsappPhone])

  useEffect(() => {
    if (!tenantId) return
    const persistido = lerEstadoWhatsapp(tenantId)
    if (!persistido) return
    if (expirouEstadoWhatsapp(persistido)) {
      limparEstadoWhatsapp(tenantId)
      return
    }
    if (persistido.status === 'WAITING_PAIRING' && codigoPareamentoValido(persistido.pairingCode)) {
      setAguardandoPairing(true)
      setPairingCode(String(persistido.pairingCode).trim())
      pairingCodeRef.current = String(persistido.pairingCode).trim()
      if (persistido.phoneNumber) {
        setPhoneInput(padronizarTelefone(persistido.phoneNumber))
      }
    }
  }, [tenantId])

  async function conectarWhatsapp() {
    const telefone = padronizarTelefone(phoneInput)
    if (!telefone) {
      setConnectionError('Digite um numero de WhatsApp valido.')
      return
    }
    setConnectionBusy(true)
    setConnectionError('')
    setConnectionNotice('')
    clearPairingTimeout()
    try {
      const response = await appApi.conectarWhatsapp(telefone)
      const pairingRecebido = response?.pairingCode || response?.code || ''
      if (!codigoPareamentoValido(pairingRecebido)) {
        setConnectionError('Nao foi possivel gerar um codigo valido. Tente novamente.')
        return
      }
      setStatusConexao((atual) => ({
        ...atual,
        conectado: false,
        numero: telefone,
        pairingCode: pairingRecebido,
        code: pairingRecebido,
      }))
      statusRef.current = {
        ...(statusRef.current || {}),
        status: 'WAITING_PAIRING',
        conectado: false,
        connected: false,
        pairingCode: pairingRecebido,
        code: pairingRecebido,
        numero: telefone,
      }
      pairingCodeRef.current = pairingRecebido
      setPairingCode(pairingRecebido)
      setAguardandoPairing(true)
      setConnectionNotice(response.message || 'Codigo de pareamento gerado.')
      salvarEstadoWhatsapp(tenantId, {
        empresaId: tenantId,
        status: 'WAITING_PAIRING',
        phoneNumber: telefone,
        pairingCode: pairingRecebido,
        expiresAt: response?.expiresAt || new Date(Date.now() + 75 * 1000).toISOString(),
      })
      const codigoGerado = pairingRecebido
      const prazoExpiracao = response?.expiresAt ? new Date(response.expiresAt).getTime() : Date.now() + 75 * 1000
      pairingTimeoutRef.current = window.setTimeout(() => {
        const aindaMesmoCodigo = String(pairingCodeRef.current || '').trim() === codigoGerado
        const conectadoAtual = Boolean(statusRef.current?.conectado || statusRef.current?.connected || String(statusRef.current?.status || '').toUpperCase() === 'CONNECTED')
        if (!conectadoAtual && aindaMesmoCodigo) {
          setConnectionError('O codigo expirou. Gere um novo codigo de conexao.')
          setConnectionNotice('')
          limparEstadoWhatsapp(tenantId)
          cancelarConexaoLocal()
        }
      }, Math.max(5000, prazoExpiracao - Date.now()))
    } catch (error) {
      setConnectionError(error.response?.data?.message || error.response?.data?.mensagem || 'Nao foi possivel gerar o codigo de conexao. Verifique o numero informado e tente novamente.')
    } finally {
      setConnectionBusy(false)
    }
  }

  async function atualizarStatusManual(silencioso = false) {
    if (!tenantId) return
    if (silencioso) {
      if (statusPollingBusyRef.current) return
      statusPollingBusyRef.current = true
    } else {
      setConnectionBusy(true)
      setConnectionError('')
      setConnectionNotice('')
    }
    try {
      const status = await appApi.getWhatsappStatus(tenantId)
      statusRef.current = status
      setStatusConexao(status)
      setConfigConexao((atual) => ({
        ...atual,
        ativo: Boolean(status?.conectado ?? status?.connected ?? false),
        numeroConectado: status?.numero || status?.numeroConectado || atual.numeroConectado || '',
      }))
      const statusApi = String(status?.status || '').toUpperCase()
      if (statusApi === 'CONNECTED') {
        const confirmado = {
          connected: true,
          conectado: true,
          whatsappConectado: true,
          status: 'CONNECTED',
          statusLabel: 'WhatsApp conectado',
          numeroConectado: status?.numero || status?.numeroConectado || status?.phoneNumber || '',
          phoneNumber: status?.numero || status?.numeroConectado || status?.phoneNumber || '',
          connectedAt: status?.connectedAt || new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }
        setConexaoConfirmada(confirmado)
        salvarConexaoConfirmada(tenantId, confirmado)
        clearPairingTimeout()
        clearStatusPolling()
        pairingCodeRef.current = ''
        setAguardandoPairing(false)
        setPairingCode('')
        limparEstadoWhatsapp(tenantId)
        setConnectionError('')
        if (!silencioso) {
          setConnectionNotice('WhatsApp conectado com sucesso.')
        }
        return
      }
      if (statusApi === 'DISCONNECTED') {
        clearPairingTimeout()
        clearStatusPolling()
        pairingCodeRef.current = ''
        setAguardandoPairing(false)
        setPairingCode('')
        limparEstadoWhatsapp(tenantId)
        limparConexaoConfirmada(tenantId)
        const desconectado = {
          status: 'DISCONNECTED',
          statusLabel: 'Desconectado',
          conectado: false,
          connected: false,
          whatsappConectado: false,
          numero: status?.numero || status?.numeroConectado || status?.phoneNumber || '',
          numeroConectado: '',
          phoneNumber: status?.phoneNumber || '',
          connectedAt: null,
          disconnectedAt: status?.disconnectedAt || new Date().toISOString(),
        }
        statusRef.current = desconectado
        setStatusConexao(desconectado)
        setConexaoConfirmada(null)
        setConfigConexao((atual) => ({
          ...atual,
          ativo: false,
          numeroConectado: '',
        }))
        if (!silencioso) {
          setConnectionNotice('WhatsApp desconectado com sucesso.')
        }
        return
      }
      if (statusApi === 'SESSION_ERROR' || statusApi === 'PAIRING_FAILED' || statusApi === 'PAIRING_EXPIRED') {
        clearPairingTimeout()
        clearStatusPolling()
        setAguardandoPairing(false)
        setPairingCode('')
        limparEstadoWhatsapp(tenantId)
        limparConexaoConfirmada(tenantId)
        setConexaoConfirmada(null)
        if (!silencioso) {
          setConnectionError(status?.message || 'A sessão do WhatsApp ficou inválida. Desconecte e conecte novamente para continuar.')
        }
        return
      }
      const pairingAtual = status?.pairingCode || status?.code || ''
      if (codigoPareamentoValido(pairingAtual)) {
        pairingCodeRef.current = pairingAtual
        setPairingCode(pairingAtual)
        setAguardandoPairing(true)
        salvarEstadoWhatsapp(tenantId, {
          empresaId: tenantId,
          status: 'WAITING_PAIRING',
          phoneNumber: status?.numero || status?.phoneNumber || phoneInput || '',
          pairingCode: pairingAtual,
          expiresAt: status?.expiresAt || estadoPersistido?.expiresAt || null,
        })
      } else if (statusApi === 'DISCONNECTED') {
        clearPairingTimeout()
        clearStatusPolling()
        pairingCodeRef.current = ''
        setAguardandoPairing(false)
        setPairingCode('')
        limparEstadoWhatsapp(tenantId)
        if (!silencioso) {
          setConnectionNotice('')
        }
      }
    } catch (error) {
      if (!silencioso) {
        setConnectionError(error.response?.data?.message || error.response?.data?.mensagem || 'Nao foi possivel consultar o status do WhatsApp.')
      }
    } finally {
      if (silencioso) {
        statusPollingBusyRef.current = false
      } else {
        setConnectionBusy(false)
      }
    }
  }

  async function desconectarWhatsapp() {
    setConnectionBusy(true)
    setConnectionError('')
    setConnectionNotice('')
    try {
      limparConexaoConfirmada(tenantId)
      setConexaoConfirmada(null)
      await cancelarConexaoLocal('WhatsApp desconectado com sucesso.')
    } catch (error) {
      setConnectionError(error.response?.data?.message || error.response?.data?.mensagem || 'Nao foi possivel desconectar o WhatsApp.')
    } finally {
      setConnectionBusy(false)
    }
  }

  async function salvarConfiguracaoWhatsapp(proxima) {
    if (!tenantId) return
    setConfigConexao((atual) => ({ ...atual, ...proxima }))
    try {
      await appApi.salvarWhatsappConfig(tenantId, {
        empresaId: tenantId,
        secretariaIaAtiva: proxima.iaHabilitada,
        notificacoesAutomaticas: proxima.notificacoesHabilitadas,
        descricaoEmpresa: proxima.descricaoEmpresa || configConexao.descricaoEmpresa || '',
        mensagemBoasVindas: proxima.mensagemBoasVindas || assistantSpeeches.boasVindas,
        respostaHorarios: proxima.respostaHorarios || assistantSpeeches.horarios,
        respostaServicos: proxima.respostaServicos || assistantSpeeches.servicos,
        respostaNaoEntende: proxima.respostaNaoEntende || assistantSpeeches.naoEntendeu,
        mensagemHumano: proxima.mensagemHumano || assistantSpeeches.humano,
      })
    } catch (error) {
      setConnectionError(error.response?.data?.message || error.response?.data?.mensagem || 'Nao foi possivel salvar a configuracao do WhatsApp.')
    }
  }

  const restauradoDeStorage = estadoPersistido && !expirouEstadoWhatsapp(estadoPersistido)
  const etapaRestaurada = useMemo(() => {
    if (conectado) return 'connected'
    if (aguardandoPairing || (restauradoDeStorage && estadoPersistido?.status === 'WAITING_PAIRING')) return 'pairing'
    return 'disconnected'
  }, [conectado, aguardandoPairing, restauradoDeStorage, estadoPersistido])

  async function copiarCodigoPareamento() {
    const codigo = pairingCodeExibido
    if (!codigo) return
    try {
      await navigator.clipboard.writeText(codigo)
      setCopiedPairingCode(true)
      window.setTimeout(() => setCopiedPairingCode(false), 1800)
    } catch {
      setConnectionError('Nao foi possivel copiar o codigo. Selecione o codigo manualmente.')
    }
  }

  async function salvarFalasAssistente(event) {
    event.preventDefault()
    if (!tenantId) return
    try {
      const proximaConfig = {
        empresaId: tenantId,
        notificacoesAutomaticas: configConexao.notificacoesHabilitadas,
        secretariaIaAtiva: configConexao.iaHabilitada,
        descricaoEmpresa: configConexao.descricaoEmpresa,
        mensagemBoasVindas: assistantSpeeches.boasVindas,
        respostaHorarios: assistantSpeeches.horarios,
        respostaServicos: assistantSpeeches.servicos,
        respostaNaoEntende: assistantSpeeches.naoEntendeu,
        mensagemHumano: assistantSpeeches.humano,
      }
      await appApi.salvarWhatsappConfig(tenantId, proximaConfig)
      setConfigConexao((atual) => ({
        ...atual,
        ...proximaConfig,
      }))
      setConnectionNotice('Falas da assistente salvas com sucesso.')
    } catch {
      setConnectionError('Nao foi possivel salvar as falas da assistente.')
    }
  }

  async function adicionarMensagem(conteudo) {
    const mensagem = conteudo.trim()
    if (!mensagem || !conversa) return
    if (mensagem.length > 500) return
    try {
      await appApi.enviarMensagem(conversa.id, mensagem)
      setTexto('')
      const atualizadas = await appApi.carregarMensagensConversa(conversa.id)
      setMensagens(atualizadas)
    } catch (error) {
      setConnectionError(error.response?.data?.message || error.response?.data?.mensagem || 'Nao foi possivel enviar a mensagem.')
    }
  }

  function abrirConfirmacao(titulo, conteudo, onConfirm) {
    setConfirmacao({ titulo, conteudo, onConfirm })
  }

  function finalizarConversa() {
    if (!conversa) return
    abrirConfirmacao('Finalizar conversa', 'Finalizar esta conversa agora?', async () => {
      await appApi.finalizarConversa(conversa.id)
      await reload(true)
    })
  }

  async function confirmarMensagem() {
    if (!confirmacao) return
    try {
      await confirmacao.onConfirm?.()
      await reload(true)
      setConfirmacao(null)
    } catch (error) {
      setConnectionError(error.response?.data?.message || error.response?.data?.mensagem || 'Nao foi possivel concluir a acao.')
    }
  }

  const podeGerarCodigo = phoneInput && phoneInput.replace(/\D/g, '').length === 13

  const statusDisplay = etapaRestaurada
  const copy = connectionCopy[statusDisplay]
  const Icon = copy.icon
  const pairingCodeExibido = codigoPareamentoValido(pairingCode)
    ? String(pairingCode).trim()
    : codigoPareamentoValido(statusConexao?.pairingCode || statusConexao?.code)
      ? String(statusConexao?.pairingCode || statusConexao?.code).trim()
      : ''

  if (loadingConnection) {
    return (
      <section className="page whatsapp-official-page">
        <div className="whatsapp-official-card">
          <RefreshCw className="spin" size={26} />
          <h1>Carregando WhatsApp</h1>
          <p>Verificando status e configuracao da conta.</p>
        </div>
      </section>
    )
  }

  if (!conectado && !aguardandoPairing) {
    return (
      <section className="page whatsapp-official-page">
        <div className="whatsapp-official-card">
          <div className="whatsapp-official-head">
            <span className="whatsapp-status-icon"><Icon size={24} /></span>
            <span className="whatsapp-status-pill">Desconectado</span>
          </div>
          <h1>{copy.title}</h1>
          <p>{copy.subtitle}</p>
          <p className="whatsapp-status-note">O fluxo usa codigo de pareamento de 8 caracteres, sem QR Code.</p>
          {(sessionError || pairingFailed || pairingExpired) && (
            <p className="form-error">{pairingExpired ? 'O codigo expirou. Gere um novo codigo de conexao.' : 'A sessao do WhatsApp ficou invalida. Desconecte e conecte novamente para continuar.'}</p>
          )}
          {connectionBusy && <p className="whatsapp-status-note">Gerando codigo de conexao...</p>}
          <div className="whatsapp-connect-phone">
            <label className="field">
              <span>Numero do WhatsApp</span>
              <input
                inputMode="numeric"
                maxLength={19}
                value={phoneInput}
                onChange={(event) => setPhoneInput(aplicarMascara(event.target.value))}
                placeholder="+55 (65) 99999-9999"
              />
              <small className="field-hint">Formato: +55 (DDD) 99999-9999</small>
            </label>
          </div>
          {connectionError && <p className="form-error">{connectionError}</p>}
          {connectionNotice && <p className="success-text">{connectionNotice}</p>}
          <div className="whatsapp-official-actions">
            <Button icon={ExternalLink} type="button" onClick={conectarWhatsapp} disabled={connectionBusy || !podeGerarCodigo}>
              {connectionBusy ? 'Gerando...' : sessionError ? 'Reconectar WhatsApp' : 'Gerar codigo de conexao'}
            </Button>
            <Button variant="secondary" icon={RefreshCw} type="button" onClick={atualizarStatusManual} disabled={connectionBusy}>Atualizar status</Button>
          </div>
        </div>
      </section>
    )
  }

  if (aguardandoPairing && !conectado) {
    return (
      <section className="page whatsapp-official-page">
        <div className="whatsapp-official-card">
          <div className="whatsapp-official-head">
            <span className="whatsapp-status-icon"><Icon size={24} /></span>
            <span className="whatsapp-status-pill">Aguardando pareamento</span>
          </div>
          <h1>{copy.title}</h1>
          <p>{copy.subtitle}</p>
          {connectionBusy && <p className="whatsapp-status-note">Conectando...</p>}
          <div className="whatsapp-pairing-code">
            <span>Codigo de pareamento</span>
            <strong className={pairingCodeExibido ? 'whatsapp-pairing-code-value' : 'whatsapp-code-placeholder'}>{pairingCodeExibido || 'Aguardando codigo completo...'}</strong>
          </div>
          <div className="whatsapp-pairing-status">
            <span className="whatsapp-status-pill">Codigo gerado / aguardando conexao</span>
            <Button variant="secondary" icon={Copy} type="button" onClick={copiarCodigoPareamento} disabled={!codigoPareamentoValido(pairingCodeExibido)}>
              {copiedPairingCode ? 'Codigo copiado' : 'Copiar codigo'}
            </Button>
          </div>
          <div className="whatsapp-tutorial">
            <strong>Como conectar:</strong>
            <ol className="whatsapp-requirements">
              <li>Abra o WhatsApp no celular.</li>
              <li>Toque em Configuracoes.</li>
              <li>Entre em Dispositivos conectados.</li>
              <li>Toque em Conectar um dispositivo.</li>
              <li>Escolha conectar com numero ou codigo, se aparecer.</li>
              <li>Digite o codigo exibido aqui no painel.</li>
            </ol>
          </div>
          {connectionError && <p className="form-error">{connectionError}</p>}
          {connectionNotice && <p className="success-text">{connectionNotice}</p>}
          <div className="whatsapp-official-actions">
            <Button variant="secondary" icon={RefreshCw} type="button" onClick={atualizarStatusManual} disabled={connectionBusy}>Atualizar status</Button>
            <Button
              variant="ghost"
              icon={XCircle}
              type="button"
              onClick={async () => {
                await cancelarConexaoLocal('Pareamento cancelado.')
                setConnectionError('')
              }}
            >
              Cancelar conexao
            </Button>
          </div>
        </div>
      </section>
    )
  }

  return (
    <section className="page page-full">
      <div className="page-title">
        <span className="section-kicker">WhatsApp</span>
        <h1>WhatsApp conectado</h1>
        <p>Sua assistente ja pode responder clientes e auxiliar nos agendamentos.</p>
      </div>

      <section className="panel whatsapp-connected-panel">
        <div>
          <span className="whatsapp-connected-badge"><CheckCircle size={14} />Conectado</span>
          <h2><CheckCircle size={18} style={{ color: 'var(--primary)', verticalAlign: 'middle', marginRight: '8px' }} />{configConexao.numeroConectado || statusConexao?.numero || phoneInput || '-'}</h2>
          <p>Status conectado. Ultima atualizacao: <strong>{formatarData(statusConexao?.connectedAt || new Date())}</strong></p>
        </div>
        <div className="whatsapp-connected-actions">
          <Button variant="secondary" icon={RefreshCw} type="button" onClick={atualizarStatusManual} disabled={connectionBusy}>Atualizar status</Button>
          <Button variant="ghost" icon={XCircle} type="button" onClick={desconectarWhatsapp} disabled={connectionBusy}>Desconectar</Button>
        </div>
      </section>

      <section className="panel whatsapp-config-panel">
        <div className="panel-head">
          <div>
            <span className="section-kicker">Falas da assistente</span>
            <h2>Configuracao inicial do bot</h2>
          </div>
          <Button variant="secondary" icon={RefreshCw} type="button" onClick={() => setAssistantSpeeches(carregarFalasAssistente(tenantId))}>
            Restaurar padrao
          </Button>
        </div>
        <form className="whatsapp-speeches-grid" onSubmit={salvarFalasAssistente}>
          <label className="field">
            <span>Descrição da empresa (o que é a empresa, foco, especialidade)</span>
            <textarea
              rows="3"
              value={configConexao.descricaoEmpresa}
              onChange={(event) => setConfigConexao((atual) => ({ ...atual, descricaoEmpresa: event.target.value }))}
              placeholder="Ex: Barbearia especializada em cortes masculinos modernos e barba."
            />
          </label>
          <label className="field">
            <span>Mensagem de boas-vindas</span>
            <textarea rows="3" value={assistantSpeeches.boasVindas} onChange={(event) => setAssistantSpeeches((atual) => ({ ...atual, boasVindas: event.target.value }))} />
          </label>
          <label className="field field-wide">
            <span>Resposta quando o cliente pergunta sobre servicos</span>
            <textarea rows="3" value={assistantSpeeches.servicos} onChange={(event) => setAssistantSpeeches((atual) => ({ ...atual, servicos: event.target.value }))} />
          </label>
          <div className="whatsapp-speeches-actions">
            <Button type="submit" icon={ShieldCheck}>Salvar falas da assistente</Button>
          </div>
        </form>
      </section>

      {(connectionNotice || connectionError) && (
        <div className="panel whatsapp-inline-feedback">
          {connectionNotice && <p className="success-text">{connectionNotice}</p>}
          {connectionError && <p className="form-error">{connectionError}</p>}
        </div>
      )}

      <ChatLayout
        left={(
          <ConversationList
            conversas={conversasFiltradas}
            selectedId={selectedId}
            onSelect={setSelectedId}
          >
            <div className="conversation-filters">
              {Object.entries(statusLabels).map(([key, label]) => (
                <button key={key} className={filtro === key ? 'filter-chip active' : 'filter-chip'} onClick={() => setFiltro(key)} type="button">
                  {label}
                </button>
              ))}
            </div>
          </ConversationList>
        )}
        center={(
          <section className="chat-center">
            <header className="chat-header">
              <div>
                <strong>{conversa?.clienteNome || 'Selecione uma conversa'}</strong>
                <span>{conversa?.clienteTelefone || 'Central de mensagens'}</span>
              </div>
              <StatusBadge status={conversa?.status} />
            </header>
            <div className="chat-body">
              {conversa ? (
                carregandoMensagens ? (
                  <div className="empty-state">
                    <strong>Carregando mensagens</strong>
                    <p>Aguarde um instante enquanto buscamos o historico desta conversa.</p>
                  </div>
                ) : mensagens.map((mensagem) => <ChatMessage key={mensagem.id} mensagem={mensagem} />)
              ) : (
                <div className="empty-state">
                  <strong>Selecione uma conversa</strong>
                  <p>Escolha um cliente na lista lateral para comecar o atendimento.</p>
                </div>
              )}
            </div>
            <div className="chat-actions">
              <Button variant="secondary" icon={SendHorizonal} disabled={!conversa} onClick={() => abrirConfirmacao('Enviar horarios', 'Enviar horarios disponiveis para este cliente?', async () => {
                await appApi.enviarHorarios({ conversaId: conversa.id, profissionalId: primeiroProfissional.id, servicoId: primeiroServico.id, data: todayIso() })
              })}>Horarios</Button>
              <Button variant="secondary" icon={CalendarPlus} disabled={!conversa || !cliente || !primeiroServico || !primeiroProfissional} onClick={() => abrirConfirmacao('Criar agendamento', 'Criar agendamento para este atendimento?', async () => {
                await appApi.criarAgendamento({ clienteId: cliente.id, servicoId: primeiroServico.id, profissionalId: primeiroProfissional.id, data: todayIso(), horaInicio: '15:00', observacoes: 'Criado pela central de WhatsApp.' })
                await appApi.enviarMensagem(conversa.id, 'Agendamento criado para hoje as 15:00.')
              })}>Criar agendamento</Button>
              <Button variant="secondary" icon={CheckCircle} disabled={!conversa || !cliente} onClick={() => abrirConfirmacao('Confirmar consulta', 'Confirmar consulta deste cliente?', async () => {
                if (agendamentoCliente && agendamentoCliente.status !== 'CONFIRMADO') {
                  await appApi.confirmarAgendamento(agendamentoCliente.id)
                }
                await appApi.enviarMensagem(conversa.id, 'Consulta confirmada.')
              })}>Confirmar</Button>
              <Button variant="secondary" icon={CreditCard} disabled={!conversa || !cliente || !primeiroServico} onClick={() => abrirConfirmacao('Marcar pagamento', 'Criar pagamento pendente para este cliente?', async () => {
                await appApi.criarPagamento({ agendamentoId: agendamentoCliente?.id, clienteId: cliente.id, valor: primeiroServico.valor, metodoPagamento: 'PIX' })
                await appApi.enviarMensagem(conversa.id, 'Pagamento registrado para acompanhamento.')
              })}>Pagamento</Button>
              <Button variant="ghost" icon={XCircle} onClick={finalizarConversa} disabled={!conversa}>Finalizar</Button>
            </div>
            <form className="message-form" onSubmit={(event) => { event.preventDefault(); adicionarMensagem(texto) }}>
              <div className="message-input-wrap">
                <input maxLength={500} value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="Digite uma mensagem" disabled={!conversa} />
                <small className={texto.length >= 500 ? 'field-hint limit-reached' : 'field-hint'}>
                  {texto.length >= 500 ? 'Limite de caracteres atingido.' : 'Mensagem com ate 500 caracteres.'}
                  <strong>{texto.length}/500</strong>
                </small>
              </div>
              <Button type="submit" icon={Send} disabled={!conversa}>Enviar</Button>
            </form>
          </section>
        )}
        right={<ClientPanel cliente={cliente} agendamentos={data.agendamentos} />}
      />

      <Modal title={confirmacao?.titulo || 'Confirmar acao'} open={Boolean(confirmacao)} onClose={() => setConfirmacao(null)}>
        <div className="confirm-box">
          <p>{confirmacao?.conteudo}</p>
          <div className="confirm-actions">
            <Button variant="secondary" onClick={() => setConfirmacao(null)}>Cancelar</Button>
            <Button onClick={confirmarMensagem}>Confirmar</Button>
          </div>
        </div>
      </Modal>
    </section>
  )
}
