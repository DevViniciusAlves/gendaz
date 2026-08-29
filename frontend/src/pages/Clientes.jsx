import { Pencil, Plus, Power, RefreshCw, Trash, Download } from 'lucide-react'
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
import { exibirTelefone, normalizarParaApi, normalizarParaInput, obterExemploTelefone, validarTelefone } from '../utils/phoneUtils.js'
import InternationalPhoneInput from '../components/InternationalPhoneInput.jsx'
import { exportarCsv, formatarData, dataHojeDdMmAAAA } from '../utils/csvExport.js'

const formInicial = { nome: '', telefone: '', email: '', observações: '' }

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
    return (Array.isArray(data.clientes) ? data.clientes : [])
      .map((cliente) => ({
        ...cliente,
        statusCliente: cliente.statusCliente || cliente.status || 'ATIVO',
      }))
      .filter((cliente) => cliente.statusCliente !== 'EXCLUIDO')
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

  function abrirBulk(ação) {
    if (!selectedCount) {
      setErro('Selecione pelo menos um item.')
      return
    }
    const config = {
      ATIVAR: {
        titulo: 'Ativar clientes',
        descrição: 'Tem certeza que deseja ativar os clientes selecionados?',
        confirmLabel: 'Ativar',
        danger: false,
      },
      DESATIVAR: {
        titulo: 'Desativar clientes',
        descrição: 'Tem certeza que deseja desativar os clientes selecionados?',
        confirmLabel: 'Desativar',
        danger: false,
      },
      EXCLUIR: {
        titulo: 'Excluir clientes',
        descrição: 'Tem certeza que deseja excluir os clientes selecionados? Essa ação não poderá ser desfeita.',
        confirmLabel: 'Excluir',
        danger: true,
      },
    }[ação]
    setBulkModal({ ação, ...config })
  }

  async function executarBulk() {
    if (!bulkModal || bulkExecutando) return
    setBulkExecutando(true)
    setErro('')
    try {
      if (bulkModal.ação === 'ATIVAR') {
        await appApi.ativarClientesEmMassa(selecionados)
      } else if (bulkModal.ação === 'DESATIVAR') {
        await appApi.desativarClientesEmMassa(selecionados)
      } else if (bulkModal.ação === 'EXCLUIR') {
        await appApi.excluirClientesEmMassa(selecionados)
      }
      await reload(true)
      window.dispatchEvent(new Event('gendaz:data-changed'))
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

  function confirmarExportacao() {
    if (!clientes.length) {
      window.dispatchEvent(new CustomEvent('gendaz:toast', {
        detail: { type: 'error', message: 'Nenhum registro encontrado para exportação.' },
      }))
      return
    }
    setConfirmacao({
      titulo: 'Exportar clientes',
      descrição: `Deseja exportar todos os ${clientes.length} cliente(s)?`,
      acaoLabel: 'Exportar',
      ação: async () => {
        const columns = [
          'ID', 'Nome', 'Telefone', 'E-mail', 'Situação do cliente', 'Total gasto',
          'Quantidade de atendimentos', 'Último atendimento', 'Data de cadastro', 'Observações'
        ]
        const rows = clientes.map((cliente) => [
          cliente.id,
          cliente.nome,
          exibirTelefone(cliente.telefone),
          cliente.email || '',
          cliente.statusCliente === 'ATIVO' ? 'Ativo' : cliente.statusCliente === 'INATIVO' ? 'Inativo' : 'Excluído',
          currency(cliente.totalGasto || 0),
          cliente.quantidadeAtendimentos || 0,
          cliente.ultimoAtendimento ? formatarData(cliente.ultimoAtendimento) : '',
          cliente.dataCriacao ? formatarData(cliente.dataCriacao) : '',
          cliente.observações || '',
        ])
        exportarCsv({
          fileName: `clientes-gendaz-${dataHojeDdMmAAAA()}.csv`,
          columns,
          rows,
        })
        window.dispatchEvent(new CustomEvent('gendaz:toast', {
          detail: { type: 'success', message: 'Arquivo CSV exportado com sucesso.' },
        }))
      },
    })
  }

  function abrirEdicao(cliente) {
    setClienteEditando(cliente.id)
    setForm({
      nome: cliente.nome || '',
      telefone: normalizarParaInput(cliente.telefone || ''),
      email: cliente.email || '',
      observações: cliente.observações || '',
    })
    setErro('')
    setModal(true)
  }

  function ativarDesativar(cliente) {
    const ação = cliente.statusCliente === 'ATIVO' ? appApi.desativarCliente(cliente.id) : appApi.ativarCliente(cliente.id)
    ação.then(() => reload(true)).catch((error) => {
      setErro(error.response?.data?.mensagem || 'Não foi possível alterar o status do cliente.')
    })
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
      descrição: `Tem certeza que deseja excluir ${cliente.nome}? O nome original será preservado e os vínculos históricos continuarão disponíveis. Não será possível reverter.`,
      acaoLabel: 'Excluir',
      ação: async () => {
        setErro('')
        try {
          await appApi.excluirCliente(cliente.id)
          setTimeout(() => {
            triggerRefreshAll()
            reload(true)
            window.dispatchEvent(new Event('gendaz:data-changed'))
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
    const telefone = normalizarParaApi(form.telefone)
    if (!telefone) {
      setErro('Telefone invalido. Use codigo da cidade + numero.')
      return
    }
    if (!email || email.length > 120 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setErro('Informe um e-mail válido com até 120 caracteres.')
      return
    }

    setSalvando(true)
    try {
      const payload = {
        ...form,
        nome,
        telefone,
        email,
        observações: form.observações?.trim() || null,
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
           <Button variant="secondary" icon={Download} onClick={confirmarExportacao} loading={recarregando} loadingText="Exportando...">
             Exportar CSV
           </Button>
           <Button variant="secondary" icon={RefreshCw} onClick={recarregar} loading={recarregando} loadingText="Recarregando...">
             Recarregar
           </Button>
           <Button icon={Plus} onClick={abrirNovo}>Novo cliente</Button>
          <BulkActionsToolbar
            selectionMode={selecionando}
            selectedCount={selectedCount}
            onToggleSelection={() => setSelecionando(true)}
            onClearSelection={limparSelecao}
            actions={[
              { label: 'Ativar', onClick: () => abrirBulk('ATIVAR') },
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
                <span>{exibirTelefone(row.telefone)}</span>
             </div>
          )},
          { key: 'email', label: 'E-MAIL' },
          { key: 'statusCliente', label: 'SITUAÇÃO DO CLIENTE', render: (row) => <StatusBadge status={row.statusCliente || row.status || 'ATIVO'} /> },
          { key: 'totalGasto', label: 'TOTAL GASTO', render: (row) => currency(row.totalGasto) },
          { key: 'observações', label: 'HISTÓRICO', render: (row) => (
             <div className="stacked-cell">
               <strong>{row.statusCliente === 'EXCLUIDO' ? 'Excluído' : (row.observações || 'Sem histórico')}</strong>
             </div>
          ) },
          { key: 'ação', label: 'AÇÕES', render: (row) => (
            row.statusCliente === 'EXCLUIDO' ? null : (
            <ActionMenu
              actions={[
                { label: 'Editar', icon: Pencil, onClick: () => abrirEdicao(row) },
                { label: row.statusCliente === 'ATIVO' ? 'Desativar' : 'Ativar', icon: Power, onClick: () => ativarDesativar(row) },
                { label: 'Excluir', icon: Trash, danger: true, onClick: () => excluir(row) },
              ]}
            />
            )
          )},
        ]}
        rows={clientesPaginados}
      />
      <Pagination page={paginaAtual} totalPages={totalPaginas} totalItems={clientes.length} pageSize={itensPorPagina} onPageChange={setPagina} />

      <Modal title={clienteEditando ? 'Editar cliente' : 'Cadastrar cliente'} open={modal} onClose={() => setModal(false)}>
        <form className="form-grid" onSubmit={salvar}>
          <Input label="Nome" helper="Digite apenas letras." maxLength={80} value={form.nome} onChange={(e) => setForm({ ...form, nome: limparNome(e.target.value) })} required />
          <InternationalPhoneInput
            label="Telefone"
            helper={form.telefone ? (validarTelefone(form.telefone) || 'Pronto para confirmar') : `Exemplo para o país selecionado: ${obterExemploTelefone('BR') || '+55 (65) 99336-0341'}`}
            value={form.telefone}
            onChangeValue={(valor) => setForm({ ...form, telefone: valor || '' })}
            required
          />
          <Input label="E-mail" helper="Use um e-mail válido." type="email" maxLength={120} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <Input label="Observações" helper="Resumo curto do histórico do cliente." maxLength={300} value={form.observações} onChange={(e) => setForm({ ...form, observações: e.target.value })} />
          {erro && <p className="form-error field-wide">{erro}</p>}
           <Button type="submit" loading={salvando} loadingText="Salvando...">
             Salvar
           </Button>
        </form>
      </Modal>

      <Modal title={confirmacao?.titulo || 'Confirmar ação'} open={Boolean(confirmacao)} onClose={() => setConfirmacao(null)}>
        <div className="confirm-box">
          <p>{confirmacao?.descrição}</p>
          <div className="confirm-actions">
            <Button variant="secondary" type="button" onClick={() => setConfirmacao(null)}>Cancelar</Button>
           <Button
             type="button"
             loading={false}
             onClick={async () => {
               const ação = confirmacao?.ação
               setConfirmacao(null)
               if (ação) await ação()
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
        description={bulkModal?.descrição || ''}
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





