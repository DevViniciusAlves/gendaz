import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import { appApi } from '../api/appApi.js'

export default function RedefinirSenha() {
  const [params] = useSearchParams()
  const [token, setToken] = useState(params.get('token') || '')
  const [novaSenha, setNovaSenha] = useState('')
  const [confirmarNovaSenha, setConfirmarNovaSenha] = useState('')
  const [mensagem, setMensagem] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  async function enviar(event) {
    event.preventDefault()
    setErro('')
    setMensagem('')
    setCarregando(true)
    try {
      const resposta = await appApi.redefinirSenha(token, novaSenha, confirmarNovaSenha)
      setMensagem(resposta.mensagem)
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel redefinir a senha.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <main className="login-screen app-dark-screen reset-password-screen">
      <section className="login-panel reset-password-panel">
        <span className="section-kicker">Nova senha</span>
        <h1>Redefinir acesso</h1>
        <form onSubmit={enviar}>
          <input type="hidden" value={token} readOnly />
          <Input label="Nova senha" type="password" value={novaSenha} onChange={(e) => setNovaSenha(e.target.value)} required />
          <Input label="Confirmar nova senha" type="password" value={confirmarNovaSenha} onChange={(e) => setConfirmarNovaSenha(e.target.value)} required />
          {erro && <p className="form-error">{erro}</p>}
          {mensagem && <p className="success-text">{mensagem}</p>}
          <Button type="submit" disabled={carregando}>{carregando ? 'Salvando...' : 'Redefinir senha'}</Button>
        </form>
        <p className="login-helper">
          <Link to="/login" className="inline-link">Voltar para o login</Link>
        </p>
      </section>
    </main>
  )
}
