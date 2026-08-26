import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Eye, EyeOff } from 'lucide-react'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import { appApi } from '../api/appApi.js'
import logoSvg from '../assets/logos/gendaz-logo-branco.png'

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
  const [falhaCarregamento, setFalhaCarregamento] = useState(false)
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [mostrarConfirmacao, setMostrarConfirmacao] = useState(false)

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
        setFalhaCarregamento(true)
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
      <main className="login-screen-v2 invite-screen-v2">
        <section className="login-card-v2 invite-card-v2">
          <div className="login-card-header">
            <div className="login-brand-v2">
              <img src={logoSvg} alt="gendaz" className="login-brand-logo" />
            </div>
            <Link to="/login" className="login-back-btn">Voltar ao login</Link>
          </div>
          <p className="login-helper-v2">Carregando convite...</p>
        </section>
      </main>
    )
  }

  return (
    <main className="login-screen-v2 invite-screen-v2">
      <section className="login-card-v2 invite-card-v2">
        <div className="login-card-header">
          <div className="login-brand-v2">
            <img src={logoSvg} alt="gendaz" className="login-brand-logo" />
          </div>
          <Link to="/login" className="login-back-btn">Voltar ao login</Link>
        </div>

        <span className="section-kicker">Convite</span>
        <h1>Voce foi convidado para acessar a conta {dados.empresaNome || 'Gendaz'}</h1>

        {falhaCarregamento ? (
          <>
            <p className="form-error">{erro || 'Nao foi possivel carregar o convite.'}</p>
            <p className="login-helper-v2">Tente abrir novamente o link mais recente recebido por e-mail.</p>
          </>
        ) : !dados.valido ? (
          <>
            <p className="form-error">Convite expirado. Peça para o dono enviar um novo convite.</p>
            <p className="login-helper-v2">
              <Link to="/login" className="inline-link">Voltar para o login</Link>
            </p>
          </>
        ) : acao === 'senha' ? (
          <>
            <p className="login-helper-v2">Confirme seus dados e crie sua senha para entrar no SaaS.</p>
            <form onSubmit={criarSenha} className="login-form-v2">
              <Input label="Nome" value={dados.nome} readOnly />
              <Input label="E-mail" type="email" value={dados.email} readOnly />
              <div className="login-field-v2">
                <label className="login-label-v2">Senha</label>
                <div className="login-input-wrap-v2">
                  <input
                    type={mostrarSenha ? 'text' : 'password'}
                    value={senha}
                    onChange={(e) => setSenha(e.target.value)}
                    required
                  />
                  <button
                    type="button"
                    className="login-eye-btn"
                    aria-label={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}
                    onClick={() => setMostrarSenha((atual) => !atual)}
                  >
                    {mostrarSenha ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>
              <div className="login-field-v2">
                <label className="login-label-v2">Confirmar senha</label>
                <div className="login-input-wrap-v2">
                  <input
                    type={mostrarConfirmacao ? 'text' : 'password'}
                    value={confirmarSenha}
                    onChange={(e) => setConfirmarSenha(e.target.value)}
                    required
                  />
                  <button
                    type="button"
                    className="login-eye-btn"
                    aria-label={mostrarConfirmacao ? 'Ocultar senha' : 'Mostrar senha'}
                    onClick={() => setMostrarConfirmacao((atual) => !atual)}
                  >
                    {mostrarConfirmacao ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>
              {erro && <p className="form-error">{erro}</p>}
              {mensagem && <p className="success-text">{mensagem}</p>}
              <div className="invite-actions-v2">
                <Button type="submit" loading={carregando} loadingText="Criando...">Criar senha</Button>
                <Button variant="secondary" type="button" onClick={recusar} loading={carregando} loadingText="Processando...">Recusar convite</Button>
              </div>
            </form>
          </>
        ) : (
          <>
            <p className="login-helper-v2">Deseja aceitar o convite para acessar essa conta?</p>
            <div className="invite-preview-v2">
              <Input label="Nome" value={dados.nome} readOnly />
              <Input label="E-mail" type="email" value={dados.email} readOnly />
            </div>
            {erro && <p className="form-error">{erro}</p>}
            {mensagem && <p className="success-text">{mensagem}</p>}
            <div className="invite-choice-grid-v2">
              <Button className="invite-choice-btn-v2" type="button" onClick={() => navigate(`/convite?token=${encodeURIComponent(token)}&acao=senha`, { replace: true })}>Sim, aceitar</Button>
              <Button className="invite-choice-btn-v2" variant="secondary" type="button" onClick={recusar} loading={carregando} loadingText="Recusando...">Nao, recusar</Button>
            </div>
          </>
        )}

        <p className="login-helper-v2">
          <Link to="/login" className="inline-link">Voltar para o login</Link>
        </p>
      </section>
    </main>
  )
}
