import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Eye, EyeOff, RefreshCw, Star, User, Mail, Lock, Phone, FileText, Check } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext.jsx'
import logoSvg from '../assets/logos/gendaz-logo-green.png'

const PLANOS_INFO = {
  BASICO: {
    nome: 'Basico',
    preco: 'R$ 39,00/mes',
    possuiTesteGratis: true,
    subtitulo: 'Seu teste gratis de 7 dias comeca apos o cadastro.',
  },
  PRO: {
    nome: 'Pro',
    preco: 'R$ 89,00/mes',
    possuiTesteGratis: false,
    subtitulo: 'Plano profissional liberado apos a aprovacao do pagamento.',
  },
}

function normalizarPlano(planoParam) {
  const texto = String(planoParam || '').toUpperCase()
  if (texto.includes('PRO')) return 'PRO'
  return 'BASICO'
}

function somenteDigitos(valor) {
  return String(valor || '').replace(/\D/g, '')
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

function formatarCpf(valor) {
  const digitos = somenteDigitos(valor).slice(0, 11)
  return digitos
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/^(\d{3})\.(\d{3})\.(\d{3})(\d)/, '$1.$2.$3-$4')
}

function formatarCnpj(valor) {
  const digitos = somenteDigitos(valor).slice(0, 14)
  return digitos
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/^(\d{2})\.(\d{3})\.(\d{3})(\d)/, '$1.$2.$3/$4')
    .replace(/^(\d{2})\.(\d{3})\.(\d{3})\/(\d{4})(\d)/, '$1.$2.$3/$4-$5')
}

function formatarDocumento(tipo, valor) {
  return tipo === 'CPF' ? formatarCpf(valor) : formatarCnpj(valor)
}

function validarCpf(cpf) {
  if (cpf.length !== 11) return false
  if (/^(\d)\1{10}$/.test(cpf)) return false

  let soma = 0
  for (let i = 0; i < 9; i += 1) {
    soma += Number(cpf[i]) * (10 - i)
  }
  let resto = soma % 11
  const digito1 = resto < 2 ? 0 : 11 - resto
  if (digito1 !== Number(cpf[9])) return false

  soma = 0
  for (let i = 0; i < 10; i += 1) {
    soma += Number(cpf[i]) * (11 - i)
  }
  resto = soma % 11
  const digito2 = resto < 2 ? 0 : 11 - resto
  return digito2 === Number(cpf[10])
}

function validarCnpj(cnpj) {
  if (cnpj.length !== 14) return false
  if (/^(\d)\1{13}$/.test(cnpj)) return false

  const pesos1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
  const pesos2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]

  let soma = pesos1.reduce((total, peso, index) => total + Number(cnpj[index]) * peso, 0)
  let resto = soma % 11
  const digito1 = resto < 2 ? 0 : 11 - resto
  if (digito1 !== Number(cnpj[12])) return false

  soma = pesos2.reduce((total, peso, index) => total + Number(cnpj[index]) * peso, 0)
  resto = soma % 11
  const digito2 = resto < 2 ? 0 : 11 - resto
  return digito2 === Number(cnpj[13])
}

function documentoValido(tipo, valor) {
  const documento = somenteDigitos(valor)
  if (tipo === 'CPF') return validarCpf(documento)
  if (tipo === 'CNPJ') return validarCnpj(documento)
  return false
}

function mensagemErroCadastro(error) {
  const data = error.response?.data
  if (data?.mensagem) return data.mensagem
  if (data?.campos) return Object.values(data.campos)[0]
  if (error.code === 'ECONNABORTED') return 'A criacao demorou mais que o esperado. Tente novamente em alguns instantes.'
  if (!error.response) return 'Nao foi possivel conectar com a API. Tente novamente em instantes.'
  return 'Nao foi possivel criar a conta.'
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
    documentoTipo: 'CNPJ',
    documentoNumero: '',
    senha: '',
    confirmarSenha: '',
    aceiteTermos: false,
  })
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [plano, setPlano] = useState(() => planoInicial)
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [mostrarConfirmarSenha, setMostrarConfirmarSenha] = useState(false)

  const planoAtual = PLANOS_INFO[plano]

  function set(campo, valor) {
    setForm((prev) => ({ ...prev, [campo]: valor }))
  }

  function handleDocumentoChange(valor) {
    const digitado = somenteDigitos(valor)
    set('documentoNumero', formatarDocumento(form.documentoTipo, digitado))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (carregando) return
    setErro('')

    const nomeEmpresa = normalizarTexto(form.nomeEmpresa)
    const nomeProprietario = normalizarTexto(form.nomeProprietario)
    const email = String(form.email || '').trim().toLowerCase()
    const telefone = somenteDigitos(form.telefone)
    const documento = somenteDigitos(form.documentoNumero)

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
    if (telefone.length < 10 || telefone.length > 15) {
      setErro('Telefone deve ter entre 10 e 15 digitos.')
      return
    }
    if (!documentoValido(form.documentoTipo, documento)) {
      setErro(form.documentoTipo === 'CPF' ? 'CPF invalido.' : 'CNPJ invalido.')
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
      setErro('As senhas nao coincidem.')
      return
    }

    setCarregando(true)
    try {
      const resultado = await criarConta({
        nomeEmpresa,
        nomeProprietario,
        email,
        telefone,
        documentoTipo: form.documentoTipo,
        documentoNumero: documento,
        senha: form.senha,
        confirmarSenha: form.confirmarSenha,
        plano,
        aceiteTermos: form.aceiteTermos,
      })
      if (resultado?.pendingPayment) {
        navigate('/pagamento-pendente')
        return
      }
      navigate('/sistema/dashboard')
    } catch (error) {
      setErro(mensagemErroCadastro(error))
    } finally {
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
            <label className="login-label-v2">Telefone / WhatsApp</label>
            <div className="login-input-wrap-v2">
              <Phone size={16} className="login-input-icon-left" />
              <input
                type="tel"
                inputMode="numeric"
                placeholder="(65) 99999-9999"
                maxLength={15}
                value={form.telefone}
                onChange={(e) => set('telefone', somenteDigitos(e.target.value))}
                required
              />
            </div>
          </div>

          <div className="cc-doc-row-v2 cc-full-v2">
            <div className="login-field-v2 cc-doc-select-v2">
              <label className="login-label-v2">Tipo</label>
              <div className="login-input-wrap-v2">
                <select
                  value={form.documentoTipo}
                  onChange={(e) => set('documentoTipo', e.target.value)}
                  required
                >
                  <option value="CNPJ">CNPJ</option>
                  <option value="CPF">CPF</option>
                </select>
              </div>
            </div>
            <div className="login-field-v2 cc-doc-input-v2">
              <label className="login-label-v2">{form.documentoTipo === 'CPF' ? 'CPF' : 'CNPJ'}</label>
              <div className="login-input-wrap-v2">
                <input
                  type="text"
                  inputMode="numeric"
                  placeholder={form.documentoTipo === 'CPF' ? '000.000.000-00' : '00.000.000/0000-00'}
                  maxLength={form.documentoTipo === 'CPF' ? 14 : 18}
                  value={form.documentoNumero}
                  onChange={(e) => handleDocumentoChange(e.target.value)}
                  required
                />
              </div>
            </div>
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
            {carregando ? 'Criando conta...' : 'Criar conta'}
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
