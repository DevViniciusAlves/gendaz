import { Headphones, LifeBuoy, MessageCircle, Send } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import Modal from '../components/Modal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'

const PRIORIDADE_LABEL = {
  BAIXA: 'Baixa',
  MEDIA: 'Media',
  ALTA: 'Alta',
}

const PRIORIDADE_POR_ASSUNTO = {
  'Dúvidas': 'BAIXA',
  'Pagamentos': 'ALTA',
  'Alteração em conta': 'MEDIA',
  'BUGS SISTEMA': 'ALTA',
}

const ASSUNTOS_CHAMADO = [
  'Dúvidas',
  'Pagamentos',
  'Alteração em conta',
  'BUGS SISTEMA',
]

const ASSUNTO_BUG = 'BUGS SISTEMA'

function mensagemErro(error) {
  return error.response?.data?.mensagem
    || Object.values(error.response?.data?.campos || {})[0]
    || error.response?.data?.message
    || 'Não foi possível enviar o chamado.'
}

export default function Suporte() {
  const { usuario } = useAuth()
  const [assunto, setAssunto] = useState('')
  const [prioridade, setPrioridade] = useState('MEDIA')
  const [mensagem, setMensagem] = useState('')
  const [chamados, setChamados] = useState([])
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [chamadoSelecionado, setChamadoSelecionado] = useState(null)
  const [filtroChamado, setFiltroChamado] = useState('TODOS')

  const totalBugs = useMemo(
    () => chamados.filter((item) => item.assunto === ASSUNTO_BUG).length,
    [chamados],
  )

  const chamadosFiltrados = useMemo(() => {
    if (filtroChamado === 'BUGS') {
      return chamados.filter((item) => item.assunto === ASSUNTO_BUG)
    }
    return chamados
  }, [chamados, filtroChamado])

  useEffect(() => {
    if (!usuario?.empresaId) return
    appApi.listarChamadosEmpresa(usuario.empresaId).then(setChamados).catch(() => setChamados([]))
  }, [usuario?.empresaId])

  function atualizarAssunto(valor) {
    setAssunto(valor)
    setPrioridade(PRIORIDADE_POR_ASSUNTO[valor] || 'MEDIA')
  }

  async function enviarChamado(event) {
    event.preventDefault()
    if (enviando) return
    setErro('')
    setSucesso('')

    const assuntoLimpo = assunto.trim()
    const mensagemLimpa = mensagem.trim()
    if (!assuntoLimpo || !mensagemLimpa || !prioridade) {
      setErro('Preencha assunto, prioridade e mensagem para enviar o chamado.')
      return
    }
    if (assuntoLimpo.length > 100 || mensagemLimpa.length > 500) {
      setErro('Revise os limites de caracteres antes de enviar o chamado.')
      return
    }

    setEnviando(true)
    try {
      await appApi.criarChamado({ assunto: assuntoLimpo, prioridade, mensagem: mensagemLimpa })
      setAssunto('')
      setPrioridade('MEDIA')
      setMensagem('')
      setSucesso('Chamado enviado com sucesso.')
      if (usuario?.empresaId) {
        const atualizados = await appApi.listarChamadosEmpresa(usuario.empresaId)
        setChamados(atualizados)
      }
    } catch (error) {
      setErro(mensagemErro(error))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Atendimento</span>
        <h1>Suporte</h1>
        <p>Central de ajuda da conta, com prioridade conforme o plano contratado.</p>
      </div>
      <div className="support-grid">
        <article className="panel support-card">
          <LifeBuoy size={26} />
          <h2>Base de ajuda</h2>
          <p>Guias rápidos para usar agenda, financeiro e configurações.</p>
        </article>
        <article className="panel support-card">
          <MessageCircle size={26} />
          <h2>Chamado pelo painel</h2>
          <p>Abra uma solicitação para dúvidas operacionais ou problemas de acesso.</p>
        </article>
        <article className="panel support-card">
          <Headphones size={26} />
          <h2>Acompanhamento da conta</h2>
          <p>Atendimento próximo para dúvidas de uso, configuração e organização da rotina no painel.</p>
        </article>
      </div>
      <section className="panel support-form">
        <h2>Novo chamado</h2>
        <form className="support-form-grid" onSubmit={enviarChamado}>
          <label className="field">
            <span>Assunto</span>
            <select value={assunto} onChange={(e) => atualizarAssunto(e.target.value)} required>
              <option value="">Selecione o assunto</option>
              {ASSUNTOS_CHAMADO.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
            <small className={assunto.length >= 100 ? 'field-hint limit-reached' : 'field-hint'}>
              {assunto.length >= 100 ? 'Limite de caracteres atingido.' : 'Escolha o motivo principal do chamado.'}
              <strong>{assunto.length}/100</strong>
            </small>
          </label>
          <label className="field">
            <span>Prioridade</span>
            <select value={prioridade} disabled required>
              <option value="BAIXA">Baixa</option>
              <option value="MEDIA">Media</option>
              <option value="ALTA">Alta</option>
            </select>
            <small className="field-hint">Definida automaticamente conforme o assunto.</small>
          </label>
          <label className="field field-wide">
            <span>Mensagem</span>
            <textarea className="support-message" maxLength={500} value={mensagem} onChange={(e) => setMensagem(e.target.value)} placeholder="Descreva o que aconteceu" required />
            <small className={mensagem.length >= 500 ? 'field-hint limit-reached' : 'field-hint'}>
              {mensagem.length >= 500 ? 'Limite de caracteres atingido.' : 'Descreva em até 500 caracteres.'}
              <strong>{mensagem.length}/500</strong>
            </small>
          </label>
          {erro && <p className="form-error field-wide">{erro}</p>}
          {sucesso && <p className="success-text field-wide">{sucesso}</p>}
          <div className="support-form-actions">
            <Button icon={Send} type="submit" loading={enviando} loadingText="Enviando...">Enviar chamado</Button>
          </div>
        </form>
      </section>

      <section className="panel support-form">
        <h2>Chamados abertos</h2>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
          {[
            { key: 'TODOS', label: `Todos (${chamados.length})` },
            { key: 'BUGS', label: `Bugs (${totalBugs})` },
          ].map((item) => (
            <button
              key={item.key}
              type="button"
              className={`filter-chip ${filtroChamado === item.key ? 'active' : ''}`}
              onClick={() => setFiltroChamado(item.key)}
            >
              {item.label}
            </button>
          ))}
        </div>
        <div className="support-ticket-list">
          {chamadosFiltrados.length === 0 ? (
            <p>{chamados.length === 0 ? 'Nenhum chamado aberto.' : 'Nenhum chamado encontrado para este filtro.'}</p>
          ) : chamadosFiltrados.map((item) => (
            <article
              key={item.id}
              className="support-ticket-item support-ticket-clickable"
              role="button"
              tabIndex={0}
              onClick={() => setChamadoSelecionado(item)}
              onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && setChamadoSelecionado(item)}
            >
              <div>
                <strong>{item.assunto || 'Não informado'}</strong>
                <p>{item.mensagem}</p>
                <span className="support-ticket-meta">Prioridade: {PRIORIDADE_LABEL[item.prioridade] || 'Media'}</span>
                {item.assunto === ASSUNTO_BUG && (
                  <span className="status status-atenção" style={{ marginLeft: 8 }} title="Bug de sistema com prioridade alta">
                    <span className="status-dot">●</span>
                    Prioridade alta
                  </span>
                )}
              </div>
              <StatusBadge status={item.status} />
            </article>
          ))}

          <Modal
            title="Detalhes do chamado"
            open={Boolean(chamadoSelecionado)}
            onClose={() => setChamadoSelecionado(null)}
          >
            {chamadoSelecionado && (
              <div className="support-ticket-detail">
                <div className="field">
                  <span>Assunto</span>
                  <p className="support-detail-value">{chamadoSelecionado.assunto || 'Não informado'}</p>
                </div>
                <div className="field">
                  <span>Situação</span>
                  <p className="support-detail-value"><StatusBadge status={chamadoSelecionado.status} /></p>
                </div>
                <div className="field">
                  <span>Sua mensagem</span>
                  <p className="support-detail-value">{chamadoSelecionado.mensagem}</p>
                </div>
                <div className="field">
                  <span>Mensagem</span>
                  <p className="support-detail-value">
                    {chamadoSelecionado.resposta && chamadoSelecionado.resposta.trim()
                      ? chamadoSelecionado.resposta
                      : 'Nenhuma resposta do administrador ainda.'}
                  </p>
                </div>
                <div className="modal-actions">
                  <Button variant="secondary" onClick={() => setChamadoSelecionado(null)}>Fechar</Button>
                </div>
              </div>
            )}
          </Modal>
        </div>
      </section>
    </section>
  )
}
