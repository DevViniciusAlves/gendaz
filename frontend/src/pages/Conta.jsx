import { UserRoundCog } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext.jsx'
import { appApi } from '../api/appApi.js'

export default function Conta() {
  const { usuario } = useAuth()
  const empresaId = usuario?.empresaId

  const [assinaturas, setAssinaturas] = useState([])
  useEffect(() => {
    if (!empresaId) return
    appApi.listarAssinaturas(empresaId).then((lista) => {
      setAssinaturas(Array.isArray(lista) ? lista : [])
    }).catch(() => setAssinaturas([]))
  }, [empresaId])

  function formatarTelefone(telefone) {
    if (!telefone) return 'Não informado'
    const digitos = String(telefone).replace(/\D/g, '')
    return digitos ? `${digitos.replace(/^(\d{2})(\d{5})(\d{4})$/, '($1) $2-$3')}` : 'Não informado'
  }

  const planosRenderizados = assinaturas
    .filter((a) => a?.status === 'ATIVA' || a?.status === 'TESTE')
    .map((a) => a?.planoNome || a?.plano || null)
    .filter((nome) => nome)

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Conta</span>
        <h1>Dados da conta</h1>
        <p>Visualize as informações da sua conta.</p>
      </div>
      <section className="panel account-card">
        <UserRoundCog size={28} />
        <h2>{usuario.nome}</h2>
        <p>{usuario.email}</p>
        <p>{formatarTelefone(usuario.telefone)}</p>
        <div className="account-planos">
          <span>Planos da conta</span>
          {planosRenderizados.length > 0 ? (
            planosRenderizados.map((nome, index) => (
              <span key={index} className="badge badge-sm">
                {nome.toUpperCase()}
              </span>
            ))
          ) : (
            <span>Nenhum plano ativo</span>
          )}
        </div>
      </section>
    </section>
  )
}
