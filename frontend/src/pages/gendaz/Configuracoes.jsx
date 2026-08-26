import { useContext, useState, useEffect } from 'react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import { Bell, LogOut, Shield, UserRound, Loader, AlertCircle, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { normalizarParaApi, normalizarParaInput, obterExemploTelefone, validarTelefone } from '../../utils/phoneUtils.js'
import InternationalPhoneInput from '../../components/InternationalPhoneInput.jsx'

const TOAST_LOGOUT_ID = 'meu-gendaz-logout'

export default function Configuracoes() {
  const navigate = useNavigate()
  const { cliente, configuracoes, atualizarPerfil, atualizarNotificacoes, atualizarPrivacidade, logout } = useContext(ClienteGendazContext)

  const [formData, setFormData] = useState({ nome: '', telefone: '', email: '' })
  const [notificacoes, setNotificacoes] = useState({ email: true, sms: false, push: true })
  const [privacidade, setPrivacidade] = useState({ compartilharHistorico: false })
  const [salvando, setSalvando] = useState(false)
  const [mensagem, setMensagem] = useState('')
  const [erros, setErros] = useState({})
  const [perfilIncompleto, setPerfilIncompleto] = useState(false)
  const [abrirLogout, setAbrirLogout] = useState(false)
  const [saindo, setSaindo] = useState(false)

  useEffect(() => {
    if (cliente) {
      setFormData({
        nome: cliente.nome || '',
        telefone: normalizarParaInput(cliente.telefone || ''),
        email: cliente.email || '',
      })
      const nomeOk = cliente.nome && cliente.nome.trim().length >= 3 && cliente.nome !== 'Cliente'
      const telOk = !validarTelefone(normalizarParaInput(cliente.telefone || ''))
      const emailOk = cliente.email && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cliente.email)
      setPerfilIncompleto(!nomeOk || !telOk || !emailOk)
    }
  }, [cliente])

  useEffect(() => {
    if (configuracoes) {
      setNotificacoes({
        email: configuracoes.notificacoes?.email ?? true,
        sms: configuracoes.notificacoes?.sms ?? false,
        push: configuracoes.notificacoes?.push ?? true,
      })
      setPrivacidade({
        compartilharHistorico: configuracoes.compartilharHistorico ?? false,
      })
    }
  }, [configuracoes])

  function mostrarMensagem(texto) {
    setMensagem(texto)
    setErros({})
    setTimeout(() => setMensagem(''), 3000)
  }

  function validarFormulario() {
    const novosErros = {}

    if (!formData.nome || formData.nome.trim().length < 3) {
      novosErros.nome = 'Nome deve ter pelo menos 3 caracteres.'
    } else if (formData.nome.trim() === 'Cliente') {
      novosErros.nome = 'Complete seu nome, nao use "Cliente".'
    } else if (/^\d+$/.test(formData.nome.trim())) {
      novosErros.nome = 'Nome nao pode conter apenas numeros.'
    }

    const erroTelefone = validarTelefone(formData.telefone)
    if (erroTelefone) {
      novosErros.telefone = erroTelefone
    }
    if (!formData.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email.trim())) {
      novosErros.email = 'Informe um e-mail valido.'
    }

    setErros(novosErros)
    return Object.keys(novosErros).length === 0
  }

  async function handleSalvarPerfil(e) {
    e.preventDefault()
    setErros({})
    if (!validarFormulario()) return

    try {
      setSalvando(true)
      const telefone = normalizarParaApi(formData.telefone) || formData.telefone
      await atualizarPerfil({
        nome: formData.nome.trim(),
        email: formData.email.trim().toLowerCase(),
        telefone,
      })
      setPerfilIncompleto(false)
      mostrarMensagem('Perfil atualizado com sucesso!')
    } catch (err) {
      const msg = err.response?.data?.mensagem || err.message || 'Erro ao salvar perfil.'
      setErros({ geral: msg })
    } finally {
      setSalvando(false)
    }
  }

  async function handleSalvarNotificacoes(e) {
    e.preventDefault()
    try {
      setSalvando(true)
      await atualizarNotificacoes(notificacoes)
      mostrarMensagem('Preferencias de notificacao atualizadas!')
    } catch (err) {
      setErros({ geral: err.response?.data?.mensagem || err.message || 'Erro ao salvar notificacoes.' })
    } finally {
      setSalvando(false)
    }
  }

  async function handleSalvarPrivacidade(e) {
    e.preventDefault()
    try {
      setSalvando(true)
      await atualizarPrivacidade(privacidade)
      mostrarMensagem('Preferencias de privacidade atualizadas!')
    } catch (err) {
      setErros({ geral: err.response?.data?.mensagem || err.message || 'Erro ao salvar privacidade.' })
    } finally {
      setSalvando(false)
    }
  }

  async function handleLogout() {
    setSaindo(true)
    setAbrirLogout(false)
    window.dispatchEvent(new CustomEvent('gendaz:toast', {
      detail: { id: TOAST_LOGOUT_ID, type: 'loading', message: 'Saindo da conta... aguarde' },
    }))
    try {
      await logout()
      navigate('..', { replace: true })
    } finally {
      window.dispatchEvent(new CustomEvent('gendaz:toast-dismiss', {
        detail: { id: TOAST_LOGOUT_ID },
      }))
      setSaindo(false)
    }
  }

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Configuracoes</span>
        <h1>Meu perfil e preferencias</h1>
        <p>Nome, telefone, notificacoes, privacidade e sessao.</p>
      </header>

      {mensagem && <div className="gendaz-mensagem gendaz-mensagem--sucesso">{mensagem}</div>}
      {erros.geral && <div className="gendaz-auth__error">{erros.geral}</div>}

      {perfilIncompleto && (
        <div className="gendaz-alerta-aviso">
          <AlertCircle size={20} />
          <div>
            <strong>Perfil incompleto</strong>
            <p>Complete seu nome e telefone para poder agendar.</p>
          </div>
        </div>
      )}

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><UserRound size={18} /><h2>Meu perfil</h2></div>
          <form className="gendaz-form" onSubmit={handleSalvarPerfil}>
            <label>
              <span>Nome *</span>
              <input type="text" value={formData.nome} onChange={(e) => setFormData({ ...formData, nome: e.target.value })} placeholder="Seu nome completo" required />
              {erros.nome && <small className="gendaz-texto-erro">{erros.nome}</small>}
            </label>
            <InternationalPhoneInput
              label="Telefone *"
              value={formData.telefone}
              onChangeValue={(valor) => setFormData({ ...formData, telefone: valor || '' })}
              defaultCountry="BR"
              error={erros.telefone}
              helper={`Exemplo para o país selecionado: ${obterExemploTelefone('BR') || '+55 (65) 99336-0341'}`}
              required
            />
            <label>
              <span>E-mail</span>
              <input type="email" value={formData.email} onChange={(e) => setFormData({ ...formData, email: e.target.value })} required />
              {erros.email && <small className="field-error">{erros.email}</small>}
            </label>
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={salvando || !formData.nome.trim() || !formData.email.trim() || Boolean(validarTelefone(formData.telefone))}>
              {salvando ? <><Loader className="spin" size={16} /> Salvando...</> : 'Salvar Alteracoes'}
            </button>
          </form>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><Bell size={18} /><h2>Notificacoes</h2></div>
          <form className="gendaz-form" onSubmit={handleSalvarNotificacoes}>
            <label className="gendaz-checkbox">
              <input type="checkbox" checked={notificacoes.email} onChange={(e) => setNotificacoes({ ...notificacoes, email: e.target.checked })} />
              <span>Receber notificacoes por e-mail</span>
            </label>
            <label className="gendaz-checkbox">
              <input type="checkbox" checked={notificacoes.sms} onChange={(e) => setNotificacoes({ ...notificacoes, sms: e.target.checked })} />
              <span>Receber notificacoes por SMS</span>
            </label>
            <label className="gendaz-checkbox">
              <input type="checkbox" checked={notificacoes.push} onChange={(e) => setNotificacoes({ ...notificacoes, push: e.target.checked })} />
              <span>Receber notificacoes push</span>
            </label>
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={salvando}>
              {salvando ? <><Loader className="spin" size={16} /> Salvando...</> : 'Salvar Preferencias'}
            </button>
          </form>
        </article>
      </div>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><Shield size={18} /><h2>Privacidade</h2></div>
          <form className="gendaz-form" onSubmit={handleSalvarPrivacidade}>
            <label className="gendaz-checkbox">
              <input type="checkbox" checked={privacidade.compartilharHistorico} onChange={(e) => setPrivacidade({ ...privacidade, compartilharHistorico: e.target.checked })} />
              <span>Compartilhar historico com a IA para recomendacoes</span>
            </label>
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={salvando}>
              {salvando ? <><Loader className="spin" size={16} /> Salvando...</> : 'Salvar Privacidade'}
            </button>
          </form>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><LogOut size={18} /><h2>Sair da conta</h2></div>
          <p>Sua sessao pode permanecer salva por longo periodo no mesmo dispositivo.</p>
          <button className="gendaz-btn gendaz-btn--danger" type="button" onClick={() => setAbrirLogout(true)} disabled={saindo}>
            {saindo ? <><Loader className="spin" size={16} /> Saindo...</> : <><LogOut size={16} /> Sair</>}
          </button>
        </article>
      </div>

      {abrirLogout && (
        <div className="gendaz-modal-overlay" onClick={() => setAbrirLogout(false)}>
          <div className="gendaz-modal gendaz-modal--confirm" onClick={(e) => e.stopPropagation()}>
            <div className="gendaz-modal__head">
              <h2>Confirmar saida</h2>
              <button type="button" className="gendaz-modal__close" onClick={() => setAbrirLogout(false)} aria-label="Fechar">
                <X size={18} />
              </button>
            </div>
            <p className="gendaz-modal__texto">Tem certeza que deseja sair da sua conta no Meu Gendaz?</p>
            <div className="gendaz-modal__actions">
              <button type="button" className="gendaz-btn gendaz-btn--ghost" onClick={() => setAbrirLogout(false)} disabled={saindo}>
                Cancelar
              </button>
              <button type="button" className="gendaz-btn gendaz-btn--danger" onClick={handleLogout} disabled={saindo}>
                {saindo ? <><Loader className="spin" size={16} /> Saindo...</> : <><LogOut size={16} /> Sair</>}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
