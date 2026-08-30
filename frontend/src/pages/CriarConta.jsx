import { useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Eye, EyeOff, Loader, RefreshCw, Star, User, Mail, Lock, FileText, Check } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext.jsx'
import { normalizarParaApi, obterExemploTelefone, validarTelefone } from '../utils/phoneUtils.js'
import { gerarUuid } from '../api/appApi.js'
import InternationalPhoneInput from '../components/InternationalPhoneInput.jsx'
import logoSvg from '../assets/logos/gendaz-logo-branco.png'

const PLANOS_INFO = {
  BASICO: {
    nome: 'Basico',
    preco: 'R$ 29,90/mês',
    possuiTesteGratis: true,
    subtitulo: 'Seu teste gratis de 7 dias comeca apos o cadastro.',
  },
  PRO: {
    nome: 'Pro',
    preco: 'R$ 79,90/mês',
    possuiTesteGratis: true,
    subtitulo: 'Seu teste gratis de 7 dias comeca apos o cadastro.',
  },
  PLUS: {
    nome: 'Plus',
    preco: 'R$ 109,90/mês',
    possuiTesteGratis: true,
    subtitulo: 'Seu teste gratis de 7 dias comeca apos o cadastro.',
  },
  ENTERPRISE: {
    nome: 'Enterprise',
    preco: 'R$ 149,90/mês',
    possuiTesteGratis: true,
    subtitulo: 'Seu teste gratis de 7 dias comeca apos o cadastro.',
  },
}

function normalizarPlano(planoParam) {
  const texto = String(planoParam || '').toUpperCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  if (texto === 'BASICO' || texto.includes('PLANO BASICO') || texto.includes('PLANO BÁSICO')) return 'BASICO'
  if (texto === 'PRO' || texto.includes('PLANO PRO')) return 'PRO'
  if (texto === 'PLUS' || texto.includes('PLANO PLUS')) return 'PLUS'
  if (texto === 'ENTERPRISE' || texto.includes('PLANO ENTERPRISE')) return 'ENTERPRISE'
  return texto
}

function normalizarTexto(valor) {
  return String(valor || '').trim().replace(/\s+/g, ' ')
}

function senhaForte(senha) {
  return /[a-z]/.test(senha)
    && /[A-Z]/.test(senha)
    && /\d/.test(senha)
    && /[^A-Za-z0-9]/.test(senha)
}

function mensagemErroCadastro(error) {
  const data = error.response?.data
  if (data?.mensagem) return data.mensagem
  if (data?.campos) return Object.values(data.campos)[0]
  if (error.code === 'ECONNABORTED') return 'A criacao demorou mais que o esperado. Tente novamente em alguns instantes.'
  if (!error.response) return 'Nao foi possivel conectar com a API. Tente novamente em instantes.'
  return 'Nao foi possivel criar a conta.'
}

function fingerprintFormulario({ nomeEmpresa, nomeProprietario, email, telefone, plano, aceiteTermos }) {
  return JSON.stringify([nomeEmpresa, nomeProprietario, email, telefone, plano, aceiteTermos])
}

