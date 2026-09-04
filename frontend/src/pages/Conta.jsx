import { UserRoundCog } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext.jsx'
import { appApi } from '../api/appApi.js'
import { useState, useEffect } from 'react'
import { exibirTelefone } from '../utils/phoneUtils.js'
import './Conta.css'

export default function Conta() {
  const { usuario } = useAuth()
  const empresaId = usuario?.empresaId

  const [empresa, setEmpresa] = useState(null)
  const [assinaturas, setAssinaturas] = useState([])

  useEffect(() => {
    if (!empresaId) return
    let ativo = true

    Promise.all([
      appApi.buscarEmpresa(empresaId).catch(() => null),
      appApi.listarAssinaturas(empresaId).catch(() => []),
    ]).then(([empresaDados, lista]) => {
      if (!ativo) return
      setEmpresa(empresaDados)
      setAssinaturas(Array.isArray(lista) ? lista : [])
    })

    return () => {
      ativo = false
    }
  }, [empresaId])

  const telefoneExibido = (empresa?.telefone && exibirTelefone(empresa.telefone)) || 'Não informado'

  const planos = assinaturas
    .filter((a) => a?.status === 'ATIVA' || a?.status === 'TESTE')
    .map((a) => a?.planoNome || a?.plano || null)
    .filter((nome) => nome)

  return (
    <section className="page conta-page">
      <div className="page-title">
        <span className="section-kicker">Conta</span>
        <h1>Dados da conta</h1>
        <p>Visualize as informações da sua conta.</p>
      </div>
      <section className="panel conta-card">
        <div className="conta-card-head">
          <UserRoundCog size={26} />
          <h2>Minha conta</h2>
        </div>
        <div className="conta-grid">
          <div className="conta-field">
            <span className="conta-label">Nome</span>
            <span className="conta-value">{usuario.nome}</span>
          </div>
          <div className="conta-field">
            <span className="conta-label">E-mail</span>
            <span className="conta-value">{usuario.email}</span>
          </div>
          <div className="conta-field">
            <span className="conta-label">Telefone</span>
            <span className="conta-value">{telefoneExibido}</span>
          </div>
          <div className="conta-field">
            <span className="conta-label">Planos da conta</span>
            {planos.length > 0 ? (
              <div className="conta-plans-list">
                {planos.map((nome, index) => (
                  <span key={index} className="conta-plan-badge">
                    {nome.toUpperCase()}
                  </span>
                ))}
              </div>
            ) : (
              <span className="conta-value">Nenhum plano ativo</span>
            )}
          </div>
        </div>
      </section>
    </section>
  )
}
