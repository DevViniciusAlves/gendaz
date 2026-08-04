import { useContext, useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Loader, Ticket, BellRing, CheckCircle2, Clock3, Percent, Tags } from 'lucide-react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'

function moeda(valor) {
  const numero = Number(valor || 0)
  return numero.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export default function Promocoes() {
  const { slug } = useParams()
  const { beneficios, carregarBeneficios, marcarPromocaoLida } = useContext(ClienteGendazContext)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [aba, setAba] = useState('DISPONIVEIS')

  useEffect(() => {
    let ativo = true
    const carregar = async () => {
      try {
        setCarregando(true)
        await carregarBeneficios()
      } catch (err) {
        if (!ativo) return
        setErro(err.response?.data?.mensagem || err.message || 'Erro ao carregar promoções.')
      } finally {
        if (ativo) setCarregando(false)
      }
    }
    carregar()
    return () => {
      ativo = false
    }
  }, [carregarBeneficios])

  const promocoes = beneficios?.promocoes || []
  const usadas = beneficios?.cupons || []
  const notificacoes = beneficios?.notificacoes || []

  const disponiveis = useMemo(() => promocoes.filter((item) => !item.jaUsou && item.valida), [promocoes])
  const jaUsadas = useMemo(() => usadas, [usadas])

  async function marcarLidas() {
    try {
      await Promise.all(notificacoes.map((item) => marcarPromocaoLida(item.promocaoId)))
    } catch (err) {
      setErro(err.response?.data?.mensagem || err.message || 'Nao foi possivel atualizar a leitura.')
    }
  }

  if (carregando) {
    return (
      <section className="gendaz-page">
        <div className="gendaz-loading"><Loader size={20} /> Carregando promoções...</div>
      </section>
    )
  }

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Promoções</span>
        <h1>Cupons e ofertas do seu atendimento</h1>
        <p>Aqui ficam os cupons próprios do Meu Gendaz. Nada vem da área interna.</p>
      </header>

      {erro && <div className="gendaz-erro">{erro}</div>}

      {notificacoes.length > 0 && (
        <div className="gendaz-card" style={{ display: 'grid', gap: 12, marginBottom: 16 }}>
          <div className="gendaz-panel__head"><BellRing size={18} /><h2>Novos cupons</h2></div>
          <p>Você tem {notificacoes.length} promoção(ões) nova(s) disponível(is).</p>
          <button className="gendaz-btn gendaz-btn--primary" type="button" onClick={marcarLidas}>
            Marcar como lidas
          </button>
        </div>
      )}

      <div className="gendaz-tabs">
        <button className={aba === 'DISPONIVEIS' ? 'is-active' : ''} onClick={() => setAba('DISPONIVEIS')}>Disponíveis ({disponiveis.length})</button>
        <button className={aba === 'USADAS' ? 'is-active' : ''} onClick={() => setAba('USADAS')}>Já usados ({jaUsadas.length})</button>
      </div>

      {aba === 'DISPONIVEIS' && (
        <div className="gendaz-grid gendaz-grid--two">
          {disponiveis.length > 0 ? disponiveis.map((cupom) => (
            <article className="gendaz-card" key={cupom.id}>
              <div className="gendaz-panel__head">
                <Tags size={18} />
                <h2>{cupom.codigo}</h2>
              </div>
              <p>{cupom.descricao}</p>
              <strong>{cupom.tipo === 'PERCENTUAL' ? `${cupom.valor}% de desconto` : moeda(cupom.valor)}</strong>
              <small><Clock3 size={14} /> Válido até {new Date(cupom.dataFim).toLocaleDateString('pt-BR')}</small>
              <small><Percent size={14} /> {cupom.aplicarTodosServicos ? 'Aplicável a todos os serviços' : 'Aplicável a serviços selecionados'}</small>
              <button
                className="gendaz-btn gendaz-btn--primary"
                type="button"
                onClick={() => { window.location.href = `/meu-gendaz/${slug}/agenda?cupom=${encodeURIComponent(cupom.codigo)}` }}
              >
                Usar cupom
              </button>
            </article>
          )) : (
            <p className="gendaz-vazio">Nenhuma promoção disponível no momento.</p>
          )}
        </div>
      )}

      {aba === 'USADAS' && (
        <div className="gendaz-grid">
          {jaUsadas.length > 0 ? jaUsadas.map((cupom) => (
            <article className="gendaz-card" key={`${cupom.cupomCodigo}-${cupom.dataUso}`}>
              <div className="gendaz-panel__head">
                <Ticket size={18} />
                <h2>{cupom.cupomCodigo}</h2>
              </div>
              <p>{cupom.cupomDescricao}</p>
              <strong>Desconto usado: {moeda(cupom.valorDesconto)}</strong>
              <small><CheckCircle2 size={14} /> {new Date(cupom.dataUso).toLocaleDateString('pt-BR')}</small>
            </article>
          )) : (
            <p className="gendaz-vazio">Você ainda não usou nenhum cupom.</p>
          )}
        </div>
      )}
    </section>
  )
}
