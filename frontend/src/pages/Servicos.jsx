import { Pencil, Plus, Power, RefreshCw, Trash } from 'lucide-react'
import { useContext, useEffect, useState } from 'react'
import { RefreshContext } from '../context/RefreshContext.jsx'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import Modal from '../components/Modal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import ActionMenu from '../components/ActionMenu.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { currency } from '../services/localStore.js'

const formInicial = { nome: '', descricao: '', duracaoMinutos: 30, valor: 100 }

function formatarDuracao(minutos) {
  const total = Number(minutos) || 0
  if (total < 60) return `${total} min`
  const horas = Math.floor(total / 60)
  const resto = total % 60
  if (!resto) return horas === 1 ? '1 hora' : `${horas} horas`
  return `${horas}h ${resto}min`
}

export default function Servicos() {
  const [data, , { loading, reload }] = useLocalData('servicos')
  const { refreshTrigger } = useContext(RefreshContext)
  const [modal, setModal] = useState(false)
  const [form, setForm] = useState(formInicial)
  const [erro, setErro] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [acaoId, setAcaoId] = useState(null)
  const [servicoEditando, setServicoEditando] = useState(null)
  const [recarregando, setRecarregando] = useState(false)
  const [confirmacao, setConfirmacao] = useState(null)

  useEffect(() => {
    reload(true)
  }, [refreshTrigger, reload])

  function abrirNovo() {
    setServicoEditando(null)
    setForm(formInicial)
    setErro('')
    setModal(true)
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

  async function salvar(event) {
    event.preventDefault()
    if (salvando) return
    setErro('')

    const nome = form.nome.trim().replace(/\s+/g, ' ')
    const descricao = form.descricao?.trim() || null
    const duracaoMinutos = form.duracaoMinutos ? Number(form.duracaoMinutos) : null
    const valor = form.valor !== '' && form.valor !== null && form.valor !== undefined ? Number(form.valor) : null

    if (nome.length < 2 || nome.length > 80) {
      setErro('Nome deve ter entre 2 e 80 caracteres.')
      return
    }
    if (!/^[\p{L}\s]+$/u.test(nome)) {
      setErro('Nome deve conter apenas letras.')
      return
    }
    if (descricao && descricao.length > 300) {
      setErro('Descrição deve ter até 300 caracteres.')
      return
    }
    if (duracaoMinutos !== null && (!Number.isInteger(duracaoMinutos) || duracaoMinutos < 5 || duracaoMinutos > 720)) {
      setErro('Duração deve ter entre 5 e 720 minutos.')
      return
    }
    if (valor !== null && (!Number.isFinite(valor) || valor < 0 || valor > 999999)) {
      setErro('Valor deve ser maior ou igual a zero.')
      return
    }

    setSalvando(true)
    try {
      const payload = { nome, descricao, valor, duracaoMinutos }
      if (servicoEditando) {
        await appApi.atualizarServico(servicoEditando, payload)
      } else {
        await appApi.criarServico(payload)
      }
      await reload(true)
      setModal(false)
      setForm(formInicial)
      setServicoEditando(null)
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Não foi possível salvar o serviço.')
    } finally {
      setSalvando(false)
    }
  }

  async function alternar(servico) {
    if (acaoId) return
    setAcaoId(servico.id)
    setErro('')
    try {
      await appApi.alterarStatusServico(servico.id, servico.status)
      await reload(true)
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Não foi possível alterar o status do serviço.')
    } finally {
      setAcaoId(null)
    }
  }

  function editar(servico) {
    setServicoEditando(servico.id)
    setForm({
      nome: servico.nome || '',
      descricao: servico.descricao || '',
      duracaoMinutos: servico.duracaoMinutos !== null && servico.duracaoMinutos !== undefined ? servico.duracaoMinutos : '',
      valor: servico.valor !== null && servico.valor !== undefined ? servico.valor : '',
    })
    setErro('')
    setModal(true)
  }

  async function excluir(servico) {
    if (acaoId) return
    setConfirmacao({
      titulo: 'Excluir serviço',
      descricao: `Tem certeza que deseja remover ${servico.nome}? Essa ação é permanente e não terá como retornar.`,
      acaoLabel: 'Excluir',
      acao: async () => {
        setAcaoId(servico.id)
        setErro('')
        try {
          await appApi.excluirServico(servico.id)
          await reload(true)
        } catch (error) {
          setErro(error.response?.data?.mensagem || 'Não foi possível remover o serviço.')
        } finally {
          setAcaoId(null)
        }
      },
    })
  }

  return (
    <section className="page">
      <div className="page-title row-title">
        <div>
          <span className="section-kicker">Catálogo</span>
          <h1>Serviços</h1>
          <p>Cadastro de serviços, duração, valor e status conectado ao backend.</p>
        </div>
        <div className="table-actions">
          <Button variant="secondary" icon={RefreshCw} onClick={recarregar} disabled={recarregando}>
            {recarregando ? 'Recarregando...' : 'Recarregar'}
          </Button>
          <Button icon={Plus} onClick={abrirNovo}>Novo serviço</Button>
        </div>
      </div>

      {loading ? (
        <div className="space-y-3">
          <div className="h-12 animate-pulse rounded bg-gray-700" />
          <div className="h-72 animate-pulse rounded bg-gray-700" />
        </div>
      ) : (
        <>
      {!modal && erro && <p className="form-error">{erro}</p>}

      <Table columns={[
        { key: 'nome', label: 'NOME' },
        { key: 'descricao', label: 'DESCRIÇÃO' },
        { key: 'duracaoMinutos', label: 'DURAÇÃO', render: (row) => formatarDuracao(row.duracaoMinutos) },
        { key: 'valor', label: 'VALOR', render: (row) => currency(row.valor) },
        { key: 'status', label: 'STATUS', render: (row) => <StatusBadge status={row.status} /> },
        { key: 'acao', label: 'AÇÕES', render: (row) => (
          <ActionMenu
            actions={[
              { label: 'Editar', icon: Pencil, onClick: () => editar(row) },
              { label: row.status === 'ATIVO' ? 'Desativar' : 'Ativar', icon: Power, onClick: () => alternar(row) },
              { label: 'Excluir', icon: Trash, danger: true, onClick: () => excluir(row) },
            ]}
          />
        ) },
      ]} rows={data.servicos} />

      <Modal title={servicoEditando ? 'Editar serviço' : 'Cadastrar serviço'} open={modal} onClose={() => setModal(false)}>
        <form className="form-grid" onSubmit={salvar}>
          <Input label="Nome" helper="Digite apenas letras." maxLength={80} value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value.replace(/[^\p{L}\s]/gu, '') })} required />
          <Input label="Descrição" helper="Limite a descrição ao essencial." maxLength={300} value={form.descricao} onChange={(e) => setForm({ ...form, descricao: e.target.value })} />
          <Input label="Duração em minutos" helper="Digite apenas números, entre 5 e 720." type="number" min="5" max="720" value={form.duracaoMinutos} onChange={(e) => setForm({ ...form, duracaoMinutos: e.target.value.replace(/\D/g, '') })} />
          <Input label="Valor" helper="Informe um valor maior que zero." type="number" min="0.01" step="0.01" value={form.valor} onChange={(e) => setForm({ ...form, valor: e.target.value })} />
          {erro && <p className="form-error field-wide">{erro}</p>}
          <Button type="submit" disabled={salvando}>{salvando ? 'Salvando...' : 'Salvar'}</Button>
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
        </>
      )}
    </section>
  )
}




