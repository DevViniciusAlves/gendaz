import { useContext, useState, useEffect } from 'react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import { Bell, LogOut, Shield, UserRound, Loader } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

export default function Configuracoes() {
  const navigate = useNavigate()
  const { cliente, configuracoes, atualizarPerfil, atualizarNotificacoes, atualizarPrivacidade, logout } = useContext(ClienteGendazContext)

  const [formData, setFormData] = useState({ nome: '', telefone: '', email: '' })
  const [notificacoes, setNotificacoes] = useState({ email: true, sms: false, push: true })
  const [privacidade, setPrivacidade] = useState({ compartilharHistorico: false })
  const [salvando, setSalvando] = useState(false)
  const [mensagem, setMensagem] = useState('')
  const [erros, setErros] = useState({})

  useEffect(() => {
    if (cliente) {
      setFormData({
        nome: cliente.nome || '',
        telefone: cliente.empresaTelefone || cliente.telefone || '',
        email: cliente.email || '',
      })
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
    } else if (/^\d+$/.test(formData.nome.trim())) {
      novosErros.nome = 'Nome não pode conter apenas números.'
    }

    if (!formData.email || !formData.email.includes('@') || !formData.email.includes('.')) {
      novosErros.email = 'Email inválido.'
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
      await atualizarPerfil(formData)
      mostrarMensagem('Perfil atualizado com sucesso!')
    } catch (err) {
      const msg = err.response?.data?.mensagem || err.message || 'Erro ao salvar perfil.'
      if (err.response?.status === 400) {
        setErros({ geral: msg })
      } else {
        setErros({ geral: msg })
      }
    } finally {
      setSalvando(false)
    }
  }

  async function handleSalvarNotificacoes(e) {
    e.preventDefault()
    try {
      setSalvando(true)
      await atualizarNotificacoes(notificacoes)
      mostrarMensagem('Preferências de notificação atualizadas!')
    } catch (err) {
      setErros({ geral: err.response?.data?.mensagem || err.message || 'Erro ao salvar notificações.' })
    } finally {
      setSalvando(false)
    }
  }

  async function handleSalvarPrivacidade(e) {
    e.preventDefault()
    try {
      setSalvando(true)
      await atualizarPrivacidade(privacidade)
      mostrarMensagem('Preferências de privacidade atualizadas!')
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
        <span className="gendaz-kicker">Configurações</span>
        <h1>Meu perfil e preferências</h1>
        <p>Nome, e-mail, notificações, privacidade e saída da sessão.</p>
      </header>

      {mensagem && <div className="gendaz-mensagem gendaz-mensagem--sucesso">{mensagem}</div>}
      {erros.geral && <div className="gendaz-auth__error">{erros.geral}</div>}

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><UserRound size={18} /><h2>Meu perfil</h2></div>
          <form className="gendaz-form" onSubmit={handleSalvarPerfil}>
            <label>
              <span>Nome</span>
              <input type="text" value={formData.nome} onChange={(e) => setFormData({ ...formData, nome: e.target.value })} required />
              {erros.nome && <small className="gendaz-texto-erro">{erros.nome}</small>}
            </label>
            <label>
              <span>Telefone</span>
              <input type="tel" value={formData.telefone} onChange={(e) => setFormData({ ...formData, telefone: e.target.value })} />
            </label>
            <label>
              <span>E-mail</span>
              <input type="email" value={formData.email} onChange={(e) => setFormData({ ...formData, email: e.target.value })} required />
              {erros.email && <small className="gendaz-texto-erro">{erros.email}</small>}
            </label>
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={salvando}>
              {salvando ? <><Loader size={16} /> Salvando...</> : 'Salvar Alterações'}
            </button>
          </form>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><Bell size={18} /><h2>Notificações</h2></div>
          <form className="gendaz-form" onSubmit={handleSalvarNotificacoes}>
            <label className="gendaz-checkbox">
              <input type="checkbox" checked={notificacoes.email} onChange={(e) => setNotificacoes({ ...notificacoes, email: e.target.checked })} />
              <span>Receber notificações por e-mail</span>
            </label>
            <label className="gendaz-checkbox">
              <input type="checkbox" checked={notificacoes.sms} onChange={(e) => setNotificacoes({ ...notificacoes, sms: e.target.checked })} />
              <span>Receber notificações por SMS</span>
            </label>
            <label className="gendaz-checkbox">
              <input type="checkbox" checked={notificacoes.push} onChange={(e) => setNotificacoes({ ...notificacoes, push: e.target.checked })} />
              <span>Receber notificações push</span>
            </label>
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={salvando}>
              {salvando ? <><Loader size={16} /> Salvando...</> : 'Salvar Preferências'}
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
              <span>Compartilhar histórico com a IA para recomendações</span>
            </label>
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={salvando}>
              {salvando ? <><Loader size={16} /> Salvando...</> : 'Salvar Privacidade'}
            </button>
          </form>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><LogOut size={18} /><h2>Sair da conta</h2></div>
          <p>Sua sessão pode permanecer salva por longo período no mesmo dispositivo.</p>
          <button className="gendaz-btn gendaz-btn--danger" type="button" onClick={handleLogout}>
            <LogOut size={16} /> Sair
          </button>
        </article>
      </div>
    </section>
  )
}
