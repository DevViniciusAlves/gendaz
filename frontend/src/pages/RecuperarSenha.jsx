import { useState } from 'react'
import { Link } from 'react-router-dom'
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
    <main className="login-screen app-dark-screen recovery-password-screen">
      <section className="login-panel recovery-password-panel">
        <span className="section-kicker">Recuperação de senha</span>
        <h1>Esqueci minha senha</h1>
        <form onSubmit={enviar}>
          <Input label="E-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          {erro && <p className="form-error">{erro}</p>}
          {mensagem && <p className="success-text">{mensagem}</p>}
          <Button type="submit" className="recovery-submit-btn" disabled={carregando}>{carregando ? 'Enviando...' : 'Enviar link de recuperação'}</Button>
        </form>
        <p className="login-helper">
          Lembrou a senha? <Link to="/login" className="inline-link">Voltar para o login</Link>
        </p>
      </section>
    </main>
  )
}
