import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Loader, Lock } from 'lucide-react'
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
    <main className="login-screen-v2 reset-password-screen">
      <section className="login-card-v2 reset-password-card-v2">
        <div className="login-card-header">
          <div className="login-brand-v2">
            <span className="section-kicker reset-kicker-v2">Nova senha</span>
          </div>
        </div>

        <h1 className="login-title-v2">Redefinir acesso</h1>

        <form onSubmit={enviar} className="login-form-v2">
          <input type="hidden" value={token} readOnly />
          <label className="login-field-v2">
            <span className="login-label-v2">Nova senha</span>
            <div className="login-input-wrap-v2">
              <Lock size={16} className="login-input-icon-left" />
              <input
                type="password"
                value={novaSenha}
                onChange={(e) => setNovaSenha(e.target.value)}
                placeholder="Digite sua nova senha"
                required
              />
            </div>
          </label>

          <label className="login-field-v2">
            <span className="login-label-v2">Confirmar nova senha</span>
            <div className="login-input-wrap-v2">
              <Lock size={16} className="login-input-icon-left" />
              <input
                type="password"
                value={confirmarNovaSenha}
                onChange={(e) => setConfirmarNovaSenha(e.target.value)}
                placeholder="Repita a nova senha"
                required
              />
            </div>
          </label>

          {erro && <p className="login-error-v2">{erro}</p>}
          {mensagem && <p className="login-success-v2 reset-success-v2">{mensagem}</p>}

          <button type="submit" className="login-submit-v2" disabled={carregando}>
            {carregando ? <><Loader className="spin" size={16} /> Salvando...</> : 'Redefinir senha'}
          </button>
        </form>

        <p className="login-links-v2 reset-links-v2">
          <Link to="/login" className="login-link-v2 reset-back-link-v2">Voltar para o login</Link>
        </p>
      </section>
    </main>
  )
}
