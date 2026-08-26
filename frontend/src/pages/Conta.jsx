import { Save, UserRoundCog } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { PLANOS } from '../services/localStore.js'

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function Conta() {
  const { usuario, atualizarUsuario, logout } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ nome: usuario.nome, email: usuario.email })
  const [senhaForm, setSenhaForm] = useState({ senhaAtual: '', novaSenha: '', confirmarNovaSenha: '' })
  const [salvo, setSalvo] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [senhaSalva, setSenhaSalva] = useState(false)
  const [erro, setErro] = useState('')
  const [erroSenha, setErroSenha] = useState('')
  const [carregandoSenha, setCarregandoSenha] = useState(false)

  async function salvar(event) {
    event.preventDefault()
    setErro('')
    const nome = form.nome.trim().replace(/\s+/g, ' ')
    const email = form.email.trim().toLowerCase()
    if (nome.length < 2 || nome.length > 80) {
      setErro('Nome deve ter entre 2 e 80 caracteres.')
      return
    }
    if (!emailRegex.test(email) || email.length > 120) {
      setErro('Informe um e-mail válido.')
      return
    }
    if (salvando) return
    setSalvando(true)
    try {
    const atualizado = await appApi.atualizarUsuario(usuario.id, {
      nome,
      email,
      perfil: usuario.perfil,
    })
    atualizarUsuario({ ...atualizado, plano: usuario.plano })
    setSalvo(true)
    } finally {
      setSalvando(false)
    }
  }

  async function trocarSenha(event) {
    event.preventDefault()
    setErroSenha('')
    setSenhaSalva(false)
    setCarregandoSenha(true)
    try {
      await appApi.trocarSenha(senhaForm.senhaAtual, senhaForm.novaSenha, senhaForm.confirmarNovaSenha)
      setSenhaForm({ senhaAtual: '', novaSenha: '', confirmarNovaSenha: '' })
      setSenhaSalva(true)
      logout()
      navigate('/login', { replace: true, state: { mensagem: 'Senha alterada com sucesso. Faça login novamente.' } })
    } catch (error) {
      setErroSenha(error.response?.data?.mensagem || 'Não foi possível trocar a senha.')
    } finally {
      setCarregandoSenha(false)
    }
  }

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Conta</span>
        <h1>Dados da conta</h1>
        <p>Altere nome, e-mail e senha de acesso da conta logada.</p>
      </div>
      <div className="settings-grid">
        <section className="panel account-card">
          <UserRoundCog size={28} />
          <h2>{usuario.nome}</h2>
          <p>{usuario.email}</p>
          <div className="account-badges">
            <StatusBadge status={usuario.perfil} />
            <StatusBadge status={PLANOS[usuario.plano]?.nome || usuario.plano} />
          </div>
        </section>
        <section className="panel">
          <h2>Editar acesso</h2>
          <form className="form-grid single" onSubmit={salvar}>
            <Input label="Nome" helper="Digite apenas letras." maxLength={80} value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value.replace(/[^\p{L}\s]/gu, '') })} />
            <Input label="E-mail" helper="Use um e-mail válido." type="email" maxLength={120} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            {erro && <p className="form-error">{erro}</p>}
            {salvo && <p className="success-text">Conta atualizada.</p>}
            <Button icon={Save} type="submit" loading={salvando} loadingText="Salvando...">Salvar alterações</Button>
          </form>
        </section>
        <section className="panel">
          <h2>Trocar senha</h2>
          <form className="form-grid single" onSubmit={trocarSenha}>
            <Input label="Senha atual" type="password" value={senhaForm.senhaAtual} onChange={(e) => setSenhaForm({ ...senhaForm, senhaAtual: e.target.value })} required />
            <Input label="Nova senha" type="password" value={senhaForm.novaSenha} onChange={(e) => setSenhaForm({ ...senhaForm, novaSenha: e.target.value })} required />
            <Input label="Confirmar nova senha" type="password" value={senhaForm.confirmarNovaSenha} onChange={(e) => setSenhaForm({ ...senhaForm, confirmarNovaSenha: e.target.value })} required />
            {erroSenha && <p className="form-error">{erroSenha}</p>}
            {senhaSalva && <p className="success-text">Senha alterada com sucesso.</p>}
            <Button type="submit" loading={carregandoSenha} loadingText="Salvando...">Trocar senha</Button>
          </form>
        </section>
      </div>
    </section>
  )
}
