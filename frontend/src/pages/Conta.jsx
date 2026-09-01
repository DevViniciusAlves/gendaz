import { UserRoundCog } from 'lucide-react'
import StatusBadge from '../components/StatusBadge.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { PLANOS } from '../services/localStore.js'

export default function Conta() {
  const { usuario } = useAuth()

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Conta</span>
        <h1>Dados da conta</h1>
        <p>Visualize as informações da sua conta.</p>
      </div>
      <section className="panel account-card">
        <UserRoundCog size={28} />
        <h2>{usuario.nome}</h2>
        <p>{usuario.email}</p>
        <div className="account-badges">
          <StatusBadge status={usuario.perfil} />
          <StatusBadge status={PLANOS[usuario.plano]?.nome || usuario.plano} />
        </div>
      </section>
    </section>
  )
}
