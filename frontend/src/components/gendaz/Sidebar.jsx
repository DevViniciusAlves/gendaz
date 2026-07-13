import { NavLink } from 'react-router-dom'
import { Bot, CalendarDays, Gift, LayoutDashboard, MessageCircle, Settings2, History } from 'lucide-react'
import logoSidebar from '../../assets/logos/gendaz-logo-branco.png'

const items = [
  { to: '/meu-gendaz', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/meu-gendaz/agenda', label: 'Agenda', icon: CalendarDays },
  { to: '/meu-gendaz/historico', label: 'Histórico', icon: History },
  { to: '/meu-gendaz/ia', label: 'Assistente IA', icon: MessageCircle },
  { to: '/meu-gendaz/beneficios', label: 'Benefícios', icon: Gift },
  { to: '/meu-gendaz/configuracoes', label: 'Configurações', icon: Settings2 },
]

export default function Sidebar() {
  return (
    <aside className="gendaz-sidebar">
      <div className="sidebar-logo-wrapper">
        <img src={logoSidebar} alt="gendaz" className="sidebar-logo" />
      </div>
      <span className="nav-label">Navegação</span>
      <nav>
        {items.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} className={({ isActive }) => (isActive ? 'gendaz-sidebar__link is-active' : 'gendaz-sidebar__link')}>
            <Icon size={18} />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="gendaz-sidebar__footer">
        <Bot size={16} />
        <small>IA sempre disponível</small>
      </div>
    </aside>
  )
}
