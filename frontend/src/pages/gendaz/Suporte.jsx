import { useContext, useEffect, useMemo, useState } from 'react'
import { AlertCircle, LifeBuoy, Loader, MessageCircle, Send, ShieldAlert, Ticket, X } from 'lucide-react'
import clienteApi from '../../api/clienteApi.js'
import StatusBadge from '../../components/StatusBadge.jsx'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'

const TIPOS_OCORRENCIA = [
  'Dúvida geral',
  'Problema de acesso',
  'Agendamento',
  'Reagendamento',
  'Cancelamento',
  'Serviços e preços',
  'BUGS SISTEMA',
  'Outros',
]

const PRIORIDADE_ALTA = 'ALTA'

function ehBugChamado(item) {
  return item?.prioridade === PRIORIDADE_ALTA
}

function extrairMensagemErro(error) {
  return error.response?.data?.mensagem
    || error.response?.data?.message
    || error.message
    || 'Não foi possível abrir o chamado.'
}

function formatarData(data) {
  if (!data) return '-----'
  try {
    return new Date(`${data}T12:00:00`).toLocaleDateString('pt-BR')
  } catch {
    return '-----'
  }
}

export default function Suporte() {
  const { cliente } = useContext(ClienteGendazContext)
  const [tipoOcorrencia, setTipoOcorrencia] = useState(TIPOS_OCORRENCIA[0])
  const [motivo, setMotivo] = useState('')
  const [mensagem, setMensagem] = useState('')
  const [chamados, setChamados] = useState([])
  const [carregando, setCarregando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState('')
  const [chamadoSelecionado, setChamadoSelecionado] = useState(null)
  const [filtroChamado, setFiltroChamado] = useState('TODOS')

  const totalBugs = useMemo(
    () => chamados.filter((item) => ehBugChamado(item)).length,
    [chamados],
  )

  const chamadosFiltrados = useMemo(() => {
    if (filtroChamado === 'BUGS') {
      return chamados.filter((item) => ehBugChamado(item))
    }
    return chamados
  }, [chamados, filtroChamado])

  const nomeEmpresa = useMemo(
    () => cliente?.empresaNome || cliente?.empresa?.nome || cliente?.empresa?.nomeFantasia || 'sua empresa',
    [cliente],
  )

  async function carregarChamados() {
    try {
      setCarregando(true)
      const data = await clienteApi.get('/meu-gendaz/suporte').then((response) => response.data)
      setChamados(Array.isArray(data) ? data : [])
    } catch {
      setChamados([])
    } finally {
      setCarregando(false)
    }
  }

  useEffect(() => {
    void carregarChamados()
  }, [])

  async function enviarChamado(event) {
    event.preventDefault()
    if (enviando) return
    setErro('')
    setSucesso('')

    const tipoLimpo = tipoOcorrencia.trim()
    const motivoLimpo = motivo.trim()
    const mensagemLimpa = mensagem.trim()

    if (!tipoLimpo || !motivoLimpo || !mensagemLimpa) {
      setErro('Preencha tipo de ocorrência, motivo e mensagem para enviar o chamado.')
      return
    }

    setEnviando(true)
    try {
      await clienteApi.post('/meu-gendaz/suporte', {
        tipoOcorrencia: tipoLimpo,
        motivo: motivoLimpo,
        mensagem: mensagemLimpa,
      })
      setTipoOcorrencia(TIPOS_OCORRENCIA[0])
      setMotivo('')
      setMensagem('')
      setSucesso('Chamado enviado com sucesso. Nossa equipe vai analisar seu caso.')
      await carregarChamados()
    } catch (error) {
      setErro(extrairMensagemErro(error))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Suporte</span>
        <h1>Fale com a {nomeEmpresa}</h1>
        <p>Abra um chamado com o tipo de ocorrência e o motivo para registrar tudo no painel administrativo.</p>
      </header>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-panel">
          <div className="gendaz-panel__head">
            <LifeBuoy size={18} />
            <h2>Novo chamado</h2>
          </div>

          <form className="gendaz-form" onSubmit={enviarChamado}>
            <label>
              <span>Tipo de ocorrência *</span>
              <select value={tipoOcorrencia} onChange={(e) => setTipoOcorrencia(e.target.value)} required>
                {TIPOS_OCORRENCIA.map((tipo) => (
                  <option key={tipo} value={tipo}>{tipo}</option>
                ))}
              </select>
            </label>

            <label>
              <span>Motivo *</span>
              <input
                type="text"
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                placeholder="Ex.: não consigo acessar a conta"
                maxLength={160}
                required
              />
            </label>

            <label>
              <span>Mensagem *</span>
              <textarea
                rows={6}
                value={mensagem}
                onChange={(e) => setMensagem(e.target.value)}
                placeholder="Explique o que aconteceu e o que você precisa..."
                maxLength={1200}
                required
              />
            </label>

            {erro && (
              <div className="gendaz-auth__error">
                <AlertCircle size={16} />
                <span>{erro}</span>
              </div>
            )}
            {sucesso && (
              <div className="gendaz-mensagem gendaz-mensagem--sucesso">
                {sucesso}
              </div>
            )}

            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={enviando}>
              {enviando ? <><Loader className="spin" size={16} /> Enviando...</> : <><Send size={16} /> Enviar chamado</>}
            </button>
          </form>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head">
            <ShieldAlert size={18} />
            <h2>Como o suporte funciona</h2>
          </div>

          <div className="gendaz-stack">
            <div className="gendaz-mini-card">
              <strong>1. Descreva o problema</strong>
              <span>Informe tipo de ocorrência, motivo e detalhes para agilizar o atendimento.</span>
            </div>
            <div className="gendaz-mini-card">
              <strong>2. Registro no admin</strong>
              <span>O chamado entra no painel administrativo como suporte do Meu Gendaz.</span>
            </div>
            <div className="gendaz-mini-card">
              <strong>3. Acompanhamento</strong>
              <span>Você acompanha o status do chamado por esta mesma tela.</span>
            </div>
          </div>

          <div className="gendaz-card" style={{ marginTop: 18 }}>
            <div className="gendaz-card__top">
              <div className="gendaz-card__icon-title">
                <MessageCircle size={18} />
                <span>Resumo</span>
              </div>
            </div>
            <p className="gendaz-vazio" style={{ marginBottom: 0 }}>
              O chamado fica vinculado à sua conta e à empresa correta.
            </p>
          </div>
        </article>
      </div>

      <article className="gendaz-panel" style={{ marginTop: 24 }}>
        <div className="gendaz-panel__head">
          <Ticket size={18} />
          <h2>Meus chamados</h2>
        </div>

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

        {carregando ? (
          <p className="gendaz-vazio">Carregando chamados...</p>
        ) : chamadosFiltrados.length > 0 ? (
          <div className="gendaz-stack">
            {chamadosFiltrados.map((item) => (
              <div
                key={item.id}
                className="gendaz-mini-card gendaz-mini-card--historico gendaz-mini-card--clickable"
                role="button"
                tabIndex={0}
                onClick={() => setChamadoSelecionado(item)}
                onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && setChamadoSelecionado(item)}
              >
                <div className="gendaz-mini-card__info">
                  <p className="gendaz-mini-card__servico">{item.assunto || 'Meu Gendaz'}</p>
                  <p className="gendaz-mini-card__profissional">
                    {item.mensagem}
                  </p>
                  <small>Aberto em {formatarData(item.dataCriacao)}</small>
                </div>
                <div style={{ display: 'grid', justifyItems: 'end', gap: 10 }}>
                  <StatusBadge status={item.status} />
                  {ehBugChamado(item) && (
                    <span className="status status-atencao" title="Bug de sistema com prioridade alta">
                      <span className="status-dot">●</span>
                      Prioridade alta
                    </span>
                  )}
                  {item.resposta && <small style={{ maxWidth: 260, textAlign: 'right' }}>{item.resposta}</small>}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="gendaz-vazio">
            {chamados.length === 0
              ? 'Nenhum chamado enviado ainda.'
              : 'Nenhum chamado encontrado para este filtro.'}
          </p>
        )}
      </article>

      {chamadoSelecionado && (
        <div className="gendaz-modal-overlay" onClick={() => setChamadoSelecionado(null)}>
          <div className="gendaz-modal" onClick={(e) => e.stopPropagation()}>
            <div className="gendaz-modal__head">
              <h2>Detalhes do chamado</h2>
              <button className="gendaz-btn gendaz-btn--ghost" type="button" onClick={() => setChamadoSelecionado(null)} aria-label="Fechar">
                <X size={18} />
              </button>
            </div>
            <div className="gendaz-modal__form gendaz-chamado-detalhe">
              <div className="gendaz-chamado-campo">
                <span>Assunto</span>
                <p>{chamadoSelecionado.assunto || 'Meu Gendaz'}</p>
              </div>
              <div className="gendaz-chamado-campo">
                <span>Situação</span>
                <p><StatusBadge status={chamadoSelecionado.status} /></p>
              </div>
              <div className="gendaz-chamado-campo">
                <span>Sua mensagem</span>
                <p>{chamadoSelecionado.mensagem}</p>
              </div>
              <div className="gendaz-chamado-campo">
                <span>Mensagem</span>
                <p>
                  {chamadoSelecionado.resposta && chamadoSelecionado.resposta.trim()
                    ? chamadoSelecionado.resposta
                    : 'Nenhuma resposta do administrador ainda.'}
                </p>
              </div>
              <div className="gendaz-modal__actions">
                <button className="gendaz-btn" type="button" onClick={() => setChamadoSelecionado(null)}>Fechar</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
