import { useContext, useEffect, useMemo, useRef, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, Bot, Calendar, Loader, Send, Sparkles } from 'lucide-react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import clienteApi from '../../api/clienteApi.js'

const ATALHOS = [
  { label: 'Quero agendar', prompt: 'agendar' },
  { label: 'Ver serviços', prompt: 'servicos' },
  { label: 'Ver promoções', prompt: 'promocoes' },
  { label: 'Cancelar', prompt: 'cancelar' },
  { label: 'Reagendar', prompt: 'reagendar' },
]

const ETAPAS = {
  AGENDAR: 'agendar',
  REAGENDAR: 'reagendar',
  CANCELAR: 'cancelar',
}

function normalizarTexto(texto) {
  return String(texto || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
}

function detectarIntencao(texto) {
  const t = normalizarTexto(texto)
  if (/cancelar|desmarcar|remover/.test(t)) return ETAPAS.CANCELAR
  if (/reagendar|remarcar|trocar.*horario|trocar.*data/.test(t)) return ETAPAS.REAGENDAR
  if (/agendar|marcar|reservar|quero.*atendimento|quero.*horario/.test(t)) return ETAPAS.AGENDAR
  return null
}

function pad2(valor) {
  return String(valor).padStart(2, '0')
}

function formatarDataISO(data) {
  if (!(data instanceof Date) || Number.isNaN(data.getTime())) return null
  const ano = data.getFullYear()
  const mes = pad2(data.getMonth() + 1)
  const dia = pad2(data.getDate())
  return `${ano}-${mes}-${dia}`
}

function interpretarDataLivre(texto) {
  const t = normalizarTexto(texto).trim()
  if (!t) return null
  if (/^\d{4}-\d{2}-\d{2}$/.test(t)) return t
  const hoje = new Date()
  const base = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate())
  if (/^hoje$/.test(t)) return formatarDataISO(base)
  if (/^amanha$/.test(t)) return formatarDataISO(new Date(base.getTime() + 86400000))
  if (/^depois de amanha$/.test(t)) return formatarDataISO(new Date(base.getTime() + 2 * 86400000))
  const matchDia = t.match(/^(\d{1,2})(?:\/(\d{1,2})(?:\/(\d{2,4}))?)?$/)
  if (matchDia) {
    const dia = Number(matchDia[1])
    const mes = Number(matchDia[2] || (base.getMonth() + 1))
    let ano = Number(matchDia[3] || base.getFullYear())
    if (ano < 100) ano += 2000
    if (!dia || !mes || !ano) return null
    const data = new Date(ano, mes - 1, dia)
    return formatarDataISO(data)
  }
  return null
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

function interpretarHoraLivre(texto) {
  const t = normalizarTexto(texto).trim()
  if (!t) return null
  const matchHora = t.match(/^(\d{1,2})(?::(\d{2}))?\s*(h|hs|horas?)?$/)
  if (!matchHora) return null
  const hora = Number(matchHora[1])
  const minuto = Number(matchHora[2] || 0)
  if (Number.isNaN(hora) || Number.isNaN(minuto)) return null
  if (hora < 0 || hora > 23 || minuto < 0 || minuto > 59) return null
  return `${pad2(hora)}:${pad2(minuto)}`
}

function normalizarHora(hora) {
  return interpretarHoraLivre(hora) || normalizarTexto(hora)
}

function formatarServicos(servicos) {
  return (Array.isArray(servicos) ? servicos : [])
    .map((servico, index) => `${index + 1}. ${servico.nome || servico.titulo || `Serviço ${servico.id}`}`)
    .join('\n')
}

function formatarAgendamentos(agendamentos) {
  return (Array.isArray(agendamentos) ? agendamentos : []).map((agendamento, index) => {
    const servico = agendamento.servicoNome || agendamento.servico?.nome || 'Serviço'
    const data = agendamento.data || agendamento.dataAgendada || ''
    const hora = agendamento.horaInicio || agendamento.hora || ''
    return `${index + 1}. #${agendamento.id} - ${servico} - ${data} ${hora}`
  }).join('\n')
}

function formatarDataLegivel(dataISO) {
  if (!dataISO) return ''
  const data = new Date(`${dataISO}T00:00:00`)
  if (Number.isNaN(data.getTime())) return dataISO
  return new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(data)
}

function criarMensagemSistema() {
  return {
    id: `sys-${Date.now()}`,
    origem: 'ia',
    texto: 'Posso te ajudar a concluir tudo por aqui. Me diga o que você quer fazer.',
    sugestoes: ['Quero agendar', 'Reagendar', 'Cancelar'],
  }
}

export default function AssistenteIA() {
  const {
    cliente,
    servicos,
    profissionais,
    agendamentos,
    criarAgendamento,
    reagendar,
    cancelarAgendamento,
    buscarHorarios,
  } = useContext(ClienteGendazContext)

  const [mensagens, setMensagens] = useState([criarMensagemSistema()])
  const [inputValue, setInputValue] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [fluxo, setFluxo] = useState(null)
  const [dadosFluxo, setDadosFluxo] = useState({})
  const [horarios, setHorarios] = useState([])
  const messagesEndRef = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [mensagens])

  const servicosAtivos = useMemo(() => (Array.isArray(servicos) ? servicos : []), [servicos])
  const profissionaisAtivos = useMemo(() => (Array.isArray(profissionais) ? profissionais.filter((item) => item?.status !== 'INATIVO') : []), [profissionais])
  const agendamentosAtivos = useMemo(() => (Array.isArray(agendamentos) ? agendamentos.filter((item) => item?.status !== 'CANCELADO') : []), [agendamentos])

  const adicionarMensagem = useCallback((origem, texto, extras = {}) => {
    setMensagens((prev) => [...prev, {
      id: Date.now() + Math.random(),
      origem,
      texto,
      ...extras,
    }])
  }, [])

  const limparFluxo = useCallback(() => {
    setFluxo(null)
    setDadosFluxo({})
    setHorarios([])
  }, [])

  const iniciarFluxo = useCallback((novoFluxo) => {
    setFluxo(novoFluxo)
    setDadosFluxo({})
    setHorarios([])

    if (novoFluxo === ETAPAS.AGENDAR) {
      adicionarMensagem('ia', 'Perfeito. Vamos agendar passo a passo. Primeiro, qual serviço você quer?')
      if (servicosAtivos.length > 0) {
        adicionarMensagem('ia', 'Escolha um serviço abaixo ou digite o nome.', {
          sugestoes: servicosAtivos.slice(0, 4).map((item) => item.nome || item.titulo || `Serviço ${item.id}`),
        })
      }
      return
    }

    if (novoFluxo === ETAPAS.REAGENDAR) {
      if (agendamentosAtivos.length === 0) {
        adicionarMensagem('ia', 'Não encontrei agendamentos futuros para reagendar.', {
          sugestoes: ['Quero agendar', 'Ver serviços'],
        })
        limparFluxo()
        return
      }
      adicionarMensagem('ia', `Qual agendamento você quer reagendar?\n\n${formatarAgendamentos(agendamentosAtivos)}`, {
        sugestoes: agendamentosAtivos.slice(0, 4).map((item) => `#${item.id}`),
      })
      return
    }

    if (novoFluxo === ETAPAS.CANCELAR) {
      if (agendamentosAtivos.length === 0) {
        adicionarMensagem('ia', 'Não encontrei agendamentos futuros para cancelar.', {
          sugestoes: ['Quero agendar', 'Ver serviços'],
        })
        limparFluxo()
        return
      }
      adicionarMensagem('ia', `Qual agendamento você quer cancelar?\n\n${formatarAgendamentos(agendamentosAtivos)}`, {
        sugestoes: agendamentosAtivos.slice(0, 4).map((item) => `#${item.id}`),
      })
    }
  }, [adicionarMensagem, agendamentosAtivos, limparFluxo, servicosAtivos])

  const processarFluxo = useCallback(async (textoUsuario) => {
    const texto = String(textoUsuario || '').trim()
    if (!texto) return

    if (!fluxo) {
      const intencao = detectarIntencao(texto)
      if (intencao) {
        iniciarFluxo(intencao)
        return
      }

      try {
        setCarregando(true)
        const { data } = await clienteApi.post('/meu-gendaz/ia', {
          pergunta: texto,
          historico: mensagens.slice(-8).map((item) => ({
            role: item.origem === 'ia' ? 'assistant' : 'user',
            content: String(item.texto || ''),
          })),
        })
        adicionarMensagem('ia', data?.resposta || 'Não consegui responder agora.', {
          sugestoes: Array.isArray(data?.sugestoes) ? data.sugestoes : [],
          acao: data?.acao,
        })
      } catch (err) {
        adicionarMensagem('ia', err?.response?.status === 401
          ? 'Sua sessão do Meu Gendaz expirou. Faça login novamente.'
          : 'Não foi possível responder agora.')
      } finally {
        setCarregando(false)
      }
      return
    }

    if (fluxo === ETAPAS.AGENDAR) {
      if (!dadosFluxo.servicoId) {
        const servicoSelecionado = servicosAtivos.find((item) => String(item.id) === texto || normalizarTexto(item.nome || item.titulo).includes(normalizarTexto(texto)))
        if (!servicoSelecionado) {
          adicionarMensagem('ia', 'Não reconheci o serviço. Escolha um item da lista ou digite o nome exato.')
          return
        }
        setDadosFluxo((prev) => ({ ...prev, servicoId: servicoSelecionado.id }))
        adicionarMensagem('ia', `Ótimo. Agora escolha o profissional para ${servicoSelecionado.nome || servicoSelecionado.titulo}.`, {
          sugestoes: profissionaisAtivos.slice(0, 4).map((item) => item.nome || `Profissional ${item.id}`),
        })
        return
      }

      if (!dadosFluxo.profissionalId) {
        const profissionalSelecionado = profissionaisAtivos.find((item) => String(item.id) === texto || normalizarTexto(item.nome).includes(normalizarTexto(texto)))
        if (!profissionalSelecionado) {
          adicionarMensagem('ia', 'Não reconheci o profissional. Escolha um nome da lista ou digite novamente.')
          return
        }
        setDadosFluxo((prev) => ({ ...prev, profissionalId: profissionalSelecionado.id }))
        adicionarMensagem('ia', 'Perfeito. Agora me diga a data desejada no formato AAAA-MM-DD.')
        return
      }

      if (!dadosFluxo.data) {
        const dataInterpretada = interpretarDataLivre(texto)
        if (!dataInterpretada) {
          adicionarMensagem('ia', 'Me diga a data como "hoje", "amanhã" ou "21/08".')
          return
        }
        try {
          setCarregando(true)
          const profissionalAtual = profissionaisAtivos.find((item) => Number(item.id) === Number(dadosFluxo.profissionalId))
          if (!trabalhaNaData(profissionalAtual, dataInterpretada)) {
            adicionarMensagem('ia', 'Esse profissional não atende nessa data. Escolha outro profissional ou outra data.')
            return
          }
          const listaHorarios = await buscarHorarios(dadosFluxo.servicoId, dadosFluxo.profissionalId, dataInterpretada)
          const disponiveis = Array.isArray(listaHorarios) ? listaHorarios.filter((item) => item?.disponivel !== false).map((item) => item.horario || item) : []
          setHorarios(disponiveis)
          setDadosFluxo((prev) => ({ ...prev, data: dataInterpretada }))
          if (disponiveis.length === 0) {
            adicionarMensagem('ia', 'Não encontrei horários para essa data. Me diga outra data.')
            setDadosFluxo((prev) => ({ ...prev, data: null }))
            return
          }
          adicionarMensagem('ia', `Tenho estes horários para ${formatarDataLegivel(dataInterpretada)}: ${disponiveis.join(', ')}. Qual você prefere?`)
        } catch {
          adicionarMensagem('ia', 'Não consegui consultar os horários agora. Tente outra data.')
        } finally {
          setCarregando(false)
        }
        return
      }

      if (!dadosFluxo.hora) {
        const horaInterpretada = interpretarHoraLivre(texto)
        const horarioEncontrado = horarios.find((item) => normalizarHora(item) === horaInterpretada || normalizarTexto(String(item)) === normalizarTexto(texto))
        if (!horaInterpretada || !horarioEncontrado) {
          adicionarMensagem('ia', 'Me diga um horário como 8h, 8:00 ou 14h30. Também pode tocar em um horário da lista.')
          return
        }
        setDadosFluxo((prev) => ({ ...prev, hora: normalizarHora(horarioEncontrado) }))
        adicionarMensagem('ia', 'Pronto. Quer que eu confirme o agendamento agora? Responda "sim" ou "não".')
        return
      }

      if (/^s(im)?$|^confirmar$|^ok$|^pode$/.test(normalizarTexto(texto))) {
        try {
          setCarregando(true)
          await criarAgendamento({
            servicoId: dadosFluxo.servicoId,
            profissionalId: dadosFluxo.profissionalId,
            data: dadosFluxo.data,
            hora: dadosFluxo.hora,
            observacoes: dadosFluxo.observacoes || '',
            cupomCodigo: dadosFluxo.cupomCodigo || '',
          })
          adicionarMensagem('ia', 'Agendamento confirmado com sucesso. Se quiser, posso ajudar com outro atendimento.')
          limparFluxo()
        } catch (err) {
          adicionarMensagem('ia', err?.response?.data?.mensagem || 'Não consegui confirmar o agendamento.')
        } finally {
          setCarregando(false)
        }
        return
      }

      adicionarMensagem('ia', 'Se quiser confirmar, responda "sim". Se quiser cancelar, digite "cancelar".')
      return
    }

    if (fluxo === ETAPAS.REAGENDAR) {
      if (!dadosFluxo.agendamentoId) {
        const agendamentoSelecionado = agendamentosAtivos.find((item) => String(item.id) === texto || texto.includes(String(item.id)))
        if (!agendamentoSelecionado) {
          adicionarMensagem('ia', 'Não reconheci o agendamento. Escolha um ID da lista acima.')
          return
        }
        setDadosFluxo((prev) => ({ ...prev, agendamentoId: agendamentoSelecionado.id, agendamentoBase: agendamentoSelecionado }))
        adicionarMensagem('ia', 'Qual a nova data? Use o formato AAAA-MM-DD.')
        return
      }

      if (!dadosFluxo.novaData) {
        const dataInterpretada = interpretarDataLivre(texto)
        if (!dataInterpretada) {
          adicionarMensagem('ia', 'Me diga a nova data como "hoje", "amanhã" ou "21/08".')
          return
        }
        try {
          setCarregando(true)
          const servicoId = dadosFluxo.agendamentoBase?.servicoId
          const profissionalId = dadosFluxo.agendamentoBase?.profissionalId
          const listaHorarios = await buscarHorarios(servicoId, profissionalId, dataInterpretada)
          const disponiveis = Array.isArray(listaHorarios) ? listaHorarios.filter((item) => item?.disponivel !== false).map((item) => item.horario || item) : []
          setHorarios(disponiveis)
          setDadosFluxo((prev) => ({ ...prev, novaData: dataInterpretada }))
          if (disponiveis.length === 0) {
            adicionarMensagem('ia', 'Não encontrei horários nessa data. Me envie outra data.')
            setDadosFluxo((prev) => ({ ...prev, novaData: null }))
            return
          }
          adicionarMensagem('ia', `Tenho estes horários para ${formatarDataLegivel(dataInterpretada)}: ${disponiveis.join(', ')}. Qual você quer?`)
        } catch {
          adicionarMensagem('ia', 'Não consegui consultar os horários para reagendar.')
        } finally {
          setCarregando(false)
        }
        return
      }

      if (!dadosFluxo.novaHora) {
        const horaInterpretada = interpretarHoraLivre(texto)
        const horarioEncontrado = horarios.find((item) => normalizarHora(item) === horaInterpretada || normalizarTexto(String(item)) === normalizarTexto(texto))
        if (!horaInterpretada || !horarioEncontrado) {
          adicionarMensagem('ia', 'Me diga um horário como 8h, 8:00 ou 14h30.')
          return
        }
        setDadosFluxo((prev) => ({ ...prev, novaHora: normalizarHora(horarioEncontrado) }))
        adicionarMensagem('ia', 'Posso confirmar o reagendamento agora? Responda "sim" ou "não".')
        return
      }

      if (/^s(im)?$|^confirmar$|^ok$|^pode$/.test(normalizarTexto(texto))) {
        try {
          setCarregando(true)
          await reagendar(dadosFluxo.agendamentoId, {
            novaData: dadosFluxo.novaData,
            novaHora: dadosFluxo.novaHora,
          })
          adicionarMensagem('ia', 'Reagendamento concluído.')
          limparFluxo()
        } catch (err) {
          adicionarMensagem('ia', err?.response?.data?.mensagem || 'Não consegui reagendar agora.')
        } finally {
          setCarregando(false)
        }
        return
      }

      adicionarMensagem('ia', 'Se quiser confirmar, responda "sim".')
      return
    }

    if (fluxo === ETAPAS.CANCELAR) {
      if (!dadosFluxo.agendamentoId) {
        const agendamentoSelecionado = agendamentosAtivos.find((item) => String(item.id) === texto || texto.includes(String(item.id)))
        if (!agendamentoSelecionado) {
          adicionarMensagem('ia', 'Não reconheci o agendamento. Escolha um ID da lista acima.')
          return
        }
        setDadosFluxo((prev) => ({ ...prev, agendamentoId: agendamentoSelecionado.id }))
        adicionarMensagem('ia', 'Quer mesmo cancelar esse agendamento? Responda "sim" para confirmar.')
        return
      }

      if (/^s(im)?$|^confirmar$|^ok$|^pode$/.test(normalizarTexto(texto))) {
        try {
          setCarregando(true)
          await cancelarAgendamento(dadosFluxo.agendamentoId, 'Cancelado pelo cliente no Gendaz')
          adicionarMensagem('ia', 'Agendamento cancelado com sucesso.')
          limparFluxo()
        } catch (err) {
          adicionarMensagem('ia', err?.response?.data?.mensagem || 'Não consegui cancelar agora.')
        } finally {
          setCarregando(false)
        }
        return
      }

      adicionarMensagem('ia', 'Se quiser confirmar o cancelamento, responda "sim".')
    }
  }, [adicionarMensagem, agendamentosAtivos, buscarHorarios, cancelarAgendamento, criarAgendamento, dadosFluxo, fluxo, horarios, iniciarFluxo, limparFluxo, mensagens, profissionaisAtivos, reagendar, servicosAtivos])

  const enviarPergunta = useCallback(async (pergunta) => {
    const textoUsuario = String(pergunta || '').trim()
    if (!textoUsuario || carregando) return

    adicionarMensagem('cliente', textoUsuario)
    setInputValue('')

    const intencao = detectarIntencao(textoUsuario)
    if (intencao && !fluxo) {
      iniciarFluxo(intencao)
      return
    }

    try {
      await processarFluxo(textoUsuario)
    } catch (err) {
      adicionarMensagem('ia', err?.response?.status === 401
        ? 'Sua sessão do Meu Gendaz expirou. Faça login novamente.'
        : 'Não foi possível responder agora.')
    }
  }, [adicionarMensagem, carregando, fluxo, iniciarFluxo, processarFluxo])

  function handleSubmit(e) {
    e.preventDefault()
    void enviarPergunta(inputValue)
  }

  function handleAtalho(atalho) {
    if (atalho === 'agenda') {
      navigate('agenda')
      return
    }
    if (atalho === 'servicos') {
      adicionarMensagem('ia', `Serviços disponíveis:\n\n${formatarServicos(servicosAtivos) || 'Nenhum serviço encontrado.'}`, {
        sugestoes: servicosAtivos.slice(0, 4).map((item) => `Agendar ${item.nome || item.titulo || item.id}`),
      })
      return
    }
    if (atalho === 'promocoes') {
      adicionarMensagem('ia', 'Posso te mostrar promoções e benefícios na aba de promoções do Meu Gendaz.')
      return
    }
    if (atalho === 'agendar' || atalho === 'reagendar' || atalho === 'cancelar') {
      iniciarFluxo(atalho)
      return
    }
    void enviarPergunta(atalho)
  }

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">gendazIA</span>
        <h1>Converse naturalmente</h1>
        <p>Agende, reagende ou cancele sem sair da conversa. Eu vou pedindo uma coisa por vez.</p>
      </header>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-chat">
          <div className="gendaz-panel__head">
            <Bot size={18} />
            <h2>gendazIA</h2>
          </div>

          <div className="gendaz-chat__messages">
            {mensagens.map((item) => (
              <div key={item.id} className={`gendaz-chat__message gendaz-chat__message--${item.origem}`}>
                <div className="gendaz-chat__text">{item.texto}</div>
                {item.sugestoes && item.sugestoes.length > 0 && (
                  <div className="gendaz-chat__sugestoes">
                    {item.sugestoes.map((sug, i) => (
                      <button key={i} className="gendaz-btn gendaz-btn--small" onClick={() => handleAtalho(sug)}>
                        {sug}
                      </button>
                    ))}
                  </div>
                )}
                {item.acao && (
                  <div className="gendaz-chat__acao">
                    <Calendar size={14} /> Ação sugerida: {String(item.acao)}
                    <button className="gendaz-btn gendaz-btn--small gendaz-btn--primary" onClick={() => navigate('agenda')}>
                      <ArrowRight size={14} /> Ir para agenda
                    </button>
                  </div>
                )}
              </div>
            ))}

            {carregando && (
              <div className="gendaz-chat__message gendaz-chat__message--ia gendaz-chat__message--loading">
                <Loader size={16} className="gendaz-spinner" /> <span>Digitando...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          <form className="gendaz-chat__form" onSubmit={handleSubmit}>
            <input
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              placeholder="Digite sua mensagem..."
              disabled={carregando}
              autoFocus
            />
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={carregando || !inputValue.trim()}>
              <Send size={16} />
            </button>
          </form>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head">
            <Sparkles size={18} />
            <h2>Como posso ajudar</h2>
          </div>
          <div className="gendaz-stack">
            {ATALHOS.map((item) => (
              <button key={item.label} type="button" className="gendaz-mini-card" onClick={() => handleAtalho(item.prompt)}>
                <strong>{item.label}</strong>
                <span>{item.label === 'Ver serviços' ? formatarServicos(servicosAtivos).split('\n').slice(0, 1)[0] || 'Veja os serviços' : item.prompt}</span>
              </button>
            ))}
          </div>
          <div className="gendaz-panel__note" style={{ marginTop: 16 }}>
            <strong>Fluxo guiado</strong>
            <p>Eu peço serviço, profissional, data e horário um por um até finalizar.</p>
          </div>
          <div className="gendaz-panel__note" style={{ marginTop: 12 }}>
            <strong>Atendimento atual</strong>
            <p>{cliente?.empresaNome || 'Sua empresa'}</p>
          </div>
        </article>
      </div>
    </section>
  )
}
