import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Mail } from 'lucide-react'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import { appApi } from '../api/appApi.js'

export default function RecuperarSenha() {
  const [email, setEmail] = useState('')
  const [mensagem, setMensagem] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  async function enviar(event) {
    event.preventDefault()
    setErro('')
    setMensagem('')
    setCarregando(true)
    try {
      await appApi.solicitarRecuperacaoSenha(email)
      setMensagem('Você receberá o e-mail para recuperação.')
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Não foi possível processar a solicitação.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <main className="login-screen-v2 recovery-password-screen">
      <section className="login-card-v2 recovery-password-card-v2">
        <div className="login-card-header">
          <div className="login-brand-v2">
            <span className="section-kicker recovery-kicker-v2">Recuperação de senha</span>
          </div>
        </div>

        <h1 className="login-title-v2">Esqueci minha senha</h1>

        <form onSubmit={enviar} className="login-form-v2">
          <label className="login-field-v2 recovery-field-v2">
            <span className="login-label-v2">E-mail</span>
            <div className="login-input-wrap-v2">
              <Mail size={16} className="login-input-icon-left" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="seu@email.com"
                required
              />
            </div>
          </label>

          {erro && <p className="login-error-v2">{erro}</p>}
          {mensagem && <p className="login-success-v2 recovery-success-v2">{mensagem}</p>}

          <Button type="submit" className="recovery-submit-btn-v2" disabled={carregando}>
            {carregando ? 'Enviando...' : 'Enviar link de recuperação'}
          </Button>
        </form>

        <p className="login-links-v2 recovery-links-v2">
          <Link to="/login" className="login-link-v2 recovery-back-link-v2">Lembrou a senha? Voltar para o login</Link>
        </p>
      </section>
    </main>
  )
}
