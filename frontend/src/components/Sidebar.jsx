import { NavLink } from 'react-router-dom'
import { BarChart3, CalendarDays, Gift, Home, MessageCircle, ReceiptText, Settings, Sparkles, Users, Wrench, UserRoundCog } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext.jsx'
import { PLANOS } from '../services/localStore.js'
import { usePendentes } from '../hooks/usePendentes.js'
import logoSidebar from '../assets/logos/gendaz-logo-branco.png'

const items = [
  { key: 'dashboard',     to: '/sistema/dashboard',     label: 'Dashboard',      icon: Home },
  { key: 'agenda',        to: '/sistema/agenda',        label: 'Agendamentos',   icon: CalendarDays },
  { key: 'clientes',      to: '/sistema/clientes',      label: 'Clientes',       icon: Users },
  { key: 'profissionais', to: '/sistema/profissionais', label: 'Profissionais',  icon: UserRoundCog },
  { key: 'servicos',      to: '/sistema/servicos',      label: 'Serviços',       icon: Wrench },
  { key: 'crm',           to: '/sistema/crm',           label: 'CRM',            icon: MessageCircle },
  { key: 'insights',      to: '/sistema/insights',      label: 'Insights',       icon: Sparkles },
  { key: 'promocoes',     to: '/sistema/promocoes',     label: 'Promoções',      icon: Gift },
  { key: 'financeiro',    to: '/sistema/financeiro',    label: 'Financeiro',     icon: BarChart3 },
  { key: 'relatorios',    to: '/sistema/relatorios',    label: 'Relatórios',     icon: ReceiptText },
  { key: 'configuracoes', to: '/sistema/configuracoes', label: 'Configurações',  icon: Settings },
]

export default function Sidebar() {
  const { usuario } = useAuth()
  const { contagemPendentes } = usePendentes()
  const allowed = PLANOS[usuario?.plano]?.rotas || []
  const visibleItems = items.filter((item) => allowed.includes(item.key))

  return (
    <aside className="sidebar">
      <div className="sidebar-logo-wrapper">
        <img src={logoSidebar} alt="gendaz" className="sidebar-logo" />
      </div>
      <span className="nav-label nav-label--accent">Navegação</span>
      <nav>
        {visibleItems.map(({ to, key, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => (isActive ? 'active' : undefined)}
          >
            <Icon size={18} />
            <span>{label}</span>
            {key === 'financeiro' && contagemPendentes > 0 && (
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

