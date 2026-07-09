import { NavLink } from 'react-router-dom'
import { BarChart3, CalendarDays, CreditCard, Home, ReceiptText, Settings, Users, Wrench, UserRoundCog } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext.jsx'
import { PLANOS } from '../services/localStore.js'
import { usePagamentosPendentes } from '../hooks/usePagamentosPendentes.js'
import logoSidebar from '../assets/logos/gendaz-logo-green.png'

const items = [
  { key: 'dashboard',     to: '/sistema/dashboard',     label: 'Dashboard',      icon: Home },
  { key: 'agenda',        to: '/sistema/agenda',        label: 'Agenda',         icon: CalendarDays },
  { key: 'clientes',      to: '/sistema/clientes',      label: 'Clientes',       icon: Users },
  { key: 'servicos',      to: '/sistema/servicos',      label: 'ServiÃ§os',       icon: Wrench },
  { key: 'profissionais', to: '/sistema/profissionais', label: 'Profissionais',  icon: UserRoundCog },
  { key: 'financeiro',    to: '/sistema/financeiro',    label: 'Financeiro',     icon: BarChart3 },
  { key: 'pagamentos',    to: '/sistema/pagamentos',    label: 'Pagamentos',     icon: CreditCard },
  { key: 'relatorios',    to: '/sistema/relatorios',    label: 'RelatÃ³rios',     icon: ReceiptText },
  { key: 'configuracoes', to: '/sistema/configuracoes', label: 'ConfiguraÃ§Ãµes',  icon: Settings },
]

export default function Sidebar() {
  const { usuario } = useAuth()
  const { contagemPendentes } = usePagamentosPendentes()
  const allowed = PLANOS[usuario?.plano]?.rotas || []
  const visibleItems = items.filter((item) => allowed.includes(item.key))

  return (
    <aside className="sidebar">
      <div className="sidebar-logo-wrapper">
        <img src={logoSidebar} alt="gendaz" className="sidebar-logo" />
      </div>
      <span className="nav-label">NavegaÃ§Ã£o</span>
      <nav>
        {visibleItems.map(({ to, key, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => (isActive ? 'active' : undefined)}
          >
            <Icon size={18} />
            <span>{label}</span>
            {key === 'pagamentos' && contagemPendentes > 0 && (
              <span className="badge-pendentes">{contagemPendentes}</span>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="sidebar-foot">
        <small>{PLANOS[usuario?.plano]?.nome || 'Painel'}</small>
      </div>
    </aside>
  )
}

