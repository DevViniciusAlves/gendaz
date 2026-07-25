import { CalendarClock, Copy, Download, Eye, EyeOff, KeyRound, Link as LinkIcon, RefreshCw, Save, Send } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { PLANOS } from '../services/localStore.js'
import { aplicarMascara, padronizarTelefone, validarTelefone } from '../utils/phoneUtils.js'

const DIAS_ATENDIMENTO = [
  { value: 'SEGUNDA', label: 'Seg', fullLabel: 'Segunda' },
  { value: 'TERCA', label: 'Ter', fullLabel: 'Terça' },
  { value: 'QUARTA', label: 'Qua', fullLabel: 'Quarta' },
  { value: 'QUINTA', label: 'Qui', fullLabel: 'Quinta' },
  { value: 'SEXTA', label: 'Sex', fullLabel: 'Sexta' },
  { value: 'SABADO', label: 'Sáb', fullLabel: 'Sábado' },
  { value: 'DOMINGO', label: 'Dom', fullLabel: 'Domingo' },
]

const TIMEZONE_OPCOES = [
  { value: 'America/Cuiaba', label: 'Cuiabá (America/Cuiaba)' },
  { value: 'America/Sao_Paulo', label: 'São Paulo (America/Sao_Paulo)' },
  { value: 'America/Manaus', label: 'Manaus (America/Manaus)' },
  { value: 'America/Rio_Branco', label: 'Rio Branco (America/Rio_Branco)' },
  { value: 'America/Porto_Velho', label: 'Porto Velho (America/Porto_Velho)' },
  { value: 'America/Belem', label: 'Belém (America/Belem)' },
  { value: 'America/Fortaleza', label: 'Fortaleza (America/Fortaleza)' },
  { value: 'America/Recife', label: 'Recife (America/Recife)' },
  { value: 'America/Bahia', label: 'Bahia (America/Bahia)' },
  { value: 'UTC', label: 'UTC' },
]

function diaAtendimentoPorValor(valor) {
  return DIAS_ATENDIMENTO.find((dia) => dia.value === valor)
}

function criarHorariosPadrao() {
  return DIAS_ATENDIMENTO.map((dia, index) => ({
    diaSemana: dia.value,
    diaLabel: dia.label,
    ativo: index < 5,
    horaInicio: index < 5 ? '08:00' : '',
    horaFim: index < 5 ? '18:00' : '',
    intervaloInicio: '',
    intervaloFim: '',
  }))
}

function normalizarHorariosAtendimento(lista) {
  const porDia = new Map((lista || []).map((item) => [item.diaSemana, item]))
  return DIAS_ATENDIMENTO.map((dia, index) => {
    const item = porDia.get(dia.value)
    if (!item) {
      return {
        diaSemana: dia.value,
        diaLabel: dia.label,
        diaFullLabel: dia.fullLabel,
        ativo: index < 5,
        horaInicio: index < 5 ? '08:00' : '',
        horaFim: index < 5 ? '18:00' : '',
        intervaloInicio: '',
        intervaloFim: '',
      }
    }
    return {
      diaSemana: item.diaSemana,
      diaLabel: item.diaLabel || dia.label,
      diaFullLabel: item.diaFullLabel || dia.fullLabel,
      ativo: Boolean(item.ativo),
      horaInicio: item.horaInicio || '',
      horaFim: item.horaFim || '',
      intervaloInicio: item.intervaloInicio || '',
      intervaloFim: item.intervaloFim || '',
    }
  })
}

function atualizarHorario(lista, index, campo, valor) {
  return lista.map((item, atualIndex) => {
    if (atualIndex !== index) return item
    const proximo = { ...item, [campo]: valor }
    if (campo === 'ativo' && valor && !proximo.horaInicio && !proximo.horaFim) {
      proximo.horaInicio = '08:00'
      proximo.horaFim = '18:00'
    }
    return proximo
  })
}

