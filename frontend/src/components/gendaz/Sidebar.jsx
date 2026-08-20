import { NavLink, useParams } from 'react-router-dom'
import { CalendarDays, LayoutDashboard, MessageCircle, Settings2, History, LifeBuoy, Ticket, MoreHorizontal } from 'lucide-react'
import { useEffect, useRef } from 'react'
import logoSidebar from '../../assets/logos/meugendazpngpreto.png'

const items = [
  { to: '.', label: 'Dashboard', icon: LayoutDashboard, end: true, mobile: true },
  { to: 'agenda', label: 'Agenda', icon: CalendarDays, mobile: true },
  { to: 'historico', label: 'Historico', icon: History, mobile: true },
  { to: 'ia', label: 'gendazIA', icon: MessageCircle, mobile: true },
  { to: 'promocoes', label: 'Promocoes', icon: Ticket },
  { to: 'suporte', label: 'Suporte', icon: LifeBuoy },
  { to: 'configuracoes', label: 'Configuracoes', icon: Settings2 },
]

const primaryMobileItems = items.filter((item) => item.mobile)
const moreMobileItems = items.filter((item) => !item.mobile)

export default function Sidebar() {
  const mobileMoreRef = useRef(null)
  const { slug } = useParams()

  const basePath = `/meu-gendaz/${slug}`

  useEffect(() => {
    function fecharMaisAoTocarFora(event) {
      if (mobileMoreRef.current && !mobileMoreRef.current.contains(event.target)) {
        mobileMoreRef.current.removeAttribute('open')
      }
    }

    document.addEventListener('pointerdown', fecharMaisAoTocarFora)
    return () => document.removeEventListener('pointerdown', fecharMaisAoTocarFora)
  }, [])

  const resolveTo = (to) =>
    to === '.'
      ? `${basePath}/dashboard`
      : `${basePath}/${to}`

  return (
    <>
      <aside className="gendaz-sidebar">
        <div className="sidebar-logo-wrapper">
          <img src={logoSidebar} alt="gendaz" className="sidebar-logo" />
        </div>
        <span className="nav-label">Navegacao</span>
        <nav>
          {items.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={resolveTo(to)}
              end={end}
              className={({ isActive }) => (isActive ? 'gendaz-sidebar__link is-active' : 'gendaz-sidebar__link')}
            >
              <Icon size={18} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      <nav className="gendaz-mobile-nav" aria-label="Navegacao Meu Gendaz">
        {primaryMobileItems.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={resolveTo(to)}
            end={end}
            className={({ isActive }) => (isActive ? 'gendaz-mobile-nav__link is-active' : 'gendaz-mobile-nav__link')}
          >
            <Icon size={18} />
            <span>{label}</span>
          </NavLink>
        ))}
        <details className="gendaz-mobile-more" ref={mobileMoreRef}>
          <summary className="gendaz-mobile-nav__link">
            <MoreHorizontal size={18} />
            <span>Mais</span>
          </summary>
          <div className="gendaz-mobile-more__panel">
            {moreMobileItems.map(({ to, label, icon: Icon, end }) => (
              <NavLink
                key={to}
                to={resolveTo(to)}
                end={end}
                onClick={() => mobileMoreRef.current?.removeAttribute('open')}
                className={({ isActive }) => (isActive ? 'gendaz-mobile-more__link is-active' : 'gendaz-mobile-more__link')}
              >
                <Icon size={18} />
                <span>{label}</span>
              </NavLink>
            ))}
          </div>
        </details>
      </nav>
    </>
  )
}
