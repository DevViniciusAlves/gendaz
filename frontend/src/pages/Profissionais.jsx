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
import { aplicarMascara, padronizarTelefone, validarTelefone } from '../utils/phoneUtils.js'

const formInicial = { nome: '', especialidade: '', telefone: '' }

export default function Profissionais() {
  const [data, , { reload }] = useLocalData('profissionais')
  const { refreshTrigger } = useContext(RefreshContext)
  const [modal, setModal] = useState(false)
  const [modalEditar, setModalEditar] = useState(false)
  const [form, setForm] = useState(formInicial)
  const [edicao, setEdicao] = useState(null)
  const [erro, setErro] = useState('')
  const [erroEditar, setErroEditar] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [salvandoEditar, setSalvandoEditar] = useState(false)
  const [acaoId, setAcaoId] = useState(null)
  const [recarregando, setRecarregando] = useState(false)
  const [confirmacao, setConfirmacao] = useState(null)

  useEffect(() => {
    reload(true)
  }, [refreshTrigger, reload])

  function abrirNovo() {
    setForm(formInicial)
    setErro('')
    setModal(true)
  }

  function abrirEdicao(profissional) {
    setEdicao({
      id: profissional.id,
      nome: profissional.nome || '',
      especialidade: profissional.especialidade || '',
      telefone: profissional.telefone || '',
    })
    setErroEditar('')
    setModalEditar(true)
  }

  function validarForm(f) {
    const nome = f.nome.trim().replace(/\s+/g, ' ')
    const especialidade = f.especialidade.trim().replace(/\s+/g, ' ')
    if (!/^[\p{L} ]{2,80}$/u.test(nome)) return 'Nome deve ter 2 a 80 letras.'
    if (especialidade && !/^[\p{L} ]{2,80}$/u.test(especialidade)) return 'Especialidade deve ter 2 a 80 letras.'
    if (f.telefone) {
      const telErr = validarTelefone(f.telefone)
      if (telErr) return telErr
    }
    return ''
  }

  function normalizarForm(f) {
    return {
      nome: f.nome.trim().replace(/\s+/g, ' '),
      especialidade: f.especialidade.trim().replace(/\s+/g, ' ') || null,
      telefone: f.telefone ? (padronizarTelefone(f.telefone) || null) : null,
    }
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
    const erroValidacao = validarForm(form)
    if (erroValidacao) { setErro(erroValidacao); return }
    setErro('')
    setSalvando(true)
    try {
      await appApi.criarProfissional(normalizarForm(form))
      await reload(true)
      setModal(false)
      setForm(formInicial)
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'N�o foi poss�vel salvar o profissional.')
    } finally {
      setSalvando(false)
    }
  }

  async function salvarEdicao(event) {
    event.preventDefault()
    if (salvandoEditar) return
    const erroValidacao = validarForm(edicao)
    if (erroValidacao) { setErroEditar(erroValidacao); return }
    setErroEditar('')
    setSalvandoEditar(true)
    try {
      await appApi.atualizarProfissional(edicao.id, normalizarForm(edicao))
      await reload(true)
      setModalEditar(false)
      setEdicao(null)
    } catch (error) {
      setErroEditar(error.response?.data?.mensagem || 'N�o foi poss�vel atualizar o profissional.')
    } finally {
      setSalvandoEditar(false)
    }
  }

  async function alternarProfissional(profissional) {
    if (acaoId) return
    setAcaoId(profissional.id)
    setErro('')
    try {
      await appApi.alterarStatusProfissional(profissional.id, profissional.status)
      await reload(true)
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'N�o foi poss�vel alterar o status do profissional.')
    } finally {
      setAcaoId(null)
    }
  }

  async function excluir(profissional) {
    if (acaoId) return
    setConfirmacao({
      titulo: 'Excluir profissional',
      descricao: `Deseja excluir o profissional "${profissional.nome}"? Esta a��o � permanente e n�o ter� como retornar.`,
      acaoLabel: 'Excluir',
      acao: async () => {
        setAcaoId(profissional.id)
        setErro('')
        try {
          await appApi.excluirProfissional(profissional.id)
          await reload(true)
        } catch (error) {
          setErro(error.response?.data?.mensagem || 'N�o foi poss�vel excluir o profissional.')
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
          <span className="section-kicker">Equipe</span>
          <h1>Profissionais</h1>
          <p>Cadastro dos profissionais que atendem na agenda, conectado ao backend.</p>
        </div>
        <div className="table-actions">
          <Button variant="secondary" icon={RefreshCw} onClick={recarregar} disabled={recarregando}>
            {recarregando ? 'Recarregando...' : 'Recarregar'}
          </Button>
          <Button icon={Plus} onClick={abrirNovo}>Novo profissional</Button>
        </div>
      </div>

      {!modal && !modalEditar && erro && <p className="form-error">{erro}</p>}

      <Table columns={[
        { key: 'nome', label: 'NOME' },
        { key: 'especialidade', label: 'ESPECIALIDADE' },
        { key: 'telefone', label: 'TELEFONE' },
        { key: 'status', label: 'STATUS', render: (row) => <StatusBadge status={row.status} /> },
        { key: 'acao', label: 'A��ES', render: (row) => (
          <ActionMenu
            actions={[
              { label: 'Editar', icon: Pencil, onClick: () => abrirEdicao(row) },
              { label: row.status === 'ATIVO' ? 'Desativar' : 'Ativar', icon: Power, onClick: () => alternarProfissional(row) },
              { label: 'Excluir', icon: Trash, danger: true, onClick: () => excluir(row) },
            ]}
          />
        ) },
      ]} rows={data.profissionais} />

      <Modal title="Cadastrar profissional" open={modal} onClose={() => setModal(false)}>
        <form className="form-grid" onSubmit={salvar}>
          <Input label="Nome" helper="Digite apenas letras." maxLength={80} value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value.replace(/[^\p{L}\s]/gu, '') })} required />
          <Input label="Especialidade" helper="Digite apenas letras." maxLength={80} value={form.especialidade} onChange={(e) => setForm({ ...form, especialidade: e.target.value.replace(/[^\p{L}\s]/gu, '') })} />
          <Input label="Telefone (opcional)" helper={form.telefone ? (validarTelefone(form.telefone) || ' Formato correto') : 'Formato: +55 (DDD) 99999-9999'} inputMode="numeric" maxLength={19} value={form.telefone} onChange={(e) => setForm({ ...form, telefone: aplicarMascara(e.target.value) })} />
          {erro && <p className="form-error field-wide">{erro}</p>}
          <Button type="submit" disabled={salvando}>{salvando ? 'Salvando...' : 'Salvar'}</Button>
        </form>
      </Modal>

      <Modal title="Editar profissional" open={modalEditar} onClose={() => setModalEditar(false)}>
        {edicao && (
          <form className="form-grid" onSubmit={salvarEdicao}>
            <Input label="Nome" helper="Digite apenas letras." maxLength={80} value={edicao.nome} onChange={(e) => setEdicao({ ...edicao, nome: e.target.value.replace(/[^\p{L}\s]/gu, '') })} required />
            <Input label="Especialidade" helper="Digite apenas letras." maxLength={80} value={edicao.especialidade} onChange={(e) => setEdicao({ ...edicao, especialidade: e.target.value.replace(/[^\p{L}\s]/gu, '') })} />
            <Input label="Telefone (opcional)" helper={edicao.telefone ? (validarTelefone(edicao.telefone) || ' Formato correto') : 'Formato: +55 (DDD) 99999-9999'} inputMode="numeric" maxLength={19} value={edicao.telefone} onChange={(e) => setEdicao({ ...edicao, telefone: aplicarMascara(e.target.value) })} />
            {erroEditar && <p className="form-error field-wide">{erroEditar}</p>}
            <Button type="submit" disabled={salvandoEditar}>{salvandoEditar ? 'Salvando...' : 'Salvar altera��es'}</Button>
          </form>
        )}
      </Modal>

      <Modal title={confirmacao?.titulo || 'Confirmar a��o'} open={Boolean(confirmacao)} onClose={() => setConfirmacao(null)}>
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
    </section>
  )
}




