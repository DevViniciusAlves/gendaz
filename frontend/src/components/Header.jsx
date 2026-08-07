import { Bell, ChevronDown, LogOut, UserCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../contexts/AuthContext.jsx'
import { appApi } from '../api/appApi.js'
import { PLANOS } from '../services/localStore.js'
import ThemeToggle from './ThemeToggle.jsx'

function hojeISO() {
  return new Date().toISOString().slice(0, 10)
}

function diasRestantesDe(dataFimISO) {
  if (!dataFimISO) return null
  const fim = new Date(`${String(dataFimISO).slice(0, 10)}T12:00:00`).getTime()
  return Math.max(0, Math.ceil((fim - Date.now()) / 86400000))
}

function nomePlanoDe(codigo) {
  return PLANOS[codigo]?.nome || codigo || 'Plano'
}

function planoResumo(usuario, filaAtiva) {
  if (usuario?.statusConta === 'ACCOUNT_INACTIVE') return 'Conta inativa'
  const assinatura = usuario?.assinatura
  if ((assinatura?.status === 'PENDENTE_PAGAMENTO' || assinatura?.status === 'PENDENTE') && filaAtiva.length === 0) {
    return 'Pagamento pendente'
  }
  if (assinatura?.status === 'EXPIRADA' && filaAtiva.length === 0) return 'Plano vencido'

  // Junta a assinatura atual (do usuario) com a fila retornada pela API,
  // removendo duplicidades para nunca exibir a mesma assinatura duas vezes.
  const mapa = new Map()
  const registrar = (item) => {
    if (!item) return
    const nome = nomePlanoDe(item?.planoNome || item?.plano)
    const inicio = String(item?.dataInicio || '').slice(0, 10)
    const fim = String(item?.dataFim || item?.dataFimTeste || '').slice(0, 10)
    if (!fim) return
    const restante = item?.diasRestantes != null ? item.diasRestantes : diasRestantesDe(fim)
    const chave = item?.id != null ? `id:${item.id}` : `${nome}|${inicio}|${fim}`
    if (mapa.has(chave)) return
    mapa.set(chave, {
      nome,
      inicio,
      restante,
    })
  }

  ;(Array.isArray(filaAtiva) ? filaAtiva : []).forEach(registrar)
  registrar(assinatura)

  // Mesmo plano comprado de novo: nao duplica o plano, apenas soma os dias
  // (considera o fim do ultimo periodo). Planos diferentes seguem lado a lado.
  const porNome = new Map()
  ;[...mapa.values()].forEach((item) => {
    const existente = porNome.get(item.nome)
    if (!existente || item.restante > existente.restante) {
      porNome.set(item.nome, item)
    }
  })

  const itens = [...porNome.values()]
    .sort((a, b) => String(a.inicio).localeCompare(String(b.inicio)) || a.nome.localeCompare(b.nome))

  // Nenhum plano identificado: recai sobre os dados do usuario
  if (itens.length === 0) {
    const nome = nomePlanoDe(usuario?.plano)
    const dataFim = assinatura?.dataFimTeste || assinatura?.dataFim
    if (!dataFim) return nome
    const restante = diasRestantesDe(dataFim) ?? 0
    return `${nome} ${restante} dias restantes`
  }

  return itens
    .map((item) => `${item.nome} ${item.restante ?? 0} dias restantes`)
    .join(' / ')
}

export default function Header() {
  const { usuario, logout } = useAuth()
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const [accountOpen, setAccountOpen] = useState(false)
  const [filaAssinaturas, setFilaAssinaturas] = useState([])
  const notificationsRef = useRef(null)
  const accountRef = useRef(null)

  useEffect(() => {
    if (!usuario?.empresaId) {
      setFilaAssinaturas([])
      return
    }
    let ativo = true
    appApi.listarAssinaturas(usuario.empresaId)
      .then((lista) => {
        if (!ativo) return
        const hoje = hojeISO()
        const fila = (Array.isArray(lista) ? lista : []).filter((item) => {
          const status = String(item?.status || '').toUpperCase()
          if (status !== 'ATIVA' && status !== 'TESTE') return false
          if (!item?.dataFim) return true
          return String(item.dataFim).slice(0, 10) >= hoje
        })
        setFilaAssinaturas(fila)
      })
      .catch(() => null)
    return () => {
      ativo = false
    }
  }, [usuario?.empresaId, usuario?.assinatura?.id])

  useEffect(() => {
    function fecharAoClicarFora(event) {
      const clicouNotificacao = notificationsRef.current?.contains(event.target)
      const clicouConta = accountRef.current?.contains(event.target)
      if (!clicouNotificacao) setNotificationsOpen(false)
      if (!clicouConta) setAccountOpen(false)
    }
    document.addEventListener('mousedown', fecharAoClicarFora)
    return () => document.removeEventListener('mousedown', fecharAoClicarFora)
  }, [])

  function alternarNotificacoes() {
    setNotificationsOpen((open) => !open)
    setAccountOpen(false)
  }

  function alternarConta() {
    setAccountOpen((open) => !open)
    setNotificationsOpen(false)
  }

  return (
    <header className="topbar">
      <div>
        <span className="section-kicker">Operação Gendaz</span>
        <strong>Visão geral</strong>
        <span>Acompanhe sua agenda, conversas e operação diária.</span>
      </div>
      <div className="topbar-actions">
        <Link to="/sistema/planos" className="demo-pill topbar-link">
          {planoResumo(usuario, filaAssinaturas)}
        </Link>
        <Link to="/sistema/suporte" className="demo-pill topbar-link">
          Suporte
        </Link>
        <div className="topbar-menu" ref={notificationsRef}>
          <button className="icon-btn" onClick={alternarNotificacoes} aria-label="Notificacoes">
            <Bell size={18} />
          </button>
          {notificationsOpen && (
            <div className="dropdown-panel notification-panel">
              <strong>Atualizacoes do sistema</strong>
              <span>Nova versao disponivel para todas as contas.</span>
              <span>Melhoria de desempenho aplicada no painel.</span>
              <span>Manutencao programada sera avisada com antecedencia.</span>
            </div>
          )}
        </div>
        <div className="topbar-menu" ref={accountRef}>
          <button className="user-menu" onClick={alternarConta} aria-label="Abrir menu da conta">
            <span>{usuario?.nome?.slice(0, 2).toUpperCase()}</span>
            <div className="user-menu__text">
              <strong>{usuario?.nome}</strong>
              <small>{usuario?.perfil}</small>
            </div>
            <ChevronDown size={16} className="user-menu__chevron" />
          </button>
          {accountOpen && (
            <div className="dropdown-panel account-panel">
              <Link to="/sistema/conta">
                <UserCircle size={16} />
                Minha conta
              </Link>
              <ThemeToggle />
              <button onClick={logout}>
                <LogOut size={16} />
                Sair
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}


