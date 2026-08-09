import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import { appApi } from '../api/appApi.js'

export default function Convite() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = useMemo(() => params.get('token') || '', [params])
  const acao = useMemo(() => String(params.get('acao') || 'confirmar').toLowerCase(), [params])
  const [dados, setDados] = useState({ nome: '', email: '', empresaNome: '', valido: false })
  const [senha, setSenha] = useState('')
  const [confirmarSenha, setConfirmarSenha] = useState('')
  const [mensagem, setMensagem] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [carregandoDados, setCarregandoDados] = useState(true)

  useEffect(() => {
    let ativo = true
    async function carregar() {
      if (!token) {
        setErro('Convite invalido.')
        setCarregandoDados(false)
        return
      }
      try {
        const resposta = await appApi.obterConviteUsuario(token)
        if (!ativo) return
        setDados({
          nome: resposta?.nome || '',
          email: resposta?.email || '',
          empresaNome: resposta?.empresaNome || '',
          valido: Boolean(resposta?.valido),
        })
      } catch (error) {
        if (!ativo) return
        setErro(error.response?.data?.mensagem || 'Nao foi possivel carregar o convite.')
      } finally {
        if (ativo) setCarregandoDados(false)
      }
    }
    carregar()
    return () => {
      ativo = false
    }
  }, [token])

  async function recusar() {
    setErro('')
    setMensagem('')
    setCarregando(true)
    try {
      const resposta = await appApi.recusarConviteUsuario({ token })
      setMensagem(resposta?.mensagem || 'Convite recusado com sucesso.')
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel recusar o convite.')
    } finally {
      setCarregando(false)
    }
  }

  async function criarSenha(event) {
    event.preventDefault()
    setErro('')
    setMensagem('')
    if (!dados.valido) {
      setErro('Convite invalido ou expirado.')
      return
    }
    if (senha !== confirmarSenha) {
      setErro('As senhas nao coincidem.')
      return
    }
    setCarregando(true)
    try {
      await appApi.aceitarConviteUsuario({
        token,
        email: dados.email,
        nome: dados.nome,
        senha,
      })
      navigate('/login', {
        replace: true,
        state: { mensagem: 'Conta criada com sucesso. Agora entre com seu e-mail e senha.' },
      })
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel criar a senha.')
    } finally {
      setCarregando(false)
    }
  }

  if (carregandoDados) {
    return (
      <main className="login-screen app-dark-screen reset-password-screen">
        <section className="login-panel reset-password-panel">
          <p className="login-helper">Carregando convite...</p>
        </section>
      </main>
    )
  }

  return (
    <main className="login-screen app-dark-screen reset-password-screen">
      <section className="login-panel reset-password-panel">
        <span className="section-kicker">Convite</span>
        <h1>Voce foi convidado para acessar a conta {dados.empresaNome || 'Gendaz'}</h1>

        {!dados.valido ? (
          <>
            <p className="form-error">Convite expirado. Peça para o dono reenviar um novo convite.</p>
            <p className="login-helper">
              <Link to="/login" className="inline-link">Voltar para o login</Link>
            </p>
          </>
        ) : acao === 'senha' ? (
          <>
            <p className="login-helper">Confirme seus dados e crie sua senha para entrar no SaaS.</p>
            <form onSubmit={criarSenha}>
              <Input label="Nome" value={dados.nome} readOnly />
              <Input label="E-mail" type="email" value={dados.email} readOnly />
              <Input label="Senha" type="password" value={senha} onChange={(e) => setSenha(e.target.value)} required />
              <Input label="Confirmar senha" type="password" value={confirmarSenha} onChange={(e) => setConfirmarSenha(e.target.value)} required />
              {erro && <p className="form-error">{erro}</p>}
              {mensagem && <p className="success-text">{mensagem}</p>}
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                <Button type="submit" disabled={carregando}>{carregando ? 'Criando...' : 'Criar senha'}</Button>
                <Button variant="secondary" type="button" onClick={recusar} disabled={carregando}>{carregando ? 'Processando...' : 'Recusar convite'}</Button>
              </div>
            </form>
          </>
        ) : (
          <>
            <p className="login-helper">Deseja aceitar o convite para acessar essa conta?</p>
            <Input label="Nome" value={dados.nome} readOnly />
            <Input label="E-mail" type="email" value={dados.email} readOnly />
            {erro && <p className="form-error">{erro}</p>}
            {mensagem && <p className="success-text">{mensagem}</p>}
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Button type="button" onClick={() => navigate(`/convite?token=${encodeURIComponent(token)}&acao=senha`, { replace: true })}>Sim, aceitar</Button>
              <Button variant="secondary" type="button" onClick={recusar} disabled={carregando}>{carregando ? 'Recusando...' : 'Nao, recusar'}</Button>
            </div>
          </>
        )}

        <p className="login-helper">
          <Link to="/login" className="inline-link">Voltar para o login</Link>
        </p>
      </section>
    </main>
  )
}
