import { NavLink } from 'react-router-dom'
import { CalendarDays, Gift, LayoutDashboard, MessageCircle, Settings2, History, LifeBuoy, Ticket } from 'lucide-react'
import logoSidebar from '../../assets/logos/meugendazpngpreto.png'

const items = [
  { to: '.', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: 'agenda', label: 'Agenda', icon: CalendarDays },
  { to: 'historico', label: 'Historico', icon: History },
  { to: 'ia', label: 'gendazIA', icon: MessageCircle },
  { to: 'promocoes', label: 'Promocoes', icon: Ticket },
  { to: 'suporte', label: 'Suporte', icon: LifeBuoy },
  { to: 'configuracoes', label: 'Configuracoes', icon: Settings2 },
]

export default function Sidebar() {
  return (
    <aside className="gendaz-sidebar">
      <div className="sidebar-logo-wrapper">
        <img src={logoSidebar} alt="gendaz" className="sidebar-logo" />
      </div>
      <span className="nav-label">Navegacao</span>
      <nav>
        {items.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} className={({ isActive }) => (isActive ? 'gendaz-sidebar__link is-active' : 'gendaz-sidebar__link')}>
            <Icon size={18} />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
