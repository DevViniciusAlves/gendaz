import { useContext, useState, useEffect } from 'react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import { BadgePercent, Ticket, Gift, Coins, Users, Copy, Check, Loader } from 'lucide-react'

const proximosBeneficios = [
  { icon: Gift, titulo: 'Programa de fidelidade', descrição: 'Futuro módulo de pontos e recompensas.' },
  { icon: Coins, titulo: 'Cashback', descrição: 'Recurso futuro para valor de volta.' },
  { icon: Users, titulo: 'Indique um amigo', descrição: 'Área pronta para campanhas de indicação.' },
]

export default function Beneficios() {
  const { beneficios, carregarBeneficios, usarCupom } = useContext(ClienteGendazContext)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [copiado, setCopiado] = useState(null)
  const [cupomEmUso, setCupomEmUso] = useState(null)

  useEffect(() => {
    let ativo = true
    const carregar = async () => {
      try {
        setCarregando(true)
        await carregarBeneficios({ usarCacheRecente: true })
      } catch (err) {
        if (ativo) setErro(err.response?.data?.mensagem || err.message || 'Erro ao carregar benefícios.')
      } finally {
        if (ativo) setCarregando(false)
      }
    }
    carregar()
    return () => {
      ativo = false
    }
  }, [carregarBeneficios])

  async function handleUsarCupom(cupomCodigo) {
    if (cupomEmUso) return
    setCupomEmUso(cupomCodigo)
    try {
      await usarCupom(cupomCodigo)
    } catch (err) {
      alert(err.response?.data?.mensagem || err.message || 'Erro ao usar cupom.')
    } finally {
      setCupomEmUso(null)
    }
  }

  function handleCopiar(codigo) {
    navigator.clipboard.writeText(codigo)
    setCopiado(codigo)
    setTimeout(() => setCopiado(null), 2000)
  }

  if (carregando) {
    return (
      <section className="gendaz-page">
        <div className="gendaz-loading"><Loader size={20} /> Carregando benefícios...</div>
      </section>
    )
  }

  if (erro) {
    return (
      <section className="gendaz-page">
        <div className="gendaz-erro">{erro}</div>
      </section>
    )
  }

  const promos = beneficios?.promocoes || []
  const cupons = beneficios?.cupons || []

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Benefícios</span>
        <h1>Promoções e cupons</h1>
        <p>Área de fidelização com promoções do estabelecimento.</p>
      </header>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><BadgePercent size={18} /><h2>Promoções disponíveis</h2></div>
          {promos.length > 0 ? (
            <div className="gendaz-stack">
              {promos.map((item) => (
                <div key={item.id} className="gendaz-mini-card">
                  <div className="gendaz-mini-card__header">
                    <strong>{item.titulo}</strong>
                    <span className="gendaz-desconto">{item.desconto}% OFF</span>
                  </div>
                  <span>{item.descrição}</span>
                  {item.cupom && <small>Cupom: <strong>{item.cupom}</strong></small>}
                  <small>Válido até {item.validade}</small>
                  {!item.elegivel && <small className="gendaz-texto-aviso">Você não é elegível</small>}
                  {item.ja_usado && <small className="gendaz-texto-ok"> Já utilizada</small>}
                </div>
              ))}
            </div>
          ) : (
            <p className="gendaz-vazio">Nenhuma promoção disponível no momento.</p>
          )}
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><Ticket size={18} /><h2>Cupons ativos</h2></div>
          {cupons.length > 0 ? (
            <div className="gendaz-stack">
              {cupons.map((item) => (
                <div key={item.id} className="gendaz-mini-card">
                  <div className="gendaz-mini-card__header">
                    <strong>{item.codigo}</strong>
                    <span>{item.desconto}% OFF</span>
                  </div>
                  <small>Válido até {item.validade}</small>
                  <div className="gendaz-mini-card__actions">
                    <button className="gendaz-btn gendaz-btn--small" onClick={() => handleCopiar(item.codigo)}>
                      {copiado === item.codigo ? <Check size={14} /> : <Copy size={14} />}
                      {copiado === item.codigo ? 'Copiado!' : 'Copiar'}
                    </button>
                    {item.ativo && (
                      <button className="gendaz-btn gendaz-btn--primary gendaz-btn--small" onClick={() => handleUsarCupom(item.codigo)} disabled={cupomEmUso === item.codigo}>
                        {cupomEmUso === item.codigo ? <><Loader className="spin" size={16} /> Usando...</> : 'Usar agora'}
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="gendaz-vazio">Nenhum cupom disponível no momento.</p>
          )}
        </article>
      </div>

      {proximosBeneficios.length > 0 && (
        <div className="gendaz-grid gendaz-grid--two">
          {proximosBeneficios.map(({ icon: Icon, titulo, descrição }) => (
            <article className="gendaz-card" key={titulo}>
              <Icon size={18} />
              <strong>{titulo}</strong>
              <span>{descrição}</span>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
