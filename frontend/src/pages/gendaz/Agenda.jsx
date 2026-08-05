import { useContext, useState, useEffect, useMemo } from 'react'
import { useLocation } from 'react-router-dom'
import { CalendarPlus, RotateCw, X, Loader, AlertTriangle } from 'lucide-react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import clienteApi from '../../api/clienteApi.js'

function NovoAgendamentoModal({ onFechar, onCriar }) {
  const { servicos, profissionais } = useContext(ClienteGendazContext)
  const location = useLocation()
  const profissionaisAtivos = profissionais.filter((profissional) => profissional.status === 'ATIVO')
  const [horarios, setHorarios] = useState([])
  const hoje = new Date()
  const dataHoje = hoje.toISOString().slice(0, 10)
  const [form, setForm] = useState({ servicoId: '', profissionalId: '', data: dataHoje, hora: '', observacoes: '', cupomCodigo: '' })
  const [cupons, setCupons] = useState([])
  const [carregandoHorarios, setCarregandoHorarios] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState('')

  useEffect(() => {
    if (!form.servicoId || !form.profissionalId || !form.data) {
      setHorarios([])
      return
    }
    const buscar = async () => {
      try {
        setCarregandoHorarios(true)
        const { data } = await clienteApi.get('/meu-gendaz/horarios-disponiveis', {
          params: { servicoId: form.servicoId, profissionalId: form.profissionalId, data: form.data },
        })
        setHorarios(Array.isArray(data) ? data : data?.horarios || [])
      } catch (err) {
        console.error('[Agenda] Erro ao buscar horários')
        setHorarios([])
      } finally {
        setCarregandoHorarios(false)
      }
    }
    buscar()
  }, [form.servicoId, form.profissionalId, form.data])

  useEffect(() => {
    const buscarCupons = async () => {
      try {
        const { data } = await clienteApi.get('/meu-gendaz/promocoes')
        setCupons(Array.isArray(data) ? data : [])
      } catch {
        setCupons([])
      }
    }
    buscarCupons()
  }, [])

  useEffect(() => {
    const cupomQuery = new URLSearchParams(location.search).get('cupom')
    if (cupomQuery) {
      setForm((prev) => ({ ...prev, cupomCodigo: cupomQuery }))
    }
  }, [location.search])

  const cuponsAplicaveis = useMemo(() => {
    if (!form.servicoId) return []
    return cupons.filter((cupom) => {
      if (!cupom?.valida || cupom?.jaUsou) return false
      if (cupom.aplicarTodosServicos) return true
      return Array.isArray(cupom.servicos) && cupom.servicos.some((servico) => String(servico.id) === String(form.servicoId))
    })
  }, [cupons, form.servicoId])

  useEffect(() => {
    if (!form.cupomCodigo) return
    if (cuponsAplicaveis.some((cupom) => cupom.codigo === form.cupomCodigo)) return
    setForm((prev) => ({ ...prev, cupomCodigo: '' }))
  }, [cuponsAplicaveis, form.cupomCodigo])

  async function handleSubmit(e) {
    e.preventDefault()
    setErro('')
    if (!form.servicoId || !form.profissionalId || !form.data || !form.hora) {
      setErro('Preencha todos os campos obrigatórios.')
      return
    }
    try {
      setSalvando(true)
      await onCriar(form)
      onFechar()
    } catch (err) {
      setErro(err.response?.data?.mensagem || err.message || 'Erro ao criar agendamento.')
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className="gendaz-modal-overlay" onClick={onFechar}>
      <div className="gendaz-modal" onClick={(e) => e.stopPropagation()}>
        <div className="gendaz-modal__head">
          <h2>Novo agendamento</h2>
          <button className="gendaz-btn gendaz-btn--ghost" onClick={onFechar}><X size={18} /></button>
        </div>
        {erro && <p className="gendaz-auth__error">{erro}</p>}
        <form className="gendaz-modal__form" onSubmit={handleSubmit}>
          <label>
            <span>Serviço *</span>
            <select value={form.servicoId} onChange={(e) => setForm({ ...form, servicoId: e.target.value, cupomCodigo: '' })} required>
              <option value="">Selecione um serviço</option>
              {servicos.map((s) => <option key={s.id} value={s.id}>{s.nome || s.titulo || `Serviço ${s.id}`}</option>)}
            </select>
            {servicos.length === 0 && <small className="gendaz-texto-aviso">Nenhum serviço disponível.</small>}
          </label>
          <label>
            <span>Profissional *</span>
            <select value={form.profissionalId} onChange={(e) => setForm({ ...form, profissionalId: e.target.value, cupomCodigo: '' })} required>
              <option value="">Selecione um profissional</option>
              {profissionaisAtivos.map((p) => <option key={p.id} value={p.id}>{p.nome || `Profissional ${p.id}`}</option>)}
            </select>
            {profissionaisAtivos.length === 0 && <small className="gendaz-texto-aviso">Nenhum profissional disponível.</small>}
          </label>
          <label>
            <span>Data *</span>
            <input type="date" value={form.data} onChange={(e) => setForm({ ...form, data: e.target.value })} required />
          </label>
          {carregandoHorarios && <div className="gendaz-loading"><Loader size={16} /> Buscando horários...</div>}
          {!carregandoHorarios && horarios.length > 0 && (
            <label>
              <span>Horário *</span>
              <select value={form.hora} onChange={(e) => setForm({ ...form, hora: e.target.value })} required>
                <option value="">Selecione um horário</option>
                {horarios.filter((h) => h.disponivel !== false).map((h) => (
                  <option key={h.horario || h} value={h.horario || h}>{h.horario || h}</option>
                ))}
              </select>
            </label>
          )}
          {!carregandoHorarios && form.servicoId && form.profissionalId && form.data && horarios.length === 0 && (
            <p className="gendaz-texto-aviso">Nenhum horário disponível para esta data.</p>
          )}
          <label>
            <span>Observações (opcional)</span>
            <textarea value={form.observacoes} onChange={(e) => setForm({ ...form, observacoes: e.target.value })} placeholder="Alguma observação..." />
          </label>
          {cuponsAplicaveis.length > 0 && (
            <label>
              <span>Cupom (opcional)</span>
              <select value={form.cupomCodigo} onChange={(e) => setForm({ ...form, cupomCodigo: e.target.value })}>
                <option value="">Sem cupom</option>
                {cuponsAplicaveis.map((cupom) => (
                  <option key={cupom.id} value={cupom.codigo}>{cupom.codigo} - {cupom.descricao}</option>
                ))}
              </select>
            </label>
          )}
          <div className="gendaz-modal__actions">
            <button type="button" className="gendaz-btn" onClick={onFechar}>Cancelar</button>
            <button type="submit" className="gendaz-btn gendaz-btn--primary" disabled={salvando}>
              {salvando ? <><Loader size={16} /> Salvando...</> : 'Confirmar agendamento'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function ReagendarModal({ agendamento, onFechar, onReagendar }) {
  const [novaData, setNovaData] = useState(agendamento.data || '')
  const [novaHora, setNovaHora] = useState(agendamento.horaInicio || agendamento.hora || '')
  const [horarios, setHorarios] = useState([])
  const [carregandoHorarios, setCarregandoHorarios] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState('')

  useEffect(() => {
    if (!novaData) { setHorarios([]); return }
    const buscar = async () => {
      try {
        setCarregandoHorarios(true)
        const { data } = await clienteApi.get('/meu-gendaz/horarios-disponiveis', {
          params: { servicoId: agendamento.servicoId, profissionalId: agendamento.profissionalId, data: novaData },
        })
        setHorarios(Array.isArray(data) ? data : data?.horarios || [])
      } catch { setHorarios([]) } finally {
        setCarregandoHorarios(false)
      }
    }
    buscar()
  }, [novaData, agendamento.servicoId, agendamento.profissionalId])

  async function handleSubmit(e) {
    e.preventDefault()
    setErro('')
    if (!novaData || !novaHora) { setErro('Selecione data e horário.'); return }
    try {
      setSalvando(true)
      await onReagendar(agendamento.id, { novaData, novaHora })
      onFechar()
    } catch (err) {
      setErro(err.response?.data?.mensagem || err.message || 'Erro ao reagendar.')
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className="gendaz-modal-overlay" onClick={onFechar}>
      <div className="gendaz-modal" onClick={(e) => e.stopPropagation()}>
        <div className="gendaz-modal__head">
          <h2>Reagendar agendamento</h2>
          <button className="gendaz-btn gendaz-btn--ghost" onClick={onFechar}><X size={18} /></button>
        </div>
        {erro && <p className="gendaz-auth__error">{erro}</p>}
        <form className="gendaz-modal__form" onSubmit={handleSubmit}>
          <label>
            <span>Nova data</span>
            <input type="date" value={novaData} onChange={(e) => setNovaData(e.target.value)} required />
          </label>
          {carregandoHorarios && <div className="gendaz-loading"><Loader size={16} /> Buscando horários...</div>}
          {!carregandoHorarios && horarios.length > 0 && (
            <label>
              <span>Novo horário</span>
              <select value={novaHora} onChange={(e) => setNovaHora(e.target.value)} required>
                <option value="">Selecione um horário</option>
                {horarios.filter((h) => h.disponivel !== false).map((h) => (
                  <option key={h.horario || h} value={h.horario || h}>{h.horario || h}</option>
                ))}
              </select>
            </label>
          )}
          <div className="gendaz-modal__actions">
            <button type="button" className="gendaz-btn" onClick={onFechar}>Cancelar</button>
            <button type="submit" className="gendaz-btn gendaz-btn--primary" disabled={salvando}>
              {salvando ? <><Loader size={16} /> Salvando...</> : 'Confirmar reagendamento'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function CancelarModal({ agendamento, onFechar, onCancelar }) {
  const [motivo, setMotivo] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    setErro('')
    try {
      setSalvando(true)
      await onCancelar(agendamento.id, motivo || 'Cancelamento pelo cliente')
      onFechar()
    } catch (err) {
      setErro(err.response?.data?.mensagem || err.message || 'Erro ao cancelar.')
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className="gendaz-modal-overlay" onClick={onFechar}>
      <div className="gendaz-modal" onClick={(e) => e.stopPropagation()}>
        <div className="gendaz-modal__head">
          <h2>Cancelar agendamento</h2>
          <button className="gendaz-btn gendaz-btn--ghost" onClick={onFechar}><X size={18} /></button>
        </div>
        {erro && <p className="gendaz-auth__error">{erro}</p>}
        <form className="gendaz-modal__form" onSubmit={handleSubmit}>
          <p>Tem certeza que deseja cancelar o agendamento de <strong>{agendamento.servicoNome || agendamento.servico || 'Serviço'}</strong>?</p>
          <label>
            <span>Motivo (opcional)</span>
            <textarea value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Informe o motivo do cancelamento..." />
          </label>
          <div className="gendaz-modal__actions">
            <button type="button" className="gendaz-btn" onClick={onFechar}>Voltar</button>
            <button type="submit" className="gendaz-btn gendaz-btn--danger" disabled={salvando}>
              {salvando ? <><Loader size={16} /> Cancelando...</> : <><AlertTriangle size={16} /> Confirmar cancelamento</>}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function Agenda() {
  const { agendamentos, criarAgendamento, reagendar, cancelarAgendamento, carregando, erro, profissionais } = useContext(ClienteGendazContext)
  const profissionaisAtivos = profissionais.filter((profissional) => profissional.status === 'ATIVO')
  const [showNovo, setShowNovo] = useState(false)
  const [modalReagendar, setModalReagendar] = useState(null)
  const [modalCancelar, setModalCancelar] = useState(null)

  if (carregando) {
    return (
      <section className="gendaz-page">
        <div className="gendaz-loading"><Loader size={20} /> Carregando agenda...</div>
      </section>
    )
  }

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Agenda</span>
        <h1>Próximos agendamentos</h1>
        <p>Aqui ficam apenas os compromissos futuros. Histórico não aparece nesta aba.</p>
      </header>

      {erro && <div className="gendaz-erro">{erro}</div>}

      <div className="gendaz-actions">
        <button className="gendaz-btn gendaz-btn--primary" type="button" onClick={() => setShowNovo(true)}>
          <CalendarPlus size={16} /> Novo agendamento
        </button>
      </div>

      <div className="gendaz-table">
        {agendamentos && agendamentos.length > 0 ? (
          agendamentos.map((item) => (
            <article key={item.id} className="gendaz-card gendaz-card--agendamento">
              <div className="gendaz-agenda-grid">
                <div className="gendaz-agenda-field">
                  <span>Serviço</span>
                  <strong>{item.servicoNome || item.servico || item.servico?.nome || 'Serviço'}</strong>
                </div>
                <div className="gendaz-agenda-field">
                  <span>Profissional</span>
                  <strong>{item.profissionalNome || item.profissional || item.profissional?.nome || 'Profissional'}</strong>
                </div>
                <div className="gendaz-agenda-field">
                  <span>Data</span>
                  <strong>{item.data ? new Date(`${item.data}T12:00:00`).toLocaleDateString('pt-BR') : 'Data não definida'}</strong>
                </div>
                <div className="gendaz-agenda-field">
                  <span>Horário</span>
                  <strong>{item.horaInicio || item.hora || '—'}</strong>
                </div>
                <div className="gendaz-agenda-field gendaz-agenda-field--status">
                  <span>Status</span>
                  <strong className={`gendaz-status gendaz-status--${(item.status || '').toLowerCase()}`}>{item.status || 'Pendente'}</strong>
                </div>
              </div>

              <div className="gendaz-card__actions gendaz-agenda-actions">
                <button className="gendaz-btn" type="button" onClick={() => setModalReagendar(item)}>
                  <RotateCw size={16} /> Reagendar
                </button>
                <button className="gendaz-btn gendaz-btn--ghost" type="button" onClick={() => setModalCancelar(item)}>
                  <X size={16} /> Cancelar
                </button>
              </div>
            </article>
          ))
        ) : (
          <p className="gendaz-vazio">Você não possui agendamentos próximos.</p>
        )}
      </div>

      {showNovo && <NovoAgendamentoModal onFechar={() => setShowNovo(false)} onCriar={criarAgendamento} />}
      {modalReagendar && <ReagendarModal agendamento={modalReagendar} onFechar={() => setModalReagendar(null)} onReagendar={reagendar} />}
      {modalCancelar && <CancelarModal agendamento={modalCancelar} onFechar={() => setModalCancelar(null)} onCancelar={cancelarAgendamento} />}
    </section>
  )
}
