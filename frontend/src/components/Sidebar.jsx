import { NavLink } from 'react-router-dom'
import { BarChart3, CalendarDays, Gift, Home, MessageCircle, MoreHorizontal, ReceiptText, Settings, Sparkles, Users, Wrench, UserRoundCog, ScrollText } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { useAuth } from '../contexts/AuthContext.jsx'
import { PLANOS } from '../services/localStore.js'
import { usePendentes } from '../contexts/PendentesContext.jsx'
import logoSidebar from '../assets/logos/gendaz-logo-branco.png'

const items = [
  { key: 'dashboard',     to: '/sistema/dashboard',     label: 'Dashboard',      icon: Home,          mobile: true },
  { key: 'agenda',        to: '/sistema/agenda',        label: 'Agenda',         icon: CalendarDays,  mobile: true },
  { key: 'clientes',      to: '/sistema/clientes',      label: 'Clientes',       icon: Users,         mobile: true },
  { key: 'profissionais', to: '/sistema/profissionais', label: 'Profissionais',  icon: UserRoundCog },
  { key: 'servicos',      to: '/sistema/servicos',      label: 'Serviços',       icon: Wrench },
  { key: 'crm',           to: '/sistema/crm',           label: 'CRM',            icon: MessageCircle },
  { key: 'insights',      to: '/sistema/insights',      label: 'Insights',       icon: Sparkles },
  { key: 'promocoes',     to: '/sistema/promocoes',     label: 'Promoções',      icon: Gift },
  { key: 'financeiro',    to: '/sistema/financeiro',    label: 'Financeiro',     icon: BarChart3,     mobile: true },
  { key: 'relatorios',    to: '/sistema/relatorios',    label: 'Relatórios',     icon: ReceiptText },
  { key: 'logs',          to: '/sistema/logs',          label: 'Logs',           icon: ScrollText, mobile: true },
  { key: 'configuracoes', to: '/sistema/configuracoes', label: 'Configurações',  icon: Settings },
]

export default function Sidebar() {
  const mobileMoreRef = useRef(null)
  const { usuario } = useAuth()
  const { contagemPendentes } = usePendentes()
  const allowed = PLANOS[usuario?.plano]?.rotas || []
  const visibleItems = items.filter((item) => allowed.includes(item.key))
  const primaryMobileItems = visibleItems.filter((item) => item.mobile)
  const moreMobileItems = visibleItems.filter((item) => !item.mobile)

  useEffect(() => {
    function fecharMaisAoTocarFora(event) {
      if (mobileMoreRef.current && !mobileMoreRef.current.contains(event.target)) {
        mobileMoreRef.current.removeAttribute('open')
      }
    }

    document.addEventListener('pointerdown', fecharMaisAoTocarFora)
    return () => document.removeEventListener('pointerdown', fecharMaisAoTocarFora)
  }, [])

  function renderFinanceBadge(key) {
    return key === 'financeiro' && contagemPendentes > 0 ? (
      <span className="badge-pendentes">{contagemPendentes}</span>
    ) : null
  }

  return (
    <>
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
              {renderFinanceBadge(key)}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <small>{PLANOS[usuario?.plano]?.nome || 'Painel'}</small>
        </div>
      </aside>
      <nav className="app-mobile-nav" aria-label="Navegação do painel Gendaz">
        {primaryMobileItems.map(({ to, key, label, icon: Icon }) => (
          <NavLink key={to} to={to} className={({ isActive }) => (isActive ? 'app-mobile-nav__link is-active' : 'app-mobile-nav__link')}>
            <Icon size={18} />
            <span>{label}</span>
            {renderFinanceBadge(key)}
          </NavLink>
        ))}
        <details className="app-mobile-more" ref={mobileMoreRef}>
          <summary className="app-mobile-nav__link">
            <MoreHorizontal size={18} />
            <span>Mais</span>
          </summary>
          <div className="app-mobile-more__panel">
            {moreMobileItems.map(({ to, key, label, icon: Icon }) => (
              <NavLink key={to} to={to} onClick={() => mobileMoreRef.current?.removeAttribute('open')} className={({ isActive }) => (isActive ? 'app-mobile-more__link is-active' : 'app-mobile-more__link')}>
                <Icon size={18} />
                <span>{label}</span>
                {renderFinanceBadge(key)}
              </NavLink>
            ))}
          </div>
        </details>
      </nav>
    </>
  )
}


