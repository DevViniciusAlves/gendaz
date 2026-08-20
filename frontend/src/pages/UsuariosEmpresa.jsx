import { Plus, RefreshCw, Shield, UserX, Mail, Copy, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import InternationalPhoneInput from '../components/InternationalPhoneInput.jsx'
import Modal from '../components/Modal.jsx'
import BulkConfirmModal from '../components/BulkConfirmModal.jsx'
import Table from '../components/Table.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { obterExemploTelefone, validarTelefone } from '../utils/phoneUtils.js'

function formatDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 10).split('-').reverse().join('/')
}

export default function UsuariosEmpresa() {
  const { usuario } = useAuth()
  const perfilAtendente = String(usuario?.perfil || '').toUpperCase() === 'ATENDENTE'
  const [resumo, setResumo] = useState({ limite: 1, usados: 0 })
  const [membros, setMembros] = useState([])
  const [convites, setConvites] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [nome, setNome] = useState('')
  const [telefone, setTelefone] = useState('')
  const [email, setEmail] = useState('')
  const [erroTelefone, setErroTelefone] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [confirmacao, setConfirmacao] = useState(null)
  const [executandoExclusao, setExecutandoExclusao] = useState(false)

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
    ...convites.filter((item) => String(item.status || '').toUpperCase() === 'PENDING').map((item) => ({
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
  ].slice(0, 3), [membros, convites])

  async function criarConvite(event) {
    event.preventDefault()
    setSalvando(true)
    setErroTelefone('')
    const erroTel = telefone.trim() ? validarTelefone(telefone, 'BR', true) : 'Telefone é obrigatório.'
    if (erroTel) {
      setErroTelefone(erroTel)
      setSalvando(false)
      return
    }
    try {
      await appApi.criarConviteUsuario({ nome, telefone, email })
      setNome('')
      setTelefone('')
      setEmail('')
      setErroTelefone('')
      setModalOpen(false)
      await carregar()
    } catch (err) {
      // Erro 400/404 não fecha o modal e não apaga os campos preenchidos.
      setError(err.response?.data?.mensagem || 'Nao foi possivel criar o convite.')
    } finally {
      setSalvando(false)
    }
  }

  function solicitarExclusao(row) {
    if (perfilAtendente) return
    setConfirmacao({
      titulo: 'Excluir conta',
      descricao: `Tem certeza que deseja excluir a conta de ${row.nome || row.email}? Essa ação apagará a conta do sistema e o login será perdido. Não será possível reverter.`,
      confirmLabel: 'Sim, excluir',
      danger: true,
      acao: async () => {
        setExecutandoExclusao(true)
        try {
          await appApi.removerMembroUsuario(row.usuarioId)
          await carregar()
        } finally {
          setExecutandoExclusao(false)
        }
      },
    })
  }

  return (
    <section className="page settings-users-page">
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
            <Button icon={Plus} onClick={() => setModalOpen(true)} disabled={limiteAtingido || perfilAtendente}>Adicionar usuário</Button>
          </div>
        </div>
        {perfilAtendente && <p className="plan-payment-note plan-payment-helper">Seu perfil nao permite adicionar usuarios.</p>}
        {limiteAtingido && !perfilAtendente && <p className="plan-payment-note plan-payment-helper">Seu plano atingiu o limite de usuários.</p>}
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
                  <button className="icon-btn" onClick={() => appApi.cancelarConviteUsuario(row.raw.id).then(carregar)} aria-label="Excluir convite"><Trash2 size={16} /></button>
                </>
              )}
              {row.tipo === 'Membro' && !row.owner && (
                <>
                  <button className="icon-btn" onClick={() => solicitarExclusao(row)} aria-label="Excluir conta"><Trash2 size={16} /></button>
                </>
              )}
              {row.owner && <span className="status-badge">Dono</span>}
            </div>
          )},
        ]}
        rows={rows}
        empty={loading ? 'Carregando...' : 'Nenhum usuário encontrado.'}
      />

      <Modal title="Adicionar usuário" open={modalOpen && !perfilAtendente} onClose={() => setModalOpen(false)}>
        <form onSubmit={criarConvite} className="modal-body">
          <Input label="Nome" value={nome} onChange={(e) => setNome(e.target.value)} />
          <InternationalPhoneInput
            label="Telefone"
            value={telefone}
            onChangeValue={(valor) => setTelefone(valor || '')}
            defaultCountry="BR"
            error={erroTelefone}
            helper={telefone ? (validarTelefone(telefone, 'BR', true) || ' Pronto para confirmar') : `Exemplo para o país selecionado: ${obterExemploTelefone('BR') || '+55 (65) 99336-0341'}`}
            required
          />
          <Input label="E-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          {error && <p className="form-error">{error}</p>}
          <div className="modal-actions">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" loading={salvando} disabled={limiteAtingido || perfilAtendente || Boolean(validarTelefone(telefone, 'BR', true))}>Criar convite</Button>
          </div>
        </form>
      </Modal>

      <BulkConfirmModal
        open={Boolean(confirmacao)}
        title={confirmacao?.titulo || 'Confirmar ação'}
        description={confirmacao?.descricao || ''}
        confirmLabel={confirmacao?.confirmLabel || 'Confirmar'}
        danger={Boolean(confirmacao?.danger)}
        loading={executandoExclusao}
        onCancel={() => setConfirmacao(null)}
        onConfirm={async () => {
          if (!confirmacao?.acao) return
          try {
            await confirmacao.acao()
            setConfirmacao(null)
          } catch (err) {
            setError(err.response?.data?.mensagem || 'Nao foi possivel excluir a conta.')
          }
        }}
      />
    </section>
  )
}
