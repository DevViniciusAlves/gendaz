import { Bike, Clock3, Eye, Package, Plus, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import Modal from '../components/Modal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import Pagination from '../components/Pagination.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { currency, nextId } from '../services/localStore.js'

const formInicial = { clienteId: 1, endereco: '', observações: '', dataPrevisao: '2026-06-18' }

export default function Entregas() {
  const [data, setData] = useLocalData('entregas')
  const [modal, setModal] = useState(false)
  const [buscaProtocolo, setBuscaProtocolo] = useState('')
  const [buscaCliente, setBuscaCliente] = useState('')
  const [form, setForm] = useState(formInicial)
  const [erro, setErro] = useState('')
  const [pagina, setPagina] = useState(1)
  const itensPorPagina = 10

  const entregas = useMemo(() => (Array.isArray(data.entregas) ? data.entregas : []).filter((item) => {
    const matchesProtocolo = String(item.protocolo || '').toLowerCase().includes(buscaProtocolo.toLowerCase())
    const matchesCliente = String(item.clienteNome || '').toLowerCase().includes(buscaCliente.toLowerCase())
    return matchesProtocolo && matchesCliente
  }), [data.entregas, buscaProtocolo, buscaCliente])
  const totalPaginas = Math.max(1, Math.ceil(entregas.length / itensPorPagina))
  const paginaAtual = Math.min(pagina, totalPaginas)
  const entregasPaginadas = useMemo(() => entregas.slice((paginaAtual - 1) * itensPorPagina, paginaAtual * itensPorPagina), [entregas, paginaAtual])

  function abrirModal() {
    setForm(formInicial)
    setErro('')
    setModal(true)
  }

  function salvar(event) {
    event.preventDefault()
    setErro('')
    const cliente = (Array.isArray(data.clientes) ? data.clientes : []).find((item) => item.id === Number(form.clienteId))
    const endereco = form.endereco.trim().replace(/\s+/g, ' ')
    const observações = form.observações.trim()

    if (!cliente) {
      setErro('Selecione um cliente valido.')
      return
    }
    if (endereco.length < 5 || endereco.length > 180) {
      setErro('Endereco deve ter entre 5 e 180 caracteres.')
      return
    }
    if (observações.length > 300) {
      setErro('Observacoes deve ter ate 300 caracteres.')
      return
    }

    setData((current) => ({
      ...current,
      entregas: [...current.entregas, {
        id: nextId(current.entregas),
        protocolo: `AGE${Date.now().toString().slice(-7)}`,
        clienteId: cliente.id,
        clienteNome: cliente.nome,
        responsavel: 'Equipe interna',
        endereco,
        observações,
        dataPrevisao: form.dataPrevisao,
        horaInicio: '18:00',
        horaFim: '19:00',
        total: 180,
        status: 'NOVO',
      }],
    }))
    setModal(false)
    setForm(formInicial)
  }

  function atualizarStatus(id, status) {
    setData((current) => ({ ...current, entregas: current.entregas.map((item) => item.id === id ? { ...item, status } : item) }))
  }

  const emRota = entregas.filter((item) => item.status === 'SAIU_PARA_ENTREGA').length
  const programadas = entregas.filter((item) => item.status === 'NOVO' || item.status === 'PENDENTE').length
  const concluidas = entregas.filter((item) => item.status === 'ENTREGUE').length

  return (
    <section className="page deliveries-page">
      <div className="page-title row-title">
        <div>
          <span className="section-kicker">Logistica</span>
          <h1>Entregas</h1>
          <p>Pesquisa por protocolo ou cliente, indicadores e atualização de status.</p>
        </div>
        <Button icon={Plus} onClick={abrirModal}>Criar entrega</Button>
      </div>

      <div className="delivery-search">
        <div className="search-shell">
          <Search size={18} />
          <input maxLength={40} placeholder="Protocolo" value={buscaProtocolo} onChange={(e) => setBuscaProtocolo(e.target.value)} />
          <small className={buscaProtocolo.length >= 40 ? 'field-hint limit-reached' : 'field-hint'}>{buscaProtocolo.length >= 40 ? 'Limite de caracteres atingido.' : 'Digite o protocolo.'}<strong>{buscaProtocolo.length}/40</strong></small>
        </div>
        <div className="search-shell">
          <Search size={18} />
          <input maxLength={80} placeholder="Nome do cliente" value={buscaCliente} onChange={(e) => setBuscaCliente(e.target.value)} />
          <small className={buscaCliente.length >= 80 ? 'field-hint limit-reached' : 'field-hint'}>{buscaCliente.length >= 80 ? 'Limite de caracteres atingido.' : 'Digite o nome do cliente.'}<strong>{buscaCliente.length}/80</strong></small>
        </div>
      </div>

      <div className="delivery-stats">
        <article><Bike size={24} /><strong>{emRota}</strong><span>em rota</span></article>
        <article><Clock3 size={24} /><strong>{programadas}</strong><span>programadas</span></article>
        <article><Package size={24} /><strong>{concluidas}</strong><span>concluidas</span></article>
      </div>

      <section className="panel">
        <Table columns={[
          { key: 'protocolo', label: 'Protocolo', render: (row) => <strong>{row.protocolo}</strong> },
          { key: 'clienteNome', label: 'Cliente', render: (row) => <div className="stacked"><strong>{row.clienteNome}</strong><small>{row.responsavel}</small></div> },
          { key: 'status', label: 'Status', render: (row) => <StatusBadge status={row.status} /> },
          { key: 'dataPrevisao', label: 'Data', render: (row) => new Date(`${row.dataPrevisao}T12:00:00`).toLocaleDateString('pt-BR') },
          { key: 'horaInicio', label: 'Horario', render: (row) => <div className="stacked"><strong>{row.horaInicio}</strong><small>ate {row.horaFim}</small></div> },
          { key: 'total', label: 'Total', render: (row) => currency(row.total) },
          { key: 'ação', label: 'Status da entrega', render: (row) => <div className="delivery-action"><select value={row.status} onChange={(e) => atualizarStatus(row.id, e.target.value)}><option value="NOVO">Novo</option><option value="PENDENTE">Pendente</option><option value="EM_SEPARACAO">Em separacao</option><option value="SAIU_PARA_ENTREGA">Saiu para entrega</option><option value="ENTREGUE">Entregue</option><option value="CANCELADA">Cancelada</option></select><button className="icon-btn" title="Ver entrega" aria-label={`Ver entrega ${row.protocolo}`}><Eye size={16} /></button></div> },
        ]} rows={entregasPaginadas} />
        <Pagination page={paginaAtual} totalPages={totalPaginas} totalItems={entregas.length} pageSize={itensPorPagina} onPageChange={setPagina} />
      </section>

      <Modal title="Criar entrega" open={modal} onClose={() => setModal(false)}>
        <form className="form-grid" onSubmit={salvar}>
          <label className="field"><span>Cliente</span><select value={form.clienteId} onChange={(e) => setForm({ ...form, clienteId: e.target.value })}>{(Array.isArray(data.clientes) ? data.clientes : []).map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
          <Input label="Endereco" helper="Informe um endereco objetivo." maxLength={180} value={form.endereco} onChange={(e) => setForm({ ...form, endereco: e.target.value })} required />
          <Input label="Observacoes" helper="Use uma observação curta." maxLength={300} value={form.observações} onChange={(e) => setForm({ ...form, observações: e.target.value })} />
          <Input label="Data prevista" helper="Escolha uma data valida." type="date" value={form.dataPrevisao} onChange={(e) => setForm({ ...form, dataPrevisao: e.target.value })} />
          {erro && <p className="form-error field-wide">{erro}</p>}
          <Button type="submit">Salvar</Button>
        </form>
      </Modal>
    </section>
  )
}
