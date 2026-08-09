import { Plus, RefreshCw, Shield, UserX, UserCheck, Mail, Copy } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import Modal from '../components/Modal.jsx'
import Table from '../components/Table.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'

function formatDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 10).split('-').reverse().join('/')
}

export default function UsuariosEmpresa() {
  const { usuario } = useAuth()
  const [resumo, setResumo] = useState({ limite: 1, usados: 0 })
  const [membros, setMembros] = useState([])
  const [convites, setConvites] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [salvando, setSalvando] = useState(false)

  async function carregar() {
    if (!usuario?.empresaId) return
    setLoading(true)
    try {
      const [resumoData, membrosData, convitesData] = await Promise.all([
        appApi.resumoUsuarios(usuario.empresaId),
        appApi.listarMembros(usuario.empresaId),
        appApi.listarConvites(usuario.empresaId),
      ])
      setResumo(resumoData || { limite: 1, usados: 0 })
      setMembros(Array.isArray(membrosData) ? membrosData : [])
      setConvites(Array.isArray(convitesData) ? convitesData : [])
      setError('')
    } catch (err) {
      setError(err.response?.data?.mensagem || 'Nao foi possivel carregar os usuarios.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    carregar()
  }, [usuario?.empresaId])

  const usados = Number(resumo?.usados || 0)
  const limite = Number(resumo?.limite || 1)
  const limiteAtingido = usados >= limite

  const rows = useMemo(() => [
    ...membros.map((item) => ({
      id: `m-${item.id}`,
      tipo: 'Membro',
      nome: item.nome,
      email: item.email,
      status: item.owner ? 'Dono' : String(item.status || '-'),
      papel: item.owner ? 'OWNER' : String(item.funcao || 'MEMBER'),
      entrada: formatDate(item.dataEntrada),
      expira: '-',
      convite: '-',
      owner: item.owner,
      usuarioId: item.usuarioId,
      membroId: item.id,
      raw: item,
    })),
    ...convites.map((item) => ({
      id: `c-${item.id}`,
      tipo: 'Convite',
      nome: '-',
      email: item.email,
      status: String(item.status || '-'),
      papel: '-',
      entrada: formatDate(item.dataCriacao),
      expira: formatDate(item.dataExpiracao),
      convite: formatDate(item.dataCriacao),
      owner: false,
      raw: item,
    })),
  ], [membros, convites])

  async function criarConvite(event) {
    event.preventDefault()
    setSalvando(true)
    try {
      await appApi.criarConviteUsuario({ email })
      setEmail('')
      setModalOpen(false)
      await carregar()
    } catch (err) {
      setError(err.response?.data?.mensagem || 'Nao foi possivel criar o convite.')
    } finally {
      setSalvando(false)
    }
  }

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Configurações</span>
        <h1>Usuários</h1>
        <p>Gerencie os membros da empresa e os convites pendentes.</p>
      </div>

      <div className="panel" style={{ marginBottom: 20 }}>
        <div className="panel-head">
          <div>
            <span className="section-kicker">Plano</span>
            <h2>{usados}/{limite} usuários utilizados</h2>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Button variant="secondary" icon={RefreshCw} onClick={carregar} loading={loading} />
            <Button icon={Plus} onClick={() => setModalOpen(true)} disabled={limiteAtingido}>Adicionar usuário</Button>
          </div>
        </div>
        {limiteAtingido && <p className="plan-payment-note plan-payment-helper">Seu plano atingiu o limite de usuários.</p>}
        {error && <p className="form-error">{error}</p>}
      </div>

      <Table
        columns={[
          { key: 'tipo', label: 'Tipo' },
          { key: 'nome', label: 'Nome' },
          { key: 'email', label: 'E-mail' },
          { key: 'status', label: 'Status' },
          { key: 'papel', label: 'Função' },
          { key: 'entrada', label: 'Entrada' },
          { key: 'expira', label: 'Expira' },
          { key: 'acao', label: 'Ações', render: (row) => (
            <div style={{ display: 'flex', gap: 8 }}>
              {row.tipo === 'Convite' && (
                <>
                  <button className="icon-btn" onClick={() => appApi.reenviarConviteUsuario(row.raw.id).then(carregar)} aria-label="Reenviar"><Mail size={16} /></button>
                  <button className="icon-btn" onClick={() => appApi.cancelarConviteUsuario(row.raw.id).then(carregar)} aria-label="Cancelar"><UserX size={16} /></button>
                </>
              )}
              {row.tipo === 'Membro' && !row.owner && (
                <>
                  <button className="icon-btn" onClick={() => appApi.desativarMembroUsuario(row.usuarioId).then(carregar)} aria-label="Desativar"><UserX size={16} /></button>
                  <button className="icon-btn" onClick={() => appApi.reativarMembroUsuario(row.usuarioId).then(carregar)} aria-label="Reativar"><UserCheck size={16} /></button>
                </>
              )}
              {row.owner && <span className="status-badge">Dono</span>}
            </div>
          )},
        ]}
        rows={rows}
        empty={loading ? 'Carregando...' : 'Nenhum usuário encontrado.'}
      />

      <Modal title="Adicionar usuário" open={modalOpen} onClose={() => setModalOpen(false)}>
        <form onSubmit={criarConvite} className="modal-body">
          <Input label="E-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <div className="modal-actions">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" loading={salvando} disabled={limiteAtingido}>Criar convite</Button>
          </div>
        </form>
      </Modal>
    </section>
  )
}