export default function Configuracoes() {
  const [data, , { reload }] = useLocalData('configuracoes')
  const { usuario, logout } = useAuth()
  const navigate = useNavigate()
  const [empresa, setEmpresa] = useState(data.empresa)
  const [salvo, setSalvo] = useState(false)
  const [erro, setErro] = useState('')
  const [solicitacaoAberta, setSolicitacaoAberta] = useState(false)
  const [mensagemAlteracao, setMensagemAlteracao] = useState('')
  const [statusChamado, setStatusChamado] = useState('')
  const [erroChamado, setErroChamado] = useState('')
  const [enviandoChamado, setEnviandoChamado] = useState(false)
  const [senhaForm, setSenhaForm] = useState({ senhaAtual: '', novaSenha: '', confirmarNovaSenha: '' })
  const [mostrarSenhaAtual, setMostrarSenhaAtual] = useState(false)
  const [mostrarNovaSenha, setMostrarNovaSenha] = useState(false)
  const [mostrarConfirmarNovaSenha, setMostrarConfirmarNovaSenha] = useState(false)
  const [statusSenha, setStatusSenha] = useState('')
  const [erroSenha, setErroSenha] = useState('')
  const [salvandoSenha, setSalvandoSenha] = useState(false)
  const [portalClienteLink, setPortalClienteLink] = useState(null)
  const [slugPortalCliente, setSlugPortalCliente] = useState('')
  const [statusLink, setStatusLink] = useState('')
  const [erroLink, setErroLink] = useState('')
  const [salvandoLink, setSalvandoLink] = useState(false)
  const [recarregando, setRecarregando] = useState(false)
  const [horariosAtendimento, setHorariosAtendimento] = useState([])
  const [horariosCarregados, setHorariosCarregados] = useState(false)
  const [statusHorario, setStatusHorario] = useState('')
  const [erroHorario, setErroHorario] = useState('')
  const [salvandoHorario, setSalvandoHorario] = useState(false)

  useEffect(() => {
    setEmpresa({
      ...data.empresa,
      telefone: aplicarMascara(data.empresa?.telefone || ''),
    })
  }, [data.empresa])

  async function carregarLink() {
    const response = await appApi.obterLinkAgendamento()
    setPortalClienteLink(response)
    setSlugPortalCliente(response.slug || '')
  }

  async function carregarHorarios() {
    const response = await appApi.listarHorariosAtendimento()
    setHorariosAtendimento(normalizarHorariosAtendimento(response))
    setHorariosCarregados(true)
  }

  useEffect(() => {
    carregarLink().catch(() => setErroLink('Não foi possível carregar o link do Meu Gendaz.'))
    carregarHorarios().catch(() => setErroHorario('Não foi possível carregar o horário de atendimento.'))
  }, [])

  async function recarregar() {
    if (recarregando) return
    setRecarregando(true)
    try {
      await reload(true)
      await Promise.all([carregarLink(), carregarHorarios()])
      setErroLink('')
      setErroHorario('')
    } catch (error) {
      setErroLink(error.response?.data?.mensagem || 'Não foi possível recarregar os dados.')
    } finally {
      setRecarregando(false)
    }
  }

  async function salvar(event) {
    event.preventDefault()
    setErro('')
    setSalvo(false)

    const telefone = padronizarTelefone(empresa.telefone)
    if (!telefone) {
      setErro('Telefone deve ter entre 16 e 19 caracteres.')
      return
    }

    await appApi.atualizarEmpresa(empresa.id, {
      nomeFantasia: data.empresa.nomeFantasia,
      documento: data.empresa.documento,
      telefone,
      email: data.empresa.email,
      timezone: empresa?.timezone || 'America/Cuiaba',
      status: empresa.status || 'ATIVA',
    })
    await reload(true)
    setSalvo(true)
  }

  async function trocarSenha(event) {
    event.preventDefault()
    setErroSenha('')
    setStatusSenha('')
    if (senhaForm.novaSenha !== senhaForm.confirmarNovaSenha) {
      setErroSenha('A nova senha e a confirmação precisam ser iguais.')
      return
    }
    if (senhaForm.novaSenha.length < 8 || senhaForm.novaSenha.length > 72) {
      setErroSenha('A nova senha deve ter entre 8 e 72 caracteres.')
      return
    }
    setSalvandoSenha(true)
    try {
      await appApi.trocarSenha(senhaForm.senhaAtual, senhaForm.novaSenha, senhaForm.confirmarNovaSenha)
      setSenhaForm({ senhaAtual: '', novaSenha: '', confirmarNovaSenha: '' })
      setMostrarSenhaAtual(false)
      setMostrarNovaSenha(false)
      setMostrarConfirmarNovaSenha(false)
      setStatusSenha('Senha alterada com sucesso. Faça login novamente.')
      logout()
      navigate('/login', {
        replace: true,
        state: { mensagem: 'Senha alterada com sucesso. Faça login novamente.' },
      })
    } catch (error) {
      setErroSenha(error.response?.data?.mensagem || Object.values(error.response?.data?.campos || {})[0] || 'Não foi possível alterar a senha.')
    } finally {
      setSalvandoSenha(false)
    }
  }

  async function solicitarAlteracao(event) {
    event.preventDefault()
    setErroChamado('')
    setStatusChamado('')
    const mensagem = mensagemAlteracao.trim()
    if (mensagem.length < 10) {
      setErroChamado('Descreva a alteração desejada com pelo menos 10 caracteres.')
      return
    }
    setEnviandoChamado(true)
    try {
      await appApi.criarChamado({
        assunto: 'Solicitação de alteração de dados da empresa',
        prioridade: 'MEDIA',
        mensagem,
      })
      setMensagemAlteracao('')
      setSolicitacaoAberta(false)
      setStatusChamado('Solicitação enviada para o Super Admin.')
    } catch (error) {
      setErroChamado(error.response?.data?.mensagem || Object.values(error.response?.data?.campos || {})[0] || 'Não foi possível abrir o chamado.')
    } finally {
      setEnviandoChamado(false)
    }
  }

  async function salvarSlugAgendamento(event) {
    event.preventDefault()
    setErroLink('')
    setStatusLink('')
    setSalvandoLink(true)
    try {
      const response = await appApi.atualizarLinkAgendamento(slugPortalCliente.trim())
      setPortalClienteLink(response)
      setSlugPortalCliente(response.slug || '')
      setStatusLink('Portal do cliente atualizado.')
    } catch (error) {
      setErroLink(error.response?.data?.mensagem || Object.values(error.response?.data?.campos || {})[0] || 'Não foi possível atualizar o link.')
    } finally {
      setSalvandoLink(false)
    }
  }

  async function copiarLinkAgendamento() {
    if (!portalClienteLink?.publicUrl) return
    try {
      await navigator.clipboard.writeText(portalClienteLink.publicUrl)
      setStatusLink('Link copiado.')
    } catch {
      setErroLink('Não foi possível copiar automaticamente. Selecione o link e copie manualmente.')
    }
  }

  function alterarHorario(index, campo, valor) {
    setHorariosAtendimento((atual) => atualizarHorario(atual, index, campo, valor))
  }

  async function salvarHorariosAtendimento(event) {
    event.preventDefault()
    setErroHorario('')
    setStatusHorario('')
    if (!horariosCarregados) {
      setErroHorario('Aguarde o carregamento dos horários antes de salvar.')
      return
    }
    setSalvandoHorario(true)
    try {
      const response = await appApi.salvarHorariosAtendimento(horariosAtendimento)
      setHorariosAtendimento(normalizarHorariosAtendimento(response))
      setStatusHorario('Horários de atendimento salvos com sucesso.')
    } catch (error) {
      setErroHorario(error.response?.data?.mensagem || Object.values(error.response?.data?.campos || {})[0] || 'Não foi possível salvar os horários.')
    } finally {
      setSalvandoHorario(false)
    }
  }

  const planoAtivo = PLANOS[usuario.plano]
  const recursosPlano = usuario.plano === 'PRO'
    ? ['Profissionais', 'Financeiro', 'Pagamentos', 'Relatórios']
    : ['Agenda', 'Clientes', 'Serviços']
  const ramoEmpresa = empresa?.ramoDisplayName || 'Detectando...'
  const regraRamo = empresa?.ramo
    ? `Configuração automática: ${empresa.diasRegular ?? '-'} dias (regular) / ${empresa.diasAltoRisco ?? '-'} dias (risco)`
    : 'Crie um serviço para detectar automaticamente o ramo da empresa.'
  const horariosExibidos = horariosAtendimento.length > 0 ? horariosAtendimento : criarHorariosPadrao()
  const qrCodeUrl = portalClienteLink?.publicUrl
    ? `https://api.qrserver.com/v1/create-qr-code/?size=320x320&format=png&data=${encodeURIComponent(portalClienteLink.publicUrl)}`
    : ''

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Configuração</span>
        <h1>Configurações</h1>
        <p>Centralize os dados da empresa, acesso da conta, plano atual e horário de atendimento.</p>
        <div className="page-title-actions">
          <Button variant="secondary" icon={RefreshCw} onClick={recarregar} disabled={recarregando}>
            {recarregando ? 'Recarregando...' : 'Recarregar'}
          </Button>
        </div>
      </div>

      <div className="settings-summary-grid">
        <section className="panel settings-card">
          <div className="settings-card-head">
            <div>
              <span className="section-kicker">Usuário logado</span>
              <h2>{usuario.nome}</h2>
            </div>
            <StatusBadge status={usuario.perfil} />
          </div>
          <p className="settings-card-text">{usuario.email}</p>
          <small className="settings-card-muted">Acesso principal do painel.</small>
        </section>

        <section className="panel settings-card">
          <div className="settings-card-head">
            <div>
              <span className="section-kicker">Plano ativo</span>
              <h2>{planoAtivo?.nome || usuario.plano}</h2>
            </div>
          </div>
          <div className="tag-list">
            {recursosPlano.map((item) => <span key={item}>{item}</span>)}
          </div>
          {usuario.assinatura?.status === 'TESTE' && (
            <small className="settings-card-muted">{usuario.assinatura.diasRestantesTeste} dias restantes do teste grátis.</small>
          )}
          <Link to="/sistema/planos" className="btn btn-secondary settings-link-btn">Ver planos</Link>
        </section>

      </div>

      <section className="panel settings-form-panel">
        <div className="panel-head settings-form-head">
          <div>
            <span className="section-kicker">Empresa</span>
            <h2>Dados da empresa</h2>
          </div>
          {salvo && <p className="success-text">Configurações salvas.</p>}
          {erro && <p className="form-error">{erro}</p>}
          {statusChamado && <p className="success-text">{statusChamado}</p>}
          {erroChamado && <p className="form-error">{erroChamado}</p>}
        </div>

        <form className="form-grid settings-form-grid" onSubmit={salvar}>
          <Input label="Nome fantasia" helper="Leitura apenas. Use Solicitar alteração para mudar este dado." maxLength={100} value={empresa?.nomeFantasia || ''} readOnly />
          <Input label="CNPJ / documento" helper="Leitura apenas. Use Solicitar alteração para mudar este dado." inputMode="numeric" maxLength={14} value={empresa?.documento || ''} readOnly />
          <Input label="Telefone" helper={empresa?.telefone ? (validarTelefone(empresa.telefone) || 'Formato correto') : 'Use codigo da cidade + numero.'} inputMode="numeric" maxLength={19} neutralLimit value={empresa?.telefone || ''} onChange={(e) => setEmpresa({ ...empresa, telefone: aplicarMascara(e.target.value) })} />
          <label className="field">
            <span>Fuso horário</span>
            <select
              value={empresa?.timezone || 'America/Cuiaba'}
              onChange={(event) => setEmpresa((atual) => ({ ...(atual || {}), timezone: event.target.value }))}
            >
              {TIMEZONE_OPCOES.map((opcao) => (
                <option key={opcao.value} value={opcao.value}>
                  {opcao.label}
                </option>
              ))}
            </select>
            <small className="field-hint">Usado para lembretes e validações de horário.</small>
          </label>
          <div className="field field-wide">
            <span>Ramo da empresa</span>
            <div className="settings-ramo-box">
              <div className="settings-ramo-copy">
                <strong>{ramoEmpresa}</strong>
                <small>{regraRamo}</small>
              </div>
              <span className="settings-ramo-badge">Automático</span>
            </div>
            <small className="field-hint">O ramo é detectado automaticamente pelo primeiro serviço criado e fica somente para leitura.</small>
          </div>
          <Input label="E-mail" helper="Leitura apenas. Use Solicitar alteração para mudar este dado." type="email" maxLength={120} value={empresa?.email || ''} readOnly />
          <div className="settings-form-actions field-wide">
            <Button variant="secondary" type="button" onClick={() => setSolicitacaoAberta((aberta) => !aberta)}>Solicitar alteração</Button>
            <Button icon={Save} type="submit">Salvar configurações</Button>
          </div>
        </form>

        {solicitacaoAberta && (
          <form className="support-inline-form" onSubmit={solicitarAlteracao}>
            <label className="field field-wide">
              <span>Solicitação de alteração</span>
              <textarea
                maxLength={500}
                value={mensagemAlteracao}
                onChange={(event) => setMensagemAlteracao(event.target.value)}
                placeholder="Informe quais dados precisam ser alterados e o motivo."
              />
              <small className={mensagemAlteracao.length >= 500 ? 'field-hint limit-reached' : 'field-hint'}>
                {mensagemAlteracao.length >= 500 ? 'Limite de caracteres atingido.' : 'Este pedido será enviado ao Super Admin.'}
                <strong>{mensagemAlteracao.length}/500</strong>
              </small>
            </label>
            <div className="settings-form-actions field-wide">
              <Button icon={Send} type="submit" disabled={enviandoChamado}>{enviandoChamado ? 'Enviando...' : 'Enviar solicitação'}</Button>
            </div>
          </form>
        )}
      </section>

      <section className="panel settings-form-panel schedule-settings-panel">
        <div className="panel-head settings-form-head">
          <div>
            <span className="section-kicker">Atendimento</span>
            <h2>Horário de atendimento</h2>
            <p>Defina os dias e horários em que sua empresa atende. A agenda e o link público respeitam esta configuração.</p>
          </div>
          <CalendarClock size={22} color="var(--primary)" />
        </div>

        {statusHorario && <p className="success-text">{statusHorario}</p>}
        {erroHorario && <p className="form-error">{erroHorario}</p>}

        <form className="schedule-settings-grid" onSubmit={salvarHorariosAtendimento}>
          {horariosExibidos.map((horario, index) => (
            <article className={`panel schedule-day-card ${horario.ativo ? 'is-active' : 'is-inactive'}`} key={horario.diaSemana}>
              <div className="schedule-day-head">
                <label className="schedule-day-toggle">
                  <input
                    type="checkbox"
                    checked={horario.ativo}
                    onChange={(event) => alterarHorario(index, 'ativo', event.target.checked)}
                  />
                  <div>
                    <strong>{horario.diaLabel}</strong>
                    <small>{horario.diaFullLabel || diaAtendimentoPorValor(horario.diaSemana)?.fullLabel || horario.diaLabel}</small>
                    <small>{horario.ativo ? 'Ativo' : 'Inativo'}</small>
                  </div>
                </label>
              </div>
              <div className="schedule-day-fields">
                <label className="field field-compact">
                  <span>Início</span>
                  <input
                    type="time"
                    value={horario.horaInicio}
                    onChange={(event) => alterarHorario(index, 'horaInicio', event.target.value)}
                    disabled={!horario.ativo && !horario.horaInicio}
                  />
                </label>
                <label className="field field-compact">
                  <span>Fim</span>
                  <input
                    type="time"
                    value={horario.horaFim}
                    onChange={(event) => alterarHorario(index, 'horaFim', event.target.value)}
                    disabled={!horario.ativo && !horario.horaFim}
                  />
                </label>
                <label className="field field-compact">
                  <span>Intervalo início</span>
                  <input
                    type="time"
                    value={horario.intervaloInicio}
                    onChange={(event) => alterarHorario(index, 'intervaloInicio', event.target.value)}
                    disabled={!horario.ativo && !horario.intervaloInicio}
                  />
                </label>
                <label className="field field-compact">
                  <span>Intervalo fim</span>
                  <input
                    type="time"
                    value={horario.intervaloFim}
                    onChange={(event) => alterarHorario(index, 'intervaloFim', event.target.value)}
                    disabled={!horario.ativo && !horario.intervaloFim}
                  />
                </label>
              </div>
            </article>
          ))}

          <div className="settings-form-actions field-wide">
            <Button icon={Save} type="submit" disabled={salvandoHorario}>{salvandoHorario ? 'Salvando...' : 'Salvar horários'}</Button>
          </div>
        </form>
      </section>

      <section className="panel settings-form-panel booking-link-panel">
        <div className="panel-head settings-form-head">
          <div>
            <span className="section-kicker">Portal do cliente</span>
            <h2>Meu Gendaz</h2>
            <p>Compartilhe este link na bio ou redes sociais para seus clientes acessarem o portal.</p>
          </div>
          <LinkIcon size={22} color="var(--primary)" />
        </div>

        <div className="booking-link-grid">
          <form className="booking-link-form" onSubmit={salvarSlugAgendamento}>
            <label className="field">
              <span>URL pública do Meu Gendaz</span>
              <input value={portalClienteLink?.publicUrl || ''} readOnly />
            </label>
            <label className="field">
              <span>Slug</span>
              <input maxLength={80} value={slugPortalCliente} onChange={(event) => setSlugPortalCliente(event.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))} />
              <small className="field-hint">Use letras minúsculas, números e hífen.</small>
            </label>
            {statusLink && <p className="success-text">{statusLink}</p>}
            {erroLink && <p className="form-error">{erroLink}</p>}
            <div className="booking-link-actions">
              <Button icon={Save} type="submit" disabled={salvandoLink}>{salvandoLink ? 'Salvando...' : 'Salvar link'}</Button>
              <Button variant="secondary" icon={Copy} type="button" onClick={copiarLinkAgendamento} disabled={!portalClienteLink?.publicUrl}>Copiar link</Button>
            </div>
          </form>

          <div className="booking-qr-card">
            {qrCodeUrl ? <img src={qrCodeUrl} alt="QR Code do Meu Gendaz" /> : <span>QR Code indisponível</span>}
            <a className="btn btn-secondary" href={qrCodeUrl} download="agendeasy-qrcode.png" target="_blank" rel="noreferrer">
              <Download size={17} />
              <span>Baixar QR Code</span>
            </a>
          </div>
        </div>
      </section>

      <section className="panel settings-form-panel">
        <div className="panel-head settings-form-head">
          <div>
            <span className="section-kicker">Segurança</span>
            <h2>Trocar senha</h2>
          </div>
          {statusSenha && <p className="success-text">{statusSenha}</p>}
          {erroSenha && <p className="form-error">{erroSenha}</p>}
        </div>

        <form className="form-grid settings-form-grid" onSubmit={trocarSenha}>
          <label className="field">
            <span>Senha atual</span>
            <div className="password-input-wrap">
              <input
                type={mostrarSenhaAtual ? 'text' : 'password'}
                maxLength={72}
                value={senhaForm.senhaAtual}
                onChange={(e) => setSenhaForm({ ...senhaForm, senhaAtual: e.target.value })}
                required
              />
              <button
                type="button"
                className="password-toggle"
                aria-label={mostrarSenhaAtual ? 'Ocultar senha atual' : 'Mostrar senha atual'}
                onClick={() => setMostrarSenhaAtual((atual) => !atual)}
              >
                {mostrarSenhaAtual ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </label>
          <label className="field">
            <span>Nova senha</span>
            <div className="password-input-wrap">
              <input
                type={mostrarNovaSenha ? 'text' : 'password'}
                maxLength={72}
                value={senhaForm.novaSenha}
                onChange={(e) => setSenhaForm({ ...senhaForm, novaSenha: e.target.value })}
                required
              />
              <button
                type="button"
                className="password-toggle"
                aria-label={mostrarNovaSenha ? 'Ocultar nova senha' : 'Mostrar nova senha'}
                onClick={() => setMostrarNovaSenha((atual) => !atual)}
              >
                {mostrarNovaSenha ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            <small className="field-hint">Use maiúscula, minúscula, número e caractere especial.</small>
          </label>
          <label className="field">
            <span>Confirmar nova senha</span>
            <div className="password-input-wrap">
              <input
                type={mostrarConfirmarNovaSenha ? 'text' : 'password'}
                maxLength={72}
                value={senhaForm.confirmarNovaSenha}
                onChange={(e) => setSenhaForm({ ...senhaForm, confirmarNovaSenha: e.target.value })}
                required
              />
              <button
                type="button"
                className="password-toggle"
                aria-label={mostrarConfirmarNovaSenha ? 'Ocultar confirmação da senha' : 'Mostrar confirmação da senha'}
                onClick={() => setMostrarConfirmarNovaSenha((atual) => !atual)}
              >
                {mostrarConfirmarNovaSenha ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            <small className="field-hint">Repita a nova senha.</small>
          </label>
          <div className="settings-form-actions field-wide">
            <Button icon={KeyRound} type="submit" disabled={salvandoSenha}>{salvandoSenha ? 'Salvando...' : 'Alterar senha'}</Button>
          </div>
        </form>
      </section>
    </section>
  )
}



