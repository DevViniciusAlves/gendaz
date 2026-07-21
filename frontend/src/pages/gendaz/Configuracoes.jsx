import { useContext, useState, useEffect } from 'react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import { Bell, LogOut, Shield, UserRound, Loader, AlertCircle } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { aplicarMascara, padronizarTelefone, validarTelefone } from '../../utils/phoneUtils.js'

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

  useEffect(() => {
    if (cliente) {
      setFormData({
        nome: cliente.nome || '',
        telefone: aplicarMascara(cliente.telefone || ''),
        email: cliente.email || '',
      })
      const nomeOk = cliente.nome && cliente.nome.trim().length >= 3 && cliente.nome !== 'Cliente'
      const telOk = !validarTelefone(cliente.telefone || '')
      setPerfilIncompleto(!nomeOk || !telOk)
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

    setErros(novosErros)
    return Object.keys(novosErros).length === 0
  }

  async function handleSalvarPerfil(e) {
    e.preventDefault()
    setErros({})
    if (!validarFormulario()) return

    try {
      setSalvando(true)
      const telefone = padronizarTelefone(formData.telefone)
      await atualizarPerfil({
        nome: formData.nome.trim(),
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
    if (window.confirm('Tem certeza que deseja sair?')) {
      await logout()
      navigate('/meu-gendaz', { replace: true })
      window.location.reload()
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
            <label>
              <span>Telefone *</span>
              <input
                type="tel"
                value={formData.telefone}
                onChange={(e) => setFormData({ ...formData, telefone: aplicarMascara(e.target.value) })}
                placeholder="65 993360300"
                maxLength={19}
                required
              />
              {erros.telefone && <small className="gendaz-texto-erro">{erros.telefone}</small>}
              <small>Use apenas o código da cidade + número.</small>
            </label>
            <label>
              <span>E-mail (somente leitura)</span>
              <input type="email" value={formData.email} disabled className="gendaz-input--disabled" />
              <small>Seu email de login. Para alterar, entre em contato.</small>
            </label>
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={salvando}>
              {salvando ? <><Loader size={16} /> Salvando...</> : 'Salvar Alteracoes'}
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
              {salvando ? <><Loader size={16} /> Salvando...</> : 'Salvar Preferencias'}
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
              {salvando ? <><Loader size={16} /> Salvando...</> : 'Salvar Privacidade'}
            </button>
          </form>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><LogOut size={18} /><h2>Sair da conta</h2></div>
          <p>Sua sessao pode permanecer salva por longo periodo no mesmo dispositivo.</p>
          <button className="gendaz-btn gendaz-btn--danger" type="button" onClick={handleLogout}>
            <LogOut size={16} /> Sair
          </button>
        </article>
      </div>
    </section>
  )
}
