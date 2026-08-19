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
import { normalizarParaApi, normalizarParaInput, obterExemploTelefone, validarTelefone, exibirTelefone } from '../utils/phoneUtils.js'
import InternationalPhoneInput from '../components/InternationalPhoneInput.jsx'

const DIAS_TRABALHO = [
  { valor: 'SEGUNDA', letra: 'S', label: 'Seg', nome: 'Segunda' },
  { valor: 'TERCA', letra: 'T', label: 'Ter', nome: 'Terça' },
  { valor: 'QUARTA', letra: 'Q', label: 'Qua', nome: 'Quarta' },
  { valor: 'QUINTA', letra: 'Q', label: 'Qui', nome: 'Quinta' },
  { valor: 'SEXTA', letra: 'S', label: 'Sex', nome: 'Sexta' },
  { valor: 'SABADO', letra: 'S', label: 'Sáb', nome: 'Sábado' },
  { valor: 'DOMINGO', letra: 'D', label: 'Dom', nome: 'Domingo' },
]

const DIAS_PADRAO = DIAS_TRABALHO.map((dia) => dia.valor)
const formInicial = { nome: '', especialidade: '', telefone: '', diasTrabalho: DIAS_PADRAO }

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
      telefone: normalizarParaInput(profissional.telefone || ''),
      diasTrabalho: Array.isArray(profissional.diasTrabalho) ? profissional.diasTrabalho : DIAS_PADRAO,
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
    if (!Array.isArray(f.diasTrabalho) || f.diasTrabalho.length === 0) return 'Selecione pelo menos um dia de trabalho.'
    return ''
  }

  function normalizarForm(f) {
    return {
      nome: f.nome.trim().replace(/\s+/g, ' '),
      especialidade: f.especialidade.trim().replace(/\s+/g, ' ') || null,
      telefone: f.telefone ? (normalizarParaApi(f.telefone) || null) : null,
      diasTrabalho: Array.isArray(f.diasTrabalho) ? f.diasTrabalho : [],
    }
  }

  function alternarDia(setter, valor) {
    setter((atual) => {
      const diasAtuais = Array.isArray(atual.diasTrabalho) ? atual.diasTrabalho : []
      const diasTrabalho = diasAtuais.includes(valor)
        ? diasAtuais.filter((dia) => dia !== valor)
        : [...diasAtuais, valor]
      return { ...atual, diasTrabalho }
    })
  }

  function DiasTrabalhoSelector({ value, onToggle }) {
    const selecionados = Array.isArray(value) ? value : []
    return (
      <div className="field field-wide dias-trabalho-field">
        <span>Dias de trabalho</span>
        <div className="dias-trabalho-grid">
          {DIAS_TRABALHO.map((dia) => {
            const ativo = selecionados.includes(dia.valor)
            return (
              <button
                key={dia.valor}
                type="button"
                className={ativo ? 'dia-trabalho-btn ativo' : 'dia-trabalho-btn'}
                aria-pressed={ativo}
                aria-label={`${dia.nome} ${ativo ? 'selecionado' : 'não selecionado'}`}
                title={dia.nome}
                onClick={() => onToggle(dia.valor)}
              >
                <strong>{dia.letra}</strong>
                <small>{dia.label}</small>
              </button>
            )
          })}
        </div>
      </div>
    )
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
      setErro(error.response?.data?.mensagem || 'Não foi possível salvar o profissional.')
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
      setErroEditar(error.response?.data?.mensagem || 'Não foi possível atualizar o profissional.')
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
      setErro(error.response?.data?.mensagem || 'Não foi possível alterar o status do profissional.')
    } finally {
      setAcaoId(null)
    }
  }

  async function excluir(profissional) {
    if (acaoId) return
    setConfirmacao({
      titulo: 'Excluir profissional',
      descricao: `Deseja excluir o profissional "${profissional.nome}"? Esta ação é permanente e não terá como retornar.`,
      acaoLabel: 'Excluir',
      acao: async () => {
        setAcaoId(profissional.id)
        setErro('')
        try {
          await appApi.excluirProfissional(profissional.id)
          await reload(true)
        } catch (error) {
          setErro(error.response?.data?.mensagem || 'Não foi possível excluir o profissional.')
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
        { key: 'telefone', label: 'TELEFONE', render: (row) => <span>{exibirTelefone(row.telefone)}</span> },
        { key: 'status', label: 'STATUS', render: (row) => <StatusBadge status={row.status} /> },
        { key: 'acao', label: 'AÇÕES', render: (row) => (
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
          <InternationalPhoneInput label="Telefone (opcional)" helper={form.telefone ? (validarTelefone(form.telefone) || ' Formato correto') : `Exemplo para o país selecionado: ${obterExemploTelefone('BR') || '+55 (65) 99336-0341'}`} value={form.telefone} onChangeValue={(valor) => setForm({ ...form, telefone: valor || '' })} />
          <DiasTrabalhoSelector value={form.diasTrabalho} onToggle={(dia) => alternarDia(setForm, dia)} />
          {erro && <p className="form-error field-wide">{erro}</p>}
          <Button type="submit" disabled={salvando}>{salvando ? 'Salvando...' : 'Salvar'}</Button>
        </form>
      </Modal>

      <Modal title="Editar profissional" open={modalEditar} onClose={() => setModalEditar(false)}>
        {edicao && (
          <form className="form-grid" onSubmit={salvarEdicao}>
            <Input label="Nome" helper="Digite apenas letras." maxLength={80} value={edicao.nome} onChange={(e) => setEdicao({ ...edicao, nome: e.target.value.replace(/[^\p{L}\s]/gu, '') })} required />
            <Input label="Especialidade" helper="Digite apenas letras." maxLength={80} value={edicao.especialidade} onChange={(e) => setEdicao({ ...edicao, especialidade: e.target.value.replace(/[^\p{L}\s]/gu, '') })} />
            <InternationalPhoneInput label="Telefone (opcional)" helper={edicao.telefone ? (validarTelefone(edicao.telefone) || ' Formato correto') : `Exemplo para o país selecionado: ${obterExemploTelefone('BR') || '+55 (65) 99336-0341'}`} value={edicao.telefone} onChangeValue={(valor) => setEdicao({ ...edicao, telefone: valor || '' })} />
            <DiasTrabalhoSelector value={edicao.diasTrabalho} onToggle={(dia) => alternarDia(setEdicao, dia)} />
            {erroEditar && <p className="form-error field-wide">{erroEditar}</p>}
            <Button type="submit" disabled={salvandoEditar}>{salvandoEditar ? 'Salvando...' : 'Salvar alterações'}</Button>
          </form>
        )}
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
    </section>
  )
}




