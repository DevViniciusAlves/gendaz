import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, CheckCircle2, Copy, Eye, Megaphone, Pencil, Plus, Search, Trash2, X } from 'lucide-react'
import { promocoesApi } from '../api/promocoesApi.js'
import { clientesApi } from '../api/clientesApi.js'
import { servicosApi } from '../api/servicosApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import Button from '../components/Button.jsx'
import { exibirTelefone } from '../utils/phoneUtils.js'

const emptyForm = {
  codigo: '',
  descricao: '',
  tipo: 'PERCENTUAL',
  valor: '',
  dataInicio: '',
  dataFim: '',
  quantidadeLimite: '',
  aplicarTodosServicos: true,
}

function toDateTimeLocal(value) {
  if (!value) return ''
  const date = new Date(value)
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function currency(value) {
  return Number(value || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function sinalizarPromocoesAtualizadas() {
  try {
    window.dispatchEvent(new CustomEvent('gendaz:promocoes-atualizadas'))
    if (typeof BroadcastChannel !== 'undefined') {
      const canal = new BroadcastChannel('gendaz-promocoes')
      canal.postMessage({ tipo: 'ATUALIZAR' })
      canal.close()
    }
    window.localStorage.setItem('gendaz-promocoes-refresh', String(Date.now()))
  } catch {
    /* ignora */
  }
}

export default function Promocoes() {
  const { usuario } = useAuth()
  const [cupons, setCupons] = useState([])
  const [clientes, setClientes] = useState([])
  const [servicos, setServicos] = useState([])
  const [filtro, setFiltro] = useState('TODOS')
  const [carregando, setCarregando] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const [disparando, setDisparando] = useState(false)
  const [cupomEmAcao, setCupomEmAcao] = useState(null)
  const [modalAberto, setModalAberto] = useState(false)
  const [notificarAberto, setNotificarAberto] = useState(false)
  const [usoAberto, setUsoAberto] = useState(false)
  const [modalExcluir, setModalExcluir] = useState(null)
  const [editing, setEditing] = useState(null)
  const [target, setTarget] = useState(null)
  const [resumo, setResumo] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [notificacao, setNotificacao] = useState({ tipo: 'TODOS', clienteIds: [] })
  const [servicosSelecionados, setServicosSelecionados] = useState(new Set())
  const [termoServico, setTermoServico] = useState('')
  const [termoCliente, setTermoCliente] = useState('')

  const empresaId = usuario?.empresaId || 1

  const filtered = useMemo(() => {
    return cupons.filter((cupom) => {
      if (filtro === 'ATIVOS') return cupom.status === 'ATIVO'
      if (filtro === 'INATIVOS') return cupom.status === 'INATIVO'
      return true
    })
  }, [cupons, filtro])

  const totalAtivos = cupons.filter((cupom) => cupom.status === 'ATIVO').length
  const totalInativos = cupons.filter((cupom) => cupom.status === 'INATIVO').length

  async function carregar() {
    setCarregando(true)
    try {
      const [lista, clientesRes, servicosRes] = await Promise.all([
        promocoesApi.listar(empresaId),
        clientesApi.listarPorEmpresa(empresaId),
        servicosApi.listarPorEmpresa(empresaId),
      ])
      setCupons(lista || [])
      setClientes(clientesRes || [])
      setServicos(servicosRes || [])
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
    setServicosSelecionados(new Set())
    setTermoServico('')
    setModalAberto(true)
  }

  function abrirEdicao(cupom) {
    const selected = new Set((cupom.servicos || []).map((item) => item.id))
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
    })
    setServicosSelecionados(selected)
    setTermoServico('')
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
      aplicarTodosServicos: Boolean(form.aplicarTodosServicos),
      servicoIds: form.aplicarTodosServicos ? [] : Array.from(servicosSelecionados),
    }

    try {
      if (editing) {
        await promocoesApi.atualizar(editing.id, payload, empresaId)
      } else {
        await promocoesApi.criar(payload, empresaId)
      }
      sinalizarPromocoesAtualizadas()
      setModalAberto(false)
      setEditing(null)
      setForm(emptyForm)
      setServicosSelecionados(new Set())
      await carregar()
    } finally {
      setSalvando(false)
    }
  }

  async function desativar(id) {
    if (cupomEmAcao) return
    setCupomEmAcao(id)
    window.dispatchEvent(new CustomEvent('gendaz:toast', {
      detail: { type: 'loading', message: 'Desativando cupom... aguarde' },
    }))
    try {
      await promocoesApi.desativar(id, empresaId)
      window.dispatchEvent(new CustomEvent('gendaz:toast', {
        detail: { type: 'success', message: 'Cupom desativado com sucesso.' },
      }))
      sinalizarPromocoesAtualizadas()
      await carregar()
    } catch (error) {
      window.dispatchEvent(new CustomEvent('gendaz:toast', {
        detail: { type: 'error', message: error?.response?.data?.message || error?.response?.data?.mensagem || 'Não foi possível desativar o cupom.' },
      }))
    } finally {
      setCupomEmAcao(null)
    }
  }

  async function ativar(id) {
    if (cupomEmAcao) return
    setCupomEmAcao(id)
    window.dispatchEvent(new CustomEvent('gendaz:toast', {
      detail: { type: 'loading', message: 'Ativando cupom... aguarde' },
    }))
    try {
      await promocoesApi.ativar(id, empresaId)
      window.dispatchEvent(new CustomEvent('gendaz:toast', {
        detail: { type: 'success', message: 'Cupom ativado com sucesso.' },
      }))
      sinalizarPromocoesAtualizadas()
      await carregar()
    } catch (error) {
      window.dispatchEvent(new CustomEvent('gendaz:toast', {
        detail: { type: 'error', message: error?.response?.data?.message || error?.response?.data?.mensagem || 'Não foi possível ativar o cupom.' },
      }))
    } finally {
      setCupomEmAcao(null)
    }
  }

  async function excluirConfirmado() {
    if (!modalExcluir || cupomEmAcao) return
    setCupomEmAcao(modalExcluir.id)
    window.dispatchEvent(new CustomEvent('gendaz:toast', {
      detail: { type: 'loading', message: 'Excluindo cupom... aguarde' },
    }))
    try {
      await promocoesApi.excluir(modalExcluir.id, empresaId)
      window.dispatchEvent(new CustomEvent('gendaz:toast', {
        detail: { type: 'success', message: 'Cupom excluído com sucesso.' },
      }))
      sinalizarPromocoesAtualizadas()
      setModalExcluir(null)
      await carregar()
    } catch (error) {
      window.dispatchEvent(new CustomEvent('gendaz:toast', {
        detail: { type: 'error', message: error?.response?.data?.message || error?.response?.data?.mensagem || 'Não foi possível excluir o cupom.' },
      }))
    } finally {
      setCupomEmAcao(null)
    }
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
    if (disparando) return
    setDisparando(true)
    try {
      window.dispatchEvent(new CustomEvent('gendaz:toast', {
        detail: { type: 'loading', message: 'Disparando... aguarde' },
      }))
      const response = await promocoesApi.notificar(target.id, notificacao, empresaId)
      const mensagem = response?.data?.mensagem || 'Notificação enviada com sucesso.'
      window.dispatchEvent(new CustomEvent('gendaz:toast', {
        detail: { type: 'success', message: mensagem },
      }))
      sinalizarPromocoesAtualizadas()
      setNotificarAberto(false)
      setTarget(null)
      await carregar()
    } catch (error) {
      window.dispatchEvent(new CustomEvent('gendaz:toast', {
        detail: { type: 'error', message: error?.response?.data?.message || error?.response?.data?.mensagem || 'Não foi possível disparar a notificação.' },
      }))
    } finally {
      setDisparando(false)
    }
  }

  const clientesFiltrados = clientes.filter((cliente) => {
    const termo = termoCliente.toLowerCase()
    if (!termo) return true
    return String(cliente.nome || '').toLowerCase().includes(termo)
      || String(cliente.email || '').toLowerCase().includes(termo)
      || String(cliente.telefone || '').toLowerCase().includes(termo)
  })

  const servicosFiltrados = servicos.filter((servico) => {
    const termo = termoServico.toLowerCase()
    if (!termo) return true
    return String(servico.nome || '').toLowerCase().includes(termo)
      || String(servico.descricao || '').toLowerCase().includes(termo)
  })

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

      <div className="panel">
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {[
            { key: 'TODOS', label: `Todos (${cupons.length})` },
            { key: 'ATIVOS', label: `Ativos (${totalAtivos})` },
            { key: 'INATIVOS', label: `Inativos (${totalInativos})` },
          ].map((item) => (
            <button
              key={item.key}
              type="button"
              className={`filter-chip ${filtro === item.key ? 'active' : ''}`}
              onClick={() => setFiltro(item.key)}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {carregando ? (
        <div className="panel">Carregando promoções...</div>
      ) : (
        <div className="panel" style={{ overflowX: 'auto', padding: 0 }}>
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
                  <td>{cupom.tipo === 'PERCENTUAL' ? `${cupom.valor}%` : currency(cupom.valor)}</td>
                  <td>{cupom.dataFim ? new Date(cupom.dataFim).toLocaleDateString('pt-BR') : 'Sem prazo'}</td>
                  <td>
                    {cupom.status === 'ATIVO'
                      ? <span className="status status-success"><CheckCircle2 size={14} /> Ativo</span>
                      : <span className="status status-muted"><AlertTriangle size={14} /> Inativo</span>}
                  </td>
                  <td>{cupom.quantidadeUsada}/{cupom.quantidadeLimite ?? '∞'}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <button type="button" className="btn-secondary" onClick={() => abrirEdicao(cupom)}><Pencil size={14} /> Editar</button>
                      <button type="button" className="btn-secondary" onClick={() => abrirNotificar(cupom)}><Megaphone size={14} /> Notificar</button>
                      <button type="button" className="btn-secondary" onClick={() => abrirResumo(cupom)}><Eye size={14} /> Ver uso</button>
                      <button type="button" className="btn-secondary" onClick={() => navigator.clipboard.writeText(cupom.codigo)}><Copy size={14} /> Copiar</button>
                      {cupom.status === 'ATIVO' ? (
                        <button type="button" className="btn-secondary" onClick={() => desativar(cupom.id)} disabled={cupomEmAcao === cupom.id}>
                          {cupomEmAcao === cupom.id ? 'Desativando...' : 'Desativar'}
                        </button>
                      ) : (
                        <button type="button" className="btn-secondary" onClick={() => ativar(cupom.id)} disabled={cupomEmAcao === cupom.id}>
                          {cupomEmAcao === cupom.id ? 'Ativando...' : 'Ativar'}
                        </button>
                      )}
                      <button type="button" className="btn btn-danger" onClick={() => setModalExcluir(cupom)} disabled={cupomEmAcao === cupom.id}><Trash2 size={14} /> Excluir</button>
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
          <div className="panel modal-card" style={{ maxWidth: 760, width: '100%' }}>
            <div className="modal-header" style={{ padding: 0, border: 0, marginBottom: 16 }}>
              <div>
                <h2 style={{ marginBottom: 6 }}>{editing ? 'Editar promoção' : 'Nova promoção'}</h2>
                <p>Preencha os dados do cupom e escolha como ele será aplicado.</p>
              </div>
              <button type="button" className="icon-btn" onClick={() => setModalAberto(false)} aria-label="Fechar">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={salvar} style={{ display: 'grid', gap: 14 }}>
              <div className="grid grid-2">
                <label><span>Código</span><input value={form.codigo} onChange={(e) => setForm((c) => ({ ...c, codigo: e.target.value }))} /></label>
                <label><span>Descrição</span><input value={form.descricao} onChange={(e) => setForm((c) => ({ ...c, descricao: e.target.value }))} /></label>
                <label><span>Tipo</span><select value={form.tipo} onChange={(e) => setForm((c) => ({ ...c, tipo: e.target.value }))}><option value="PERCENTUAL">Percentual</option><option value="VALOR_FIXO">Valor fixo</option></select></label>
                <label><span>Valor</span><input value={form.valor} onChange={(e) => setForm((c) => ({ ...c, valor: e.target.value }))} /></label>
                <label><span>Início</span><input type="datetime-local" value={form.dataInicio} onChange={(e) => setForm((c) => ({ ...c, dataInicio: e.target.value }))} /></label>
                <label><span>Fim</span><input type="datetime-local" value={form.dataFim} onChange={(e) => setForm((c) => ({ ...c, dataFim: e.target.value }))} /></label>
                <label><span>Limite</span><input value={form.quantidadeLimite} onChange={(e) => setForm((c) => ({ ...c, quantidadeLimite: e.target.value }))} /></label>
              </div>

              <div className="form-section">
                <label className="section-title">Aplicar cupom em</label>

                <div style={{ display: 'grid', gap: 8 }}>
                  {[
                    {
                      id: 'servicos-todos',
                      checked: form.aplicarTodosServicos,
                      onChange: () => {
                        setForm((c) => ({ ...c, aplicarTodosServicos: true }))
                        setServicosSelecionados(new Set())
                      },
                      titulo: 'Todos os serviços',
                      detalhe: 'Cupom válido em qualquer serviço',
                    },
                    {
                      id: 'servicos-especificos',
                      checked: !form.aplicarTodosServicos,
                      onChange: () => setForm((c) => ({ ...c, aplicarTodosServicos: false })),
                      titulo: 'Serviços específicos',
                      detalhe: 'Cupom válido apenas nestes serviços',
                    },
                  ].map((item) => (
                    <label
                      key={item.id}
                      htmlFor={item.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        minHeight: 46,
                        padding: '8px 10px',
                        border: '1px solid rgba(255, 255, 255, 0.10)',
                        borderRadius: 12,
                        background: 'rgba(255, 255, 255, 0.03)',
                        cursor: 'pointer',
                      }}
                    >
                      <input
                        id={item.id}
                        type="radio"
                        name="servicos"
                        checked={item.checked}
                        onChange={item.onChange}
                        style={{
                          width: 14,
                          height: 14,
                          margin: 0,
                          flexShrink: 0,
                        }}
                      />
                      <span style={{ display: 'grid', gap: 1, minWidth: 0 }}>
                        <span className="radio-label" style={{ lineHeight: 1.1 }}>{item.titulo}</span>
                        <small style={{ lineHeight: 1.1 }}>{item.detalhe}</small>
                      </span>
                    </label>
                  ))}
                </div>

                {!form.aplicarTodosServicos && (
                  <div className="servicos-lista">
                    <label className="section-title" style={{ marginBottom: 8 }}>
                      Selecione os serviços
                    </label>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 10 }}>
                      <Search size={16} />
                      <input value={termoServico} onChange={(e) => setTermoServico(e.target.value)} placeholder="Filtrar serviço" />
                    </div>
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
                        gap: 8,
                        maxHeight: 220,
                        overflowY: 'auto',
                        paddingRight: 4,
                      }}
                    >
                      {servicosFiltrados.map((servico) => (
                        <label
                          key={servico.id}
                          className="cliente-item"
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 10,
                            minHeight: 48,
                            margin: 0,
                            padding: '8px 10px',
                          }}
                        >
                          <input
                            type="checkbox"
                            checked={servicosSelecionados.has(servico.id)}
                            onChange={(e) => {
                              const next = new Set(servicosSelecionados)
                              if (e.target.checked) next.add(servico.id)
                              else next.delete(servico.id)
                              setServicosSelecionados(next)
                            }}
                            style={{
                              width: 14,
                              height: 14,
                              margin: 0,
                              flexShrink: 0,
                            }}
                          />
                          <span style={{ display: 'grid', gap: 1, minWidth: 0 }}>
                            <strong style={{ fontSize: 13, lineHeight: 1.1 }}>{servico.nome}</strong>
                            <small style={{ lineHeight: 1.1 }}>{servico.descricao || 'Sem descrição'}</small>
                          </span>
                        </label>
                      ))}
                    </div>
                  </div>
                )}
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
          <div className="panel modal-card" style={{ maxWidth: 680, width: '100%' }}>
            <div className="modal-header" style={{ padding: 0, border: 0, marginBottom: 16 }}>
              <div>
                <h2 style={{ marginBottom: 6 }}>Notificar clientes</h2>
                <p>O disparo será enviado para todos os clientes carregados.</p>
              </div>
              <button type="button" className="icon-btn" onClick={() => setNotificarAberto(false)} aria-label="Fechar">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={enviarNotificacoes} style={{ display: 'grid', gap: 12 }}>
              <div className="form-section">
                <label className="section-title">Segmentação</label>

                <div style={{ display: 'grid', gap: 8 }}>
                  {[
                    {
                      id: 'notif-todos',
                      tipo: 'TODOS',
                      titulo: 'Todos os clientes',
                      detalhe: `${clientes.length} clientes carregados`,
                    },
                    {
                      id: 'notif-risco',
                      tipo: 'EM_RISCO',
                      titulo: 'Clientes em risco',
                      detalhe: 'Segmentação do CRM',
                    },
                    {
                      id: 'notif-manual',
                      tipo: 'MANUAL',
                      titulo: 'Seleção manual',
                      detalhe: 'Escolha clientes específicos',
                    },
                  ].map((item) => (
                    <label
                      key={item.id}
                      htmlFor={item.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        minHeight: 46,
                        padding: '8px 10px',
                        border: '1px solid rgba(255, 255, 255, 0.10)',
                        borderRadius: 12,
                        background: 'rgba(255, 255, 255, 0.03)',
                        cursor: 'pointer',
                      }}
                    >
                      <input
                        id={item.id}
                        type="radio"
                        name="segmentacao"
                        checked={notificacao.tipo === item.tipo}
                        onChange={() => setNotificacao((c) => ({ ...c, tipo: item.tipo, clienteIds: [] }))}
                        style={{
                          width: 14,
                          height: 14,
                          margin: 0,
                          flexShrink: 0,
                        }}
                      />
                      <span style={{ display: 'grid', gap: 1, minWidth: 0 }}>
                        <span className="radio-label" style={{ lineHeight: 1.1 }}>{item.titulo}</span>
                        <small style={{ lineHeight: 1.1 }}>{item.detalhe}</small>
                      </span>
                    </label>
                  ))}
                </div>
              </div>

              {notificacao.tipo === 'MANUAL' && (
                <div className="form-section">
                  <label className="section-title">Selecione os clientes</label>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 10 }}>
                    <Search size={16} />
                    <input value={termoCliente} onChange={(e) => setTermoCliente(e.target.value)} placeholder="Filtrar cliente" />
                  </div>
                  <div
                    className="clientes-lista"
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
                      gap: 8,
                      maxHeight: 220,
                      overflowY: 'auto',
                      paddingRight: 4,
                    }}
                  >
                    {clientesFiltrados.map((cliente) => (
                      <label
                        key={cliente.id}
                        className="cliente-item"
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 10,
                          minHeight: 48,
                          margin: 0,
                          padding: '8px 10px',
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={notificacao.clienteIds.includes(cliente.id)}
                          onChange={(e) => {
                            const next = new Set(notificacao.clienteIds)
                            if (e.target.checked) next.add(cliente.id)
                            else next.delete(cliente.id)
                            setNotificacao((c) => ({ ...c, clienteIds: Array.from(next) }))
                          }}
                          style={{
                            width: 14,
                            height: 14,
                            margin: 0,
                            flexShrink: 0,
                          }}
                        />
                        <span style={{ display: 'grid', gap: 1, minWidth: 0 }}>
                          <strong style={{ fontSize: 13, lineHeight: 1.1 }}>{cliente.nome}</strong>
                          <small style={{ lineHeight: 1.1 }}>{cliente.email || exibirTelefone(cliente.telefone) || 'Sem contato'}</small>
                        </span>
                      </label>
                    ))}
                  </div>
                </div>
              )}

              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <Button type="button" variant="secondary" onClick={() => setNotificarAberto(false)} disabled={disparando}>Cancelar</Button>
                <Button type="submit" disabled={disparando}>{disparando ? 'Disparando...' : 'Disparar'}</Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {usoAberto && resumo && (
        <div className="modal-backdrop">
          <div className="panel modal-card" style={{ maxWidth: 800, width: '100%' }}>
            <div className="modal-header" style={{ padding: 0, border: 0, marginBottom: 16 }}>
              <div>
                <h2 style={{ marginBottom: 6 }}>Uso da promoção</h2>
                <p>{resumo.codigo} - {resumo.descricao}</p>
              </div>
              <button type="button" className="icon-btn" onClick={() => setUsoAberto(false)} aria-label="Fechar">
                <X size={18} />
              </button>
            </div>

            <div className="promo-usage-summary-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 12, marginBottom: 16 }}>
              <div className="panel"><strong>{resumo.totalClientesUsaram}</strong><div>Clientes</div></div>

              <div className="panel"><strong>{resumo.totalUsos}</strong><div>Usos</div></div>
              <div className="panel"><strong>{resumo.totalNotificacoesEnviadas}</strong><div>Enviadas</div></div>
              <div className="panel"><strong>{resumo.totalNotificacoesErros}</strong><div>Erros</div></div>
            </div>
            <Button variant="secondary" onClick={() => setUsoAberto(false)}>Fechar</Button>
          </div>
        </div>
      )}

      {modalExcluir && (
        <div className="modal-backdrop">
          <div className="panel modal-card" style={{ maxWidth: 520, width: '100%' }}>
            <div className="modal-header" style={{ padding: 0, border: 0, marginBottom: 16 }}>
              <div>
                <h2 style={{ marginBottom: 6 }}>Excluir promoção</h2>
                <p>Tem certeza que deseja excluir permanentemente <strong>{modalExcluir.codigo}</strong>?</p>
              </div>
              <button type="button" className="icon-btn" onClick={() => setModalExcluir(null)} aria-label="Fechar">
                <X size={18} />
              </button>
            </div>
            <p className="warning" style={{ marginTop: 0 }}>Essa ação é irreversível.</p>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button variant="secondary" onClick={() => setModalExcluir(null)} disabled={Boolean(cupomEmAcao)}>Cancelar</Button>
              <button type="button" className="btn btn-danger" onClick={excluirConfirmado} disabled={Boolean(cupomEmAcao)}>
                {cupomEmAcao ? 'Excluindo...' : 'Excluir'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}