function aguardar(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export default function CriarConta() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { criarConta } = useAuth()

  const planoInicial = normalizarPlano(params.get('plano'))

  const [form, setForm] = useState({
    nomeEmpresa: '',
    nomeProprietario: '',
    email: '',
    telefone: '',
    senha: '',
    confirmarSenha: '',
    aceiteTermos: false,
  })
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [plano, setPlano] = useState(() => planoInicial)
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [mostrarConfirmarSenha, setMostrarConfirmarSenha] = useState(false)

  const submitEmAndamentoRef = useRef(false)
  const idempotenciaRef = useRef(null)

  const planoAtual = PLANOS_INFO[plano]

  function set(campo, valor) {
    setForm((prev) => ({ ...prev, [campo]: valor }))
  }

  async function enviarCadastro({ nomeEmpresa, nomeProprietario, email, telefone, tentativas }) {
    const idempotencyKey = idempotenciaRef.current?.key

    try {
      const resultado = await criarConta({
        nomeEmpresa,
        nomeProprietario,
        email,
        telefone,
        senha: form.senha,
        confirmarSenha: form.confirmarSenha,
        plano,
        aceiteTermos: form.aceiteTermos,
      }, { idempotencyKey })

      idempotenciaRef.current = null

      if (resultado?.pendingPayment) {
        navigate('/pagamento-pendente')
        return
      }
      navigate('/sistema/dashboard')
    } catch (error) {
      const data = error.response?.data
      const codigo = data?.erro
      if (codigo === 'IDEMPOTENCY_IN_PROGRESS' && tentativas < 3) {
        setErro('Seu cadastro ainda esta sendo processado. Aguarde alguns instantes...')
        await aguardar(2500)
        return enviarCadastro({ nomeEmpresa, nomeProprietario, email, telefone, tentativas: tentativas + 1 })
      }
      setErro(mensagemErroCadastro(error))
      if (error.response) {
        idempotenciaRef.current = null
      }
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitEmAndamentoRef.current) return
    setErro('')

    const nomeEmpresa = normalizarTexto(form.nomeEmpresa)
    const nomeProprietario = normalizarTexto(form.nomeProprietario)
    const email = String(form.email || '').trim().toLowerCase()
    const telefone = normalizarParaApi(form.telefone)

    if (!telefone) {
      setErro('Telefone invalido. Confira o formato do pais selecionado.')
      return
    }

    if (!form.aceiteTermos) {
      setErro('Voce precisa aceitar os termos para continuar.')
      return
    }

    if (nomeEmpresa.length < 2 || nomeEmpresa.length > 100) {
      setErro('Nome da empresa deve ter entre 2 e 100 caracteres.')
      return
    }

    if (nomeProprietario.length < 2 || nomeProprietario.length > 80) {
      setErro('Nome do responsavel deve ter entre 2 e 80 caracteres.')
      return
    }

    if (email.length > 120) {
      setErro('E-mail deve ter no maximo 120 caracteres.')
      return
    }

    const telValidationError = validarTelefone(form.telefone)
    if (telValidationError) {
      setErro(telValidationError)
      return
    }

    if (form.senha.length < 8 || form.senha.length > 72) {
      setErro('A senha deve ter entre 8 e 72 caracteres.')
      return
    }

    if (!senhaForte(form.senha)) {
      setErro('A senha deve ter letra maiuscula, letra minuscula, numero e caractere especial.')
      return
    }

    if (form.senha !== form.confirmarSenha) {
      setErro('As senhas não coincidem.')
      return
    }

    const fingerprint = fingerprintFormulario({ nomeEmpresa, nomeProprietario, email, telefone, plano, aceiteTermos: form.aceiteTermos })
    if (idempotenciaRef.current && idempotenciaRef.current.fingerprint !== fingerprint) {
      idempotenciaRef.current = null
    }
    if (!idempotenciaRef.current) {
      idempotenciaRef.current = { key: gerarUuid(), fingerprint }
    }

    submitEmAndamentoRef.current = true
    setCarregando(true)

    try {
      await enviarCadastro({ nomeEmpresa, nomeProprietario, email, telefone, tentativas: 0 })
    } finally {
      submitEmAndamentoRef.current = false
      setCarregando(false)
    }
  }

  return (
    <main className="login-screen-v2">
      <section className="login-card-v2 criar-conta-card-v2">
        <div className="login-card-header">
          <div className="login-brand-v2">
            <img src={logoSvg} alt="gendaz" className="login-brand-logo" />
          </div>
          <Link to="/" className="login-back-btn">Voltar ao site</Link>
        </div>

        <h1 className="login-title-v2">Criar sua conta</h1>

        <form onSubmit={handleSubmit} className="login-form-v2 cc-form-grid-v2">
          <div className="cc-plan-badge-v2 cc-full-v2">
            <div className="cc-plan-badge-left">
              <strong>Plano {planoAtual.nome}</strong>
              <span>{planoAtual.preco}</span>
            </div>
            <button
              type="button"
              className="cc-plan-switch-v2"
              onClick={() => setPlano((c) => (c === 'PRO' ? 'BASICO' : 'PRO'))}
            >
              <RefreshCw size={13} /> Trocar
            </button>
            {planoAtual.possuiTesteGratis && (
              <span className="cc-plan-free-v2"><Star size={12} /> 7 dias gratis</span>
            )}
          </div>
          <p className="cc-plan-sub-v2 cc-full-v2"><Check size={13} /> {planoAtual.subtitulo}</p>

          <div className="login-field-v2">
            <label className="login-label-v2">Nome da empresa</label>
            <div className="login-input-wrap-v2">
              <FileText size={16} className="login-input-icon-left" />
              <input
                type="text"
                placeholder="Ex: Clinica Saude Plena"
                maxLength={100}
                value={form.nomeEmpresa}
                onChange={(e) => set('nomeEmpresa', e.target.value.replace(/[^\p{L}\s]/gu, ''))}
                required
              />
            </div>
          </div>

          <div className="login-field-v2">
            <label className="login-label-v2">Nome do responsavel</label>
            <div className="login-input-wrap-v2">
              <User size={16} className="login-input-icon-left" />
              <input
                type="text"
                placeholder="Seu nome completo"
                maxLength={80}
                value={form.nomeProprietario}
                onChange={(e) => set('nomeProprietario', e.target.value.replace(/[^\p{L}\s]/gu, ''))}
                required
              />
            </div>
          </div>

          <div className="login-field-v2">
            <label className="login-label-v2">E-mail</label>
            <div className="login-input-wrap-v2">
              <Mail size={16} className="login-input-icon-left" />
              <input
                type="email"
                placeholder="contato@empresa.com"
                maxLength={120}
                value={form.email}
                onChange={(e) => set('email', e.target.value)}
                required
              />
            </div>
          </div>

          <div className="login-field-v2">
            <InternationalPhoneInput
              label="Telefone"
              value={form.telefone}
              onChangeValue={(valor) => set('telefone', valor || '')}
              defaultCountry="BR"
              helper={form.telefone ? (validarTelefone(form.telefone) || ' Pronto para confirmar') : `Exemplo para o país selecionado: ${obterExemploTelefone('BR') || '+55 (65) 99336-0341'}`}
              required
            />
          </div>

          <div className="login-field-v2">
            <label className="login-label-v2">Senha</label>
            <div className="login-input-wrap-v2">
              <Lock size={16} className="login-input-icon-left" />
              <input
                type={mostrarSenha ? 'text' : 'password'}
                placeholder="Minimo 8 caracteres"
                maxLength={72}
                value={form.senha}
                onChange={(e) => set('senha', e.target.value)}
                required
              />
              <button
                type="button"
                className="login-eye-btn"
                aria-label={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}
                onClick={() => setMostrarSenha((c) => !c)}
              >
                {mostrarSenha ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            <small className="login-hint-v2">
              {form.senha.length >= 72 ? 'Limite atingido.' : 'Maiuscula, minuscula, numero e caractere especial.'}
              {' '}{form.senha.length}/72
            </small>
          </div>

          <div className="login-field-v2">
            <label className="login-label-v2">Confirmar senha</label>
            <div className="login-input-wrap-v2">
              <Lock size={16} className="login-input-icon-left" />
              <input
                type={mostrarConfirmarSenha ? 'text' : 'password'}
                placeholder="Repita a senha"
                maxLength={72}
                value={form.confirmarSenha}
                onChange={(e) => set('confirmarSenha', e.target.value)}
                required
              />
              <button
                type="button"
                className="login-eye-btn"
                aria-label={mostrarConfirmarSenha ? 'Ocultar senha' : 'Mostrar senha'}
                onClick={() => setMostrarConfirmarSenha((c) => !c)}
              >
                {mostrarConfirmarSenha ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            <small className="login-hint-v2">
              {form.confirmarSenha.length >= 72 ? 'Limite atingido.' : 'Repita a mesma senha.'}
              {' '}{form.confirmarSenha.length}/72
            </small>
          </div>

          <label className="cc-termos-v2 cc-full-v2">
            <input
              type="checkbox"
              checked={form.aceiteTermos}
              onChange={(e) => set('aceiteTermos', e.target.checked)}
            />
            <span>
              Li e aceito os <Link to="/termos-de-uso" className="login-link-v2">Termos de Uso</Link> e a{' '}
              <Link to="/politica-de-privacidade" className="login-link-v2">Politica de Privacidade</Link>
            </span>
          </label>

          {erro && <p className="login-error-v2 cc-full-v2">{erro}</p>}

          <button type="submit" className="login-submit-v2 cc-submit-v2 cc-full-v2" disabled={carregando}>
            {carregando ? <><Loader className="spin" size={16} /> Criando conta...</> : 'Criar conta'}
          </button>
        </form>

        <div className="login-links-v2 cc-divider-v2">
          <p className="login-helper-v2">
            Ja tem uma conta? <Link to="/login" className="login-link-v2">Entrar</Link>
          </p>
          <p className="cc-footer-v2">&copy; 2026 gendaz &middot; Todos os direitos reservados</p>
        </div>
      </section>
    </main>
  )
}
