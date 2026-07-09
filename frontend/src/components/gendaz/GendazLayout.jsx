import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar.jsx'
import BotaoFlutante from './BotaoFlutante.jsx'

export default function GendazLayout() {
  return (
    <div className="gendaz-shell">
      <Sidebar />
      <main className="gendaz-main">
        <Outlet />
      </main>
      <BotaoFlutante />
    </div>
  )
}
