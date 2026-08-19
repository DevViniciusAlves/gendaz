import { LogOut, RotateCcw } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { appApi } from '../api/appApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import logoGendaz from '../assets/logos/gendaz-logo-branco.png'

export default function ContaEncerrada() {
  const navigate = useNavigate()
  const { usuario, logout } = useAuth()
  const [carregando, setCarregando] = useState(false)
  const [mensagem, setMensagem] = useState('')
  const [tipoMensagem, setTipoMensagem] = useState('')

  async function reativarConta() {
    if (carregando) return
    setMensagem('')
    setTipoMensagem('')
    setCarregando(true)
    try {
      const resultado = await appApi.reativarConta()
      await logout('manual')
      navigate('/login', {
        replace: true,
        state: { mensagem: resultado?.mensagem || 'Sua conta foi reativada. Faca login para continuar.' },
      })
    } catch (error) {
      setTipoMensagem('error')
      setMensagem(error?.response?.data?.mensagem || 'Não foi possível reativar a conta. Tente novamente.')
    } finally {
      setCarregando(false)
    }
  }

  function sairDaConta() {
    logout('manual')
    navigate('/login', { replace: true })
  }

  if (!usuario) {
    return null
  }

  return (
    <main className="login-screen-v2 conta-inativa-screen">
      <section className="login-card-v2 conta-inativa-card">
        <div className="conta-inativa-brand">
          <img src={logoGendaz} alt="gendaz" className="login-brand-logo" />
        </div>

        <span className="conta-inativa-badge">Conta encerrada</span>
        <h1 className="conta-inativa-title">Conta encerrada</h1>
        <p className="conta-inativa-copy">
          Você encerrou esta conta anteriormente. Se quiser voltar a utilizar o gendaz, reative sua conta.
        </p>

        <div className="inactive-account-cards">
          <button type="button" className="inactive-account-card orange" onClick={reativarConta} disabled={carregando}>
            <RotateCcw size={18} className={`inactive-account-icon ${carregando ? 'animate-spin' : ''}`} />
            <span className="inactive-account-label">{carregando ? 'Reativando conta...' : 'Reativar conta'}</span>
          </button>

          <button type="button" className="inactive-account-card white" onClick={sairDaConta}>
            <LogOut size={18} className="inactive-account-icon" />
            <span className="inactive-account-label">Sair da conta</span>
          </button>
        </div>

        {mensagem && <div className={`conta-inativa-feedback ${tipoMensagem}`}>{mensagem}</div>}
      </section>
    </main>
  )
}