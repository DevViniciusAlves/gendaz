import { Pencil, Plus, Power, RefreshCw, Trash } from 'lucide-react'
import { useContext, useEffect, useMemo, useState } from 'react'
import { RefreshContext } from '../context/RefreshContext.jsx'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import Modal from '../components/Modal.jsx'
import Table from '../components/Table.jsx'
import ActionMenu from '../components/ActionMenu.jsx'
import Pagination from '../components/Pagination.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import BulkActionsToolbar from '../components/BulkActionsToolbar.jsx'
import BulkConfirmModal from '../components/BulkConfirmModal.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { currency } from '../services/localStore.js'
// âš ï¸ DESATIVADO — import whatsappLogo from '../assets/whatsapp.png'
import { aplicarMascara, exibirTelefone, padronizarTelefone, validarTelefone } from '../utils/phoneUtils.js'

const formInicial = { nome: '', telefone: '', email: '', observacoes: '' }

function limparNome(valor) {
  return valor.replace(/[^\p{L}\s]/gu, '')
}

export default function Clientes() {
  const [data, , { loading, reload }] = useLocalData('clientes')
  const { refreshTrigger, triggerRefreshAll } = useContext(RefreshContext)
  const [busca, setBusca] = useState('')
  const [modal, setModal] = useState(false)
  const [clienteEditando, setClienteEditando] = useState(null)
  const [form, setForm] = useState(formInicial)
  const [erro, setErro] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [recarregando, setRecarregando] = useState(false)
  const [pagina, setPagina] = useState(1)
  const [confirmacao, setConfirmacao] = useState(null)
  const [selecionando, setSelecionando] = useState(false)
  const [selecionados, setSelecionados] = useState([])
  const [bulkModal, setBulkModal] = useState(null)
  const [bulkExecutando, setBulkExecutando] = useState(false)
  const itensPorPagina = 10

  useEffect(() => {
    reload(true)
  }, [refreshTrigger, reload])

  const clientes = useMemo(() => {
    return data.clientes
      .map((cliente) => ({
        ...cliente,
        status: localStorage.getItem(`cliente_status_${cliente.id}`) || 'ATIVO',
      }))
      .filter((cliente) => `${cliente.nome} ${cliente.telefone} ${cliente.email || ''}`.toLowerCase().includes(busca.toLowerCase()))
  }, [data.clientes, busca])
  const totalPaginas = Math.max(1, Math.ceil(clientes.length / itensPorPagina))
  const paginaAtual = Math.min(pagina, totalPaginas)
  const clientesPaginados = useMemo(() => clientes.slice((paginaAtual - 1) * itensPorPagina, paginaAtual * itensPorPagina), [clientes, paginaAtual])
  const selectedCount = selecionados.length

  function limparSelecao() {
    setSelecionando(false)
    setSelecionados([])
    setBulkModal(null)
  }

  function alternarSelecionado(id) {
    setSelecionados((current) => {
      if (current.includes(id)) {
        return current.filter((item) => item !== id)
      }
      if (current.length >= 10) {
        setErro('Você pode selecionar no máximo 10 itens por vez.')
        return current
      }
      setErro('')
      return [...current, id]
    })
  }

  function abrirBulk(acao) {
    if (!selectedCount) {
      setErro('Selecione pelo menos um item.')
      return
    }
    const config = {
      DESATIVAR: {
        titulo: 'Desativar clientes',
        descricao: 'Tem certeza que deseja desativar os clientes selecionados?',
        confirmLabel: 'Desativar',
        danger: false,
      },
      EXCLUIR: {
        titulo: 'Excluir clientes',
        descricao: 'Tem certeza que deseja excluir os clientes selecionados? Essa ação não poderá ser desfeita.',
        confirmLabel: 'Excluir',
        danger: true,
      },
    }[acao]
    setBulkModal({ acao, ...config })
  }

  async function executarBulk() {
    if (!bulkModal || bulkExecutando) return
    setBulkExecutando(true)
    setErro('')
    try {
      if (bulkModal.acao === 'DESATIVAR') {
        selecionados.forEach((id) => {
          localStorage.setItem(`cliente_status_${id}`, 'INATIVO')
        })
        await reload(true)
      } else {
        await appApi.excluirClientesEmMassa(selecionados)
        await reload(true)
        window.dispatchEvent(new Event('agendapro:data-changed'))
      }
      limparSelecao()
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Não foi possível executar a ação em massa.')
    } finally {
      setBulkExecutando(false)
    }
  }

  function abrirNovo() {
    setClienteEditando(null)
    setForm(formInicial)
    setErro('')
    setModal(true)
  }

  function abrirEdicao(cliente) {
    setClienteEditando(cliente.id)
    setForm({
      nome: cliente.nome || '',
      telefone: aplicarMascara(cliente.telefone || ''),
      email: cliente.email || '',
      observacoes: cliente.observacoes || '',
    })
    setErro('')
    setModal(true)
  }

  function ativarDesativar(cliente) {
    const novoStatus = cliente.status === 'ATIVO' ? 'INATIVO' : 'ATIVO'
    localStorage.setItem(`cliente_status_${cliente.id}`, novoStatus)
    reload(true)
  }

  async function recarregar() {
    if (recarregando) return
    setRecarregando(true)
    try {
      await reload(true)
    } finally {
      setRecarregando(false)
    }
  }

  async function excluir(cliente) {
    setConfirmacao({
      titulo: 'Excluir cliente',
      descricao: `Tem certeza que deseja excluir ${cliente.nome}? Essa ação é permanente e não terá como retornar.`,
      acaoLabel: 'Excluir',
      acao: async () => {
        setErro('')
        try {
          await appApi.excluirCliente(cliente.id)
          setTimeout(() => {
            triggerRefreshAll()
            reload(true)
            window.dispatchEvent(new Event('agendapro:data-changed'))
          }, 2000)
        } catch (error) {
          setErro(error.response?.data?.mensagem || 'Não foi possível excluir o cliente.')
        }
      },
    })
  }

  async function salvar(event) {
    event.preventDefault()
    if (salvando) return
    setErro('')

    const nome = form.nome.trim().replace(/\s+/g, ' ')
    const email = form.email.trim().toLowerCase()

    if (!/^[\p{L} ]{2,80}$/u.test(nome)) {
      setErro('Nome deve ter 2 a 80 letras.')
      return
    }
    const telValidationError = validarTelefone(form.telefone)
    if (telValidationError) {
      setErro(telValidationError)
      return
    }
    const telefone = padronizarTelefone(form.telefone)
    if (!telefone) {
      setErro('Telefone invalido. Use codigo da cidade + numero.')
      return
    }
    if (email && (email.length > 120 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))) {
      setErro('Informe um e-mail válido com até 120 caracteres.')
      return
    }

    setSalvando(true)
    try {
      const payload = {
        ...form,
        nome,
        telefone,
        email: email || null,
        observacoes: form.observacoes?.trim() || null,
      }
      if (clienteEditando) {
        await appApi.atualizarCliente(clienteEditando, payload)
      } else {
        await appApi.criarCliente(payload)
      }
      await reload(true)
      setModal(false)
      setClienteEditando(null)
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Não foi possível salvar o cliente.')
    } finally {
      setSalvando(false)
    }
  }

  return (
    <section className="page clients-page">
      <div className="page-title row-title">
        <div>
          <span className="section-kicker">Base de clientes</span>
          <h1>Clientes</h1>
          <p>Busca, cadastro e histórico básico da base atendida.</p>
        </div>
        <div className="table-actions">
          <Button variant="secondary" icon={RefreshCw} onClick={recarregar} disabled={recarregando}>
            {recarregando ? 'Recarregando...' : 'Recarregar'}
          </Button>
          <Button icon={Plus} onClick={abrirNovo}>Novo cliente</Button>
          <BulkActionsToolbar
            selectionMode={selecionando}
            selectedCount={selectedCount}
            onToggleSelection={() => setSelecionando(true)}
            onClearSelection={limparSelecao}
            actions={[
              { label: 'Desativar', onClick: () => abrirBulk('DESATIVAR') },
              { label: 'Excluir', danger: true, onClick: () => abrirBulk('EXCLUIR') },
            ]}
          />
        </div>
      </div>

      {loading ? (
        <div className="space-y-3">
          <div className="h-12 animate-pulse rounded bg-gray-700" />
          <div className="h-72 animate-pulse rounded bg-gray-700" />
        </div>
      ) : (
        <>
      <div className="filters">
        <label className="field search-field">
          <input maxLength={80} placeholder="Buscar por nome, telefone ou e-mail" value={busca} onChange={(e) => setBusca(e.target.value)} />
          <small className={busca.length >= 80 ? 'field-hint limit-reached' : 'field-hint'}>{busca.length >= 80 ? 'Limite de caracteres atingido.' : 'Busque por nome, telefone ou e-mail.'}<strong>{busca.length}/80</strong></small>
        </label>
      </div>

      <Table
        columns={[
          ...(selecionando ? [{
            key: '__selecionar',
            label: '',
            render: (row) => (
              <input
                type="checkbox"
                checked={selecionados.includes(row.id)}
                onChange={() => alternarSelecionado(row.id)}
                disabled={!selecionados.includes(row.id) && selectedCount >= 10}
                aria-label={`Selecionar cliente ${row.nome}`}
              />
            ),
          }] : []),
          { key: 'nome', label: 'NOME', render: (row) => {
            const iniciais = (row.nome || 'CL').substring(0, 2).toUpperCase();
            return (
              <div className="name-cell">
                <div className="avatar">{iniciais}</div>
                <div className="name-cell-info">
                  <strong>{row.nome}</strong>
                  <small>Cliente</small>
                </div>
              </div>
            )
          }},
          { key: 'telefone', label: 'TELEFONE', render: (row) => (
             <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                {/* âš ï¸ DESATIVADO — <img src={whatsappLogo} alt="WhatsApp" style={{ width: '18px', height: '18px', flexShrink: 0 }} /> */}
                <span>{exibirTelefone(row.telefone)}</span>
             </div>
          )},
          { key: 'email', label: 'E-MAIL' },
          { key: 'status', label: 'STATUS', render: (row) => <StatusBadge status={row.status} /> },
          { key: 'totalGasto', label: 'TOTAL GASTO', render: (row) => currency(row.totalGasto) },
          { key: 'observacoes', label: 'HISTÃ“RICO', render: (row) => (
             <div className="stacked-cell">
               <strong>{row.observacoes || 'Sem histórico'}</strong>
             </div>
          ) },
          { key: 'acao', label: 'AÃ‡Ã•ES', render: (row) => (
            <ActionMenu
              actions={[
                { label: 'Editar', icon: Pencil, onClick: () => abrirEdicao(row) },
                { label: row.status === 'ATIVO' ? 'Desativar' : 'Ativar', icon: Power, onClick: () => ativarDesativar(row) },
                { label: 'Excluir', icon: Trash, danger: true, onClick: () => excluir(row) },
              ]}
            />
          )},
        ]}
        rows={clientesPaginados}
      />
      <Pagination page={paginaAtual} totalPages={totalPaginas} totalItems={clientes.length} pageSize={itensPorPagina} onPageChange={setPagina} />

      <Modal title={clienteEditando ? 'Editar cliente' : 'Cadastrar cliente'} open={modal} onClose={() => setModal(false)}>
        <form className="form-grid" onSubmit={salvar}>
          <Input label="Nome" helper="Digite apenas letras." maxLength={80} value={form.nome} onChange={(e) => setForm({ ...form, nome: limparNome(e.target.value) })} required />
          <Input
            label="Telefone"
            helper={form.telefone ? (validarTelefone(form.telefone) || 'Pronto para confirmar') : 'Use codigo da cidade + numero.'}
            inputMode="numeric"
            maxLength={19}
            neutralLimit
            value={form.telefone}
            onChange={(e) => setForm({ ...form, telefone: aplicarMascara(e.target.value) })}
            required
          />
          <Input label="E-mail" helper="Use um e-mail válido." type="email" maxLength={120} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <Input label="ObservaçÃµes" helper="Resumo curto do histórico do cliente." maxLength={300} value={form.observacoes} onChange={(e) => setForm({ ...form, observacoes: e.target.value })} />
          {erro && <p className="form-error field-wide">{erro}</p>}
          <Button type="submit" disabled={salvando || !form.nome || (validarTelefone(form.telefone) !== '')}>
            {salvando ? 'Salvando...' : 'Salvar'}
          </Button>
        </form>
      </Modal>

      <Modal title={confirmacao?.titulo || 'Confirmar ação'} open={Boolean(confirmacao)} onClose={() => setConfirmacao(null)}>
        <div className="confirm-box">
          <p>{confirmacao?.descricao}</p>
          <div className="confirm-actions">
            <Button variant="secondary" type="button" onClick={() => setConfirmacao(null)}>Cancelar</Button>
            <Button
              type="button"
              onClick={async () => {
                const acao = confirmacao?.acao
                setConfirmacao(null)
                if (acao) await acao()
              }}
            >
              {confirmacao?.acaoLabel || 'Confirmar'}
            </Button>
          </div>
        </div>
      </Modal>
      <BulkConfirmModal
        open={Boolean(bulkModal)}
        title={bulkModal?.titulo || 'Confirmar ação'}
        description={bulkModal?.descricao || ''}
        confirmLabel={bulkModal?.confirmLabel || 'Confirmar'}
        danger={Boolean(bulkModal?.danger)}
        loading={bulkExecutando}
        onCancel={() => setBulkModal(null)}
        onConfirm={executarBulk}
      />
        </>
      )}
    </section>
  )
}




