import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, CheckCircle2, Copy, Pencil, Plus, Trash2, Users, Megaphone, Eye } from 'lucide-react'
import { promocoesApi } from '../api/promocoesApi.js'
import { clientesApi } from '../api/clientesApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import Button from '../components/Button.jsx'

const emptyForm = {
  codigo: '',
  descricao: '',
  tipo: 'PERCENTUAL',
  valor: '',
  dataInicio: '',
  dataFim: '',
  quantidadeLimite: '',
  aplicarTodosServicos: true,
  servicoIds: [],
}

function toDateTimeLocal(value) {
  if (!value) return ''
  const date = new Date(value)
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

export default function Promocoes() {
  const { usuario } = useAuth()
  const [cupons, setCupons] = useState([])
  const [clientes, setClientes] = useState([])
  const [filtro, setFiltro] = useState('TODOS')
  const [carregando, setCarregando] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const [modalAberto, setModalAberto] = useState(false)
  const [notificarAberto, setNotificarAberto] = useState(false)
  const [usoAberto, setUsoAberto] = useState(false)
  const [editing, setEditing] = useState(null)
  const [target, setTarget] = useState(null)
  const [resumo, setResumo] = useState(null)
  const [historico, setHistorico] = useState([])
  const [form, setForm] = useState(emptyForm)
  const [notificacao, setNotificacao] = useState({ tipo: 'TODOS', clienteIds: [] })

  const empresaId = usuario?.empresaId || 1
  const filtered = useMemo(() => cupons.filter((cupom) => {
    if (filtro === 'ATIVOS') return cupom.status === 'ATIVO'
    if (filtro === 'INATIVOS') return cupom.status === 'INATIVO'
    return true
  }), [cupons, filtro])

  async function carregar() {
    setCarregando(true)
    try {
      const [lista, clientesRes] = await Promise.all([
        promocoesApi.listar(empresaId),
        clientesApi.listarPorEmpresa(empresaId),
      ])
      setCupons(lista || [])
      setClientes(clientesRes || [])
    } finally {
      setCarregando(false)
    }
  }

  useEffect(() => {
    carregar().catch((error) => console.error(error))
  }, [empresaId])

  function abrirNovo() {
    setEditing(null)
    setForm(emptyForm)
    setModalAberto(true)
  }

  function abrirEdicao(cupom) {
    setEditing(cupom)
    setForm({
      codigo: cupom.codigo || '',
      descricao: cupom.descricao || '',
      tipo: cupom.tipo || 'PERCENTUAL',
      valor: String(cupom.valor ?? ''),
      dataInicio: toDateTimeLocal(cupom.dataInicio),
      dataFim: toDateTimeLocal(cupom.dataFim),
      quantidadeLimite: String(cupom.quantidadeLimite ?? ''),
      aplicarTodosServicos: cupom.aplicarTodosServicos ?? true,
      servicoIds: (cupom.servicos || []).map((item) => item.id),
    })
    setModalAberto(true)
  }

  async function salvar(event) {
    event.preventDefault()
    setSalvando(true)
    const payload = {
      ...form,
      valor: Number(String(form.valor).replace(',', '.')),
      quantidadeLimite: form.quantidadeLimite ? Number(form.quantidadeLimite) : null,
      dataInicio: new Date(form.dataInicio).toISOString(),
      dataFim: new Date(form.dataFim).toISOString(),
      servicoIds: form.aplicarTodosServicos ? [] : form.servicoIds,
    }

    try {
      if (editing) {
        await promocoesApi.atualizar(editing.id, payload, empresaId)
      } else {
        await promocoesApi.criar(payload, empresaId)
      }
      setModalAberto(false)
      setEditing(null)
      setForm(emptyForm)
      await carregar()
    } finally {
      setSalvando(false)
    }
  }

  async function desativar(id) {
    await promocoesApi.desativar(id, empresaId)
    await carregar()
  }

  async function excluir(id) {
    if (!window.confirm('Excluir esta promoção permanentemente?')) return
    await promocoesApi.excluir(id, empresaId)
    await carregar()
  }

  async function abrirResumo(cupom) {
    const { data } = await promocoesApi.uso(cupom.id, empresaId)
    setResumo(data)
    setUsoAberto(true)
  }

  async function abrirNotificar(cupom) {
    setTarget(cupom)
    setNotificacao({ tipo: 'TODOS', clienteIds: [] })
    setNotificarAberto(true)
  }

  async function enviarNotificacoes(event) {
    event.preventDefault()
    if (!target) return
    await promocoesApi.notificar(target.id, notificacao, empresaId)
    setNotificarAberto(false)
    setTarget(null)
    await carregar()
  }

  return (
    <section className="page">
      <header className="page-title">
        <div>
          <span className="section-kicker">Gendaz</span>
          <h1>Promoções</h1>
          <p>Crie cupons, notifique clientes e acompanhe o uso em um único lugar.</p>
        </div>
        <Button icon={Plus} onClick={abrirNovo}>Nova promoção</Button>
      </header>

      <div className="panel" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {['TODOS', 'ATIVOS', 'INATIVOS'].map((item) => (
            <button key={item} type="button" className={filtro === item ? 'btn-primary' : 'btn-secondary'} onClick={() => setFiltro(item)}>{item}</button>
          ))}
        </div>
      </div>

      {carregando ? (
        <div className="panel">Carregando promoções...</div>
      ) : (
        <div className="panel" style={{ overflowX: 'auto' }}>
          <table className="table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Descrição</th>
                <th>Desconto</th>
                <th>Vigência</th>
                <th>Status</th>
                <th>Uso</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((cupom) => (
                <tr key={cupom.id}>
                  <td><strong>{cupom.codigo}</strong></td>
                  <td>{cupom.descricao}</td>
                  <td>{cupom.tipo === 'PERCENTUAL' ? `${cupom.valor}%` : `R$ ${cupom.valor}`}</td>
                  <td>{new Date(cupom.dataFim).toLocaleDateString('pt-BR')}</td>
                  <td>{cupom.status === 'ATIVO' ? <span className="status status-success"><CheckCircle2 size={14} /> Ativo</span> : <span className="status status-muted"><AlertTriangle size={14} /> Inativo</span>}</td>
                  <td>{cupom.quantidadeUsada}/{cupom.quantidadeLimite ?? '∞'}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <button type="button" className="btn-secondary" onClick={() => abrirEdicao(cupom)}><Pencil size={14} /> Editar</button>
                      <button type="button" className="btn-secondary" onClick={() => abrirNotificar(cupom)}><Megaphone size={14} /> Notificar</button>
                      <button type="button" className="btn-secondary" onClick={() => abrirResumo(cupom)}><Eye size={14} /> Ver uso</button>
                      <button type="button" className="btn-secondary" onClick={() => navigator.clipboard.writeText(cupom.codigo)}><Copy size={14} /> Copiar</button>
                      <button type="button" className="btn-secondary" onClick={() => desativar(cupom.id)}>Desativar</button>
                      <button type="button" className="btn-danger" onClick={() => excluir(cupom.id)}><Trash2 size={14} /> Excluir</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalAberto && (
        <div className="modal-backdrop">
          <div className="panel modal-card" style={{ maxWidth: 720, width: '100%' }}>
            <h2>{editing ? 'Editar promoção' : 'Nova promoção'}</h2>
            <form onSubmit={salvar} style={{ display: 'grid', gap: 12 }}>
              <div className="grid grid-2">
                <label><span>Código</span><input value={form.codigo} onChange={(e) => setForm((c) => ({ ...c, codigo: e.target.value }))} /></label>
                <label><span>Descrição</span><input value={form.descricao} onChange={(e) => setForm((c) => ({ ...c, descricao: e.target.value }))} /></label>
                <label><span>Tipo</span><select value={form.tipo} onChange={(e) => setForm((c) => ({ ...c, tipo: e.target.value }))}><option value="PERCENTUAL">Percentual</option><option value="VALOR_FIXO">Valor fixo</option></select></label>
                <label><span>Valor</span><input value={form.valor} onChange={(e) => setForm((c) => ({ ...c, valor: e.target.value }))} /></label>
                <label><span>Início</span><input type="datetime-local" value={form.dataInicio} onChange={(e) => setForm((c) => ({ ...c, dataInicio: e.target.value }))} /></label>
                <label><span>Fim</span><input type="datetime-local" value={form.dataFim} onChange={(e) => setForm((c) => ({ ...c, dataFim: e.target.value }))} /></label>
                <label><span>Limite</span><input value={form.quantidadeLimite} onChange={(e) => setForm((c) => ({ ...c, quantidadeLimite: e.target.value }))} /></label>
                <label><span>Todos os serviços</span><select value={String(form.aplicarTodosServicos)} onChange={(e) => setForm((c) => ({ ...c, aplicarTodosServicos: e.target.value === 'true' }))}><option value="true">Sim</option><option value="false">Não</option></select></label>
              </div>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <Button type="button" variant="secondary" onClick={() => setModalAberto(false)}>Cancelar</Button>
                <Button type="submit" disabled={salvando}>{salvando ? 'Salvando...' : 'Salvar'}</Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {notificarAberto && (
        <div className="modal-backdrop">
          <div className="panel modal-card" style={{ maxWidth: 640, width: '100%' }}>
            <h2>Notificar clientes</h2>
            <form onSubmit={enviarNotificacoes} style={{ display: 'grid', gap: 12 }}>
              <label><span>Segmentação</span><select value={notificacao.tipo} onChange={(e) => setNotificacao((c) => ({ ...c, tipo: e.target.value }))}><option value="TODOS">Todos</option><option value="EM_RISCO">Em risco</option><option value="MANUAL">Manual</option></select></label>
              {notificacao.tipo === 'MANUAL' && (
                <label>
                  <span>Clientes</span>
                  <select multiple value={notificacao.clienteIds.map(String)} onChange={(e) => setNotificacao((c) => ({ ...c, clienteIds: Array.from(e.target.selectedOptions).map((opt) => Number(opt.value)) }))}>
                    {clientes.map((cliente) => <option key={cliente.id} value={cliente.id}>{cliente.nome}</option>)}
                  </select>
                </label>
              )}
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <Button type="button" variant="secondary" onClick={() => setNotificarAberto(false)}>Cancelar</Button>
                <Button type="submit">Disparar</Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {usoAberto && resumo && (
        <div className="modal-backdrop">
          <div className="panel modal-card" style={{ maxWidth: 800, width: '100%' }}>
            <h2>Uso da promoção</h2>
            <p>{resumo.codigo} - {resumo.descricao}</p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 12, marginBottom: 16 }}>
              <div className="panel"><strong>{resumo.totalClientesUsaram}</strong><div>Clientes</div></div>
              <div className="panel"><strong>{resumo.totalUsos}</strong><div>Usos</div></div>
              <div className="panel"><strong>{resumo.totalNotificacoesEnviadas}</strong><div>Enviadas</div></div>
              <div className="panel"><strong>{resumo.totalNotificacoesErros}</strong><div>Erros</div></div>
            </div>
            <Button variant="secondary" onClick={() => setUsoAberto(false)}>Fechar</Button>
          </div>
        </div>
      )}
    </section>
  )
}
