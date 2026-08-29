import { Power } from 'lucide-react'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import { useLocalData } from '../hooks/useLocalData.js'

export default function Usuarios() {
  const [data, setData] = useLocalData('usuarios')

  function alternarUsuario(id) {
    setData((current) => ({
      ...current,
      equipe: current.equipe.map((usuario) => usuario.id === id
        ? { ...usuario, status: usuario.status === 'ATIVO' ? 'INATIVO' : 'ATIVO' }
        : usuario),
    }))
  }

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Equipe</span>
        <h1>Usuários</h1>
        <p>Exemplo com usuários da mesma conta, status de acesso e presença online.</p>
      </div>
      <Table columns={[
        { key: 'nome', label: 'Nome' },
        { key: 'email', label: 'E-mail' },
        { key: 'perfil', label: 'Perfil' },
        { key: 'status', label: 'Situação', render: (row) => <StatusBadge status={row.status} /> },
        { key: 'presenca', label: 'Presença', render: (row) => <StatusBadge status={row.presenca} /> },
        { key: 'ultimoAcesso', label: 'Último acesso' },
        { key: 'acao', label: 'Ações', render: (row) => <button className="icon-btn" aria-label={`Alternar usuário ${row.nome}`} onClick={() => alternarUsuario(row.id)}><Power size={16} /></button> },
      ]} rows={data.equipe} />
    </section>
  )
}
