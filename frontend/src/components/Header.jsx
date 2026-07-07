import { Bell, ChevronDown, LogOut, UserCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../contexts/AuthContext.jsx'
import { PLANOS } from '../services/localStore.js'
import ThemeToggle from './ThemeToggle.jsx'

function planoResumo(usuario) {
  const nomePlano = PLANOS[usuario?.plano]?.nome || usuario?.plano || 'Plano'
  const assinatura = usuario?.assinatura
  if (usuario?.statusConta === 'ACCOUNT_INACTIVE') return 'Conta inativa'
  if (assinatura?.status === 'EXPIRADA') return 'Plano vencido'
  if (assinatura?.status === 'PENDENTE_PAGAMENTO' || assinatura?.status === 'PENDENTE') return 'Pagamento pendente'
  const dataFim = assinatura?.dataFimTeste || assinatura?.dataFim
  if (!dataFim) return nomePlano
  const restante = Math.max(0, Math.ceil((new Date(`${dataFim}T12:00:00`).getTime() - Date.now()) / 86400000))
  return `${nomePlano} - ${restante} dias restantes`
}

export default function Header() {
  const { usuario, logout } = useAuth()
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const [accountOpen, setAccountOpen] = useState(false)
  const notificationsRef = useRef(null)
  const accountRef = useRef(null)

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
          {planoResumo(usuario)}
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
            <div>
              <strong>{usuario?.nome}</strong>
              <small>{usuario?.perfil}</small>
            </div>
            <ChevronDown size={16} />
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
