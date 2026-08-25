import { CalendarDays, CheckCircle, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import InternationalPhoneInput from '../components/InternationalPhoneInput.jsx'
import { normalizarParaApi, obterExemploTelefone, validarTelefone } from '../utils/phoneUtils.js'

function dataLocalISO() {
  const agora = new Date()
  const ano = agora.getFullYear()
  const mes = String(agora.getMonth() + 1).padStart(2, '0')
  const dia = String(agora.getDate()).padStart(2, '0')
  return `${ano}-${mes}-${dia}`
}

const hoje = dataLocalISO()

function moeda(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function somenteLetras(valor) {
  return String(valor || '').replace(/[^\p{L}\s]/gu, '').replace(/\s{2,}/g, ' ')
}

function diaSemanaParaIndice(diaSemana) {
  const mapa = {
    DOMINGO: 0,
    SEGUNDA: 1,
    TERCA: 2,
    QUARTA: 3,
    QUINTA: 4,
    SEXTA: 5,
    SABADO: 6,
  }
  return mapa[diaSemana] ?? null
}

function diaSemanaIso(data) {
  if (!data) return null
  const [ano, mes, dia] = String(data).split('-').map(Number)
  const local = ano && mes && dia ? new Date(ano, mes - 1, dia, 12) : null
  const dias = ['DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO']
  return local ? dias[local.getDay()] : null
}

function trabalhaNaData(profissional, data) {
  const dia = diaSemanaIso(data)
  return !dia || (Array.isArray(profissional?.diasTrabalho) && profissional.diasTrabalho.includes(dia))
}

function formatarHorarioAtendimento(item) {
  if (!item) return null

  if (!item.ativo) {
    return `${item.diaLabel}: indisponível`
  }

  const intervalo = item.intervaloInicio && item.intervaloFim
    ? ` / intervalo ${item.intervaloInicio.slice(0, 5)}-${item.intervaloFim.slice(0, 5)}`
    : ''

  const expediente = `${item.horaInicio?.slice(0, 5)}-${item.horaFim?.slice(0, 5)}`
  return `${item.diaLabel}: ${expediente}${intervalo}`
}

export default function Booking() {
  const { slugOuEmpresaId } = useParams()
  const [booking, setBooking] = useState(null)
  const [servicoId, setServicoId] = useState('')
  const [profissionalId, setProfissionalId] = useState('')
  const [data, setData] = useState(hoje)
  const [horaInicio, setHoraInicio] = useState('')
  const [busca, setBusca] = useState('')
  const [horarios, setHorarios] = useState([])
  const [cliente, setCliente] = useState({ nome: '', telefone: '', email: '', observacao: '' })
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState('')
  const [loading, setLoading] = useState(true)
  const [salvando, setSalvando] = useState(false)

  const profissionais = booking?.profissionais || []
  const profissionaisAtivos = profissionais.filter((profissional) => profissional.status === 'ATIVO')
  const profissionaisDisponiveis = profissionaisAtivos.filter((profissional) => trabalhaNaData(profissional, data))
  const exigeProfissional = profissionaisAtivos.length > 0
  const servicoSelecionado = useMemo(
    () => (booking?.servicos || []).find((servico) => String(servico.id) === String(servicoId)) || null,
    [booking?.servicos, servicoId],
  )
  const profissionalSelecionado = useMemo(
    () => profissionais.find((profissional) => String(profissional.id) === String(profissionalId)) || null,
    [profissionais, profissionalId],
  )

  useEffect(() => {
    async function carregar() {
      setLoading(true)
      setErro('')
      try {
        const response = await appApi.carregarBooking(slugOuEmpresaId)
        setBooking(response)
        setServicoId(response.servicos?.[0]?.id ? String(response.servicos[0].id) : '')
        const profissionalInicial = (response.profissionais || []).find((profissional) => profissional.status === 'ATIVO' && trabalhaNaData(profissional, hoje))
        setProfissionalId(profissionalInicial?.id ? String(profissionalInicial.id) : '')
      } catch (error) {
        setErro(error.response?.data?.mensagem || 'Agendamento indisponível no momento.')
      } finally {
        setLoading(false)
      }
    }

    carregar()
  }, [slugOuEmpresaId])

  useEffect(() => {
    if (!exigeProfissional || !profissionalId) return
    if (!profissionaisDisponiveis.some((profissional) => String(profissional.id) === String(profissionalId))) {
      setProfissionalId(profissionaisDisponiveis[0]?.id ? String(profissionaisDisponiveis[0].id) : '')
      setHoraInicio('')
    }
  }, [data, exigeProfissional, profissionalId, profissionaisDisponiveis])

  useEffect(() => {
    async function carregarHorarios() {
      if (!servicoId || !data || (exigeProfissional && !profissionalId)) {
        setHorarios([])
        setHoraInicio('')
        return
      }

      try {
        const profissionalSelecionado = profissionalId ? Number(profissionalId) : null
        const response = await appApi.listarHorariosBooking(slugOuEmpresaId, profissionalSelecionado, servicoId, data)
        setHorarios(response)
        setHoraInicio((atual) => (response.includes(atual) ? atual : ''))
      } catch {
        setHorarios([])
        setHoraInicio('')
      }
    }

    carregarHorarios()
  }, [slugOuEmpresaId, servicoId, profissionalId, data, exigeProfissional])

  const servicosFiltrados = useMemo(() => {
    const termo = busca.trim().toLowerCase()
    return (booking?.servicos || []).filter((servico) => (
      !termo
      || servico.nome.toLowerCase().includes(termo)
      || String(servico.descricao || '').toLowerCase().includes(termo)
    ))
  }, [booking?.servicos, busca])

  const horariosAtendimento = booking?.horariosAtendimento || []
  const diaAtual = new Date(`${data}T12:00:00`)
  const horarioDoDia = horariosAtendimento.find((item) => diaSemanaParaIndice(item.diaSemana) === diaAtual.getDay())
  const semServicos = (booking?.servicos || []).length === 0

  async function atualizarHorariosDepoisDoAgendamento() {
    if (!servicoId || !data) return
    const profissionalSelecionado = profissionalId ? Number(profissionalId) : null
    const response = await appApi.listarHorariosBooking(slugOuEmpresaId, profissionalSelecionado, servicoId, data)
    setHorarios(response)
  }

  function iniciarNovoAgendamento() {
    setErro('')
    setSucesso('')
    setServicoId(booking?.servicos?.[0]?.id ? String(booking.servicos[0].id) : '')
    const profissionalInicial = (booking?.profissionais || []).find((profissional) => profissional.status === 'ATIVO' && trabalhaNaData(profissional, hoje))
    setProfissionalId(profissionalInicial?.id ? String(profissionalInicial.id) : '')
    setData(hoje)
    setHoraInicio('')
    setBusca('')
    setCliente({ nome: '', telefone: '', email: '', observacao: '' })
    setHorarios([])
  }

  async function confirmar(event) {
    event.preventDefault()
    if (salvando) return
    setErro('')
    setSucesso('')

    if (!servicoId || !data || !horaInicio) {
      setErro('Escolha serviço, data e horário.')
      return
    }
    if (exigeProfissional && !profissionalId) {
      setErro(profissionaisDisponiveis.length === 0 ? 'Nenhum profissional disponível nesta data. Escolha outro dia.' : 'Escolha um profissional para continuar.')
      return
    }

    const telValidationError = validarTelefone(cliente.telefone)
    if (cliente.nome.trim().length < 2 || telValidationError) {
      setErro(telValidationError || 'Informe um nome válido.')
      return
    }
    const telefone = normalizarParaApi(cliente.telefone)
    if (!telefone) {
      setErro('Telefone inválido. Confira o formato do país selecionado.')
      return
    }

    setSalvando(true)
    try {
      const response = await appApi.criarAgendamentoPublico(slugOuEmpresaId, {
        servicoId: Number(servicoId),
        profissionalId: profissionalId ? Number(profissionalId) : null,
        data,
        horaInicio,
        clienteNome: cliente.nome.trim(),
        clienteTelefone: telefone,
        clienteEmail: cliente.email.trim() || null,
        observacao: cliente.observacao.trim() || null,
      })

      const protocolo = response?.agendamento?.protocolo
      setSucesso(
        protocolo
          ? `${response.mensagem || 'Agendamento confirmado com sucesso.'}\n\nProtocolo: ${protocolo}`
          : (response.mensagem || 'Agendamento confirmado com sucesso.')
      )
      await atualizarHorariosDepoisDoAgendamento()
    } catch (error) {
      setErro(
        error.response?.data?.mensagem
        || Object.values(error.response?.data?.campos || {})[0]
        || 'Não foi possível criar o agendamento.',
      )
    } finally {
      setSalvando(false)
    }
  }

  if (loading) {
    return (
      <main className="booking-page">
        <section className="booking-card">
          <p>Carregando agendamento...</p>
        </section>
      </main>
    )
  }

  if (erro && !booking) {
    return (
      <main className="booking-page">
        <section className="booking-card">
          <h1>Agendamento indisponível</h1>
          <p>{erro}</p>
        </section>
      </main>
    )
  }

  if (!booking?.disponivel) {
    return (
      <main className="booking-page">
        <section className="booking-card">
          <h1>{booking?.nomeFantasia || 'Gendaz'}</h1>
          <p>{booking?.mensagem || 'Agendamento indisponível no momento.'}</p>
        </section>
      </main>
    )
  }

  return (
    <main className="booking-page">
      <section className="booking-card booking-card-wide">
        <div className="booking-head">
          <span className="booking-icon"><CalendarDays size={24} /></span>
          <div>
            <span className="section-kicker">Agendamento online</span>
            <h1>{booking.nomeFantasia}</h1>
            <p>Escolha serviço, profissional e horário para solicitar seu agendamento.</p>

            <div className="booking-flow-steps" aria-label="Etapas do agendamento">
              <span>Serviço</span>
              <span>Profissional</span>
              <span>Data</span>
              <span>Horário</span>
              <span>Dados</span>
            </div>

            {horariosAtendimento.length > 0 && (
              <div className="booking-hours-summary">
                {horariosAtendimento.map((item) => (
                  <span
                    key={item.diaSemana}
                    className={item.ativo ? 'booking-hour-chip' : 'booking-hour-chip inactive'}
                  >
                    {formatarHorarioAtendimento(item)}
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>

        {sucesso ? (
          <div className="booking-success">
            <div className="booking-success-card">
              <span className="booking-success-icon">
                <CheckCircle size={28} />
              </span>
              <div className="booking-success-copy">
                <span className="section-kicker">Agendamento confirmado</span>
                <h2>Seu horário foi reservado com sucesso.</h2>
                <p>{sucesso}</p>
              </div>
            </div>

            <div className="booking-success-summary">
              <div>
                <span>Serviço</span>
                <strong>{servicoSelecionado?.nome || 'Não informado'}</strong>
              </div>
              <div>
                <span>Data</span>
                <strong>{data ? new Date(`${data}T12:00:00`).toLocaleDateString('pt-BR') : '-'}</strong>
              </div>
              <div>
                <span>Horário</span>
                <strong>{horaInicio ? horaInicio.slice(0, 5) : '-'}</strong>
              </div>
              <div>
                <span>Profissional</span>
                <strong>{profissionalSelecionado?.nome || 'Sem preferência'}</strong>
              </div>
            </div>

            <div className="booking-actions booking-actions-success">
              <Button type="button" onClick={iniciarNovoAgendamento} className="booking-reset-btn">
                Fazer novo agendamento
              </Button>
            </div>
          </div>
        ) : (
          <form className="booking-form" onSubmit={confirmar}>
            <div className="field field-wide booking-service-picker">
              <span>Buscar e escolher serviço</span>
              <div className="booking-search">
                <Search size={17} />
                <input
                  value={busca}
                  onChange={(event) => setBusca(event.target.value)}
                  placeholder="Buscar por nome ou descrição"
                  maxLength={80}
                />
              </div>

              <div className="booking-select-shell">
                <select
                  value={servicoId}
                  onChange={(event) => {
                    setServicoId(event.target.value)
                    setHoraInicio('')
                  }}
                  required
                >
                  <option value="">Selecione um serviço</option>
                  {servicosFiltrados.map((servico) => (
                    <option key={servico.id} value={servico.id}>
                      {servico.nome} • {servico.duracaoMinutos} min • {moeda(servico.valor)}
                    </option>
                  ))}
                </select>
              </div>

              <div className="booking-service-compact-list">
                {semServicos ? (
                  <p>Nenhum serviço disponível no momento.</p>
                ) : servicosFiltrados.length === 0 ? (
                  <p>Nenhum serviço encontrado.</p>
                ) : (
                  servicosFiltrados.map((servico) => (
                    <button
                      key={servico.id}
                      className={String(servico.id) === String(servicoId) ? 'booking-service active' : 'booking-service'}
                      type="button"
                      onClick={() => {
                        setServicoId(String(servico.id))
                        setHoraInicio('')
                      }}
                    >
                      <strong>{servico.nome}</strong>
                      <span>{servico.descricao || 'Serviço disponível para agendamento.'}</span>
                      <small>{servico.duracaoMinutos} min · {moeda(servico.valor)}</small>
                    </button>
                  ))
                )}
              </div>

              {servicoSelecionado && (
                <div className="booking-service-preview">
                  <div>
                    <span>{servicoSelecionado.nome}</span>
                    <strong>{servicoSelecionado.duracaoMinutos} min · {moeda(servicoSelecionado.valor)}</strong>
                  </div>
                  <p>{servicoSelecionado.descricao || 'Serviço disponível para agendamento.'}</p>
                </div>
              )}
            </div>

            <div className="booking-schedule-grid field-wide">
              {exigeProfissional ? (
                <label className="field">
                  <span>Profissional</span>
                  <select
                    value={profissionalId}
                    onChange={(event) => {
                      setProfissionalId(event.target.value)
                      setHoraInicio('')
                    }}
                    required
                  >
                    <option value="">Selecione</option>
                    {profissionaisDisponiveis.map((profissional) => (
                      <option key={profissional.id} value={profissional.id}>
                        {profissional.nome}
                      </option>
                    ))}
                  </select>
                  {profissionaisDisponiveis.length === 0 && <small className="field-hint limit-reached">Nenhum profissional disponível nesta data. Escolha outro dia.</small>}
                </label>
              ) : (
                <div className="booking-note">
                  <strong>Sem preferência</strong>
                  <p>Sem profissionais cadastrados. O sistema libera o atendimento padrão da empresa.</p>
                </div>
              )}

              <label className="field">
                <span>Data</span>
                <input type="date" min={hoje} value={data} onChange={(event) => setData(event.target.value)} required />
              </label>

              <div className="field booking-time-panel">
                <span>Horário</span>
                <div className="booking-time-grid">
                  {horarios.length === 0 ? (
                    <p>{horarioDoDia?.ativo === false ? 'Nenhum horário disponível para este dia.' : 'Nenhum horário disponível para esta data.'}</p>
                  ) : (
                    horarios.map((horario) => (
                      <button
                        key={horario}
                        type="button"
                        className={horaInicio === horario ? 'booking-time active' : 'booking-time'}
                        onClick={() => setHoraInicio(horario)}
                      >
                        {horario.slice(0, 5)}
                      </button>
                    ))
                  )}
                </div>
              </div>
            </div>

            <div className="booking-contact-grid field-wide">
              <label className="field">
                <span>Nome</span>
                <input
                  value={cliente.nome}
                  onChange={(event) => setCliente({ ...cliente, nome: somenteLetras(event.target.value) })}
                  maxLength={80}
                  required
                />
              </label>

              <InternationalPhoneInput
                label="Telefone"
                value={cliente.telefone}
                onChangeValue={(valor) => setCliente({ ...cliente, telefone: valor || '' })}
                defaultCountry="BR"
                required
                helper={cliente.telefone ? (validarTelefone(cliente.telefone) || ' Pronto para confirmar') : `Exemplo para o país selecionado: ${obterExemploTelefone('BR') || '+55 (65) 99336-0341'}`}
              />
            </div>

            <label className="field field-wide">
              <span>E-mail</span>
              <input
                type="email"
                value={cliente.email}
                onChange={(event) => setCliente({ ...cliente, email: event.target.value })}
                maxLength={120}
                placeholder="Opcional"
              />
            </label>

            <label className="field field-wide">
              <span>Observação</span>
              <textarea
                value={cliente.observacao}
                onChange={(event) => setCliente({ ...cliente, observacao: event.target.value })}
                maxLength={500}
                placeholder="Opcional"
              />
              <small className={cliente.observacao.length >= 500 ? 'field-hint limit-reached' : 'field-hint'}>
                {cliente.observacao.length >= 500 ? 'Limite de caracteres atingido.' : 'Opcional para recados ao profissional.'}
                <strong>{cliente.observacao.length}/500</strong>
              </small>
            </label>

            {erro && <p className="form-error field-wide">{erro}</p>}
            <div className="booking-actions field-wide">
              <Button type="submit" loading={salvando} loadingText="Confirmando..." disabled={semServicos || !cliente.nome || (validarTelefone(cliente.telefone) !== '')}>
                Confirmar agendamento
              </Button>
            </div>
          </form>
        )}
      </section>
    </main>
  )
}
