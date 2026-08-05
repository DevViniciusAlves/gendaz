import { useContext, useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { BellRing, CheckCircle2, Clock3, Loader, Percent, Ticket, Tags } from 'lucide-react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'

function moeda(valor) {
  const numero = Number(valor || 0)
  return numero.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data) {
  if (!data) return 'Sem data'
  return new Date(data).toLocaleDateString('pt-BR')
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
  const cupons = beneficios?.cupons || []
  const notificacoes = beneficios?.notificacoes || []

  const disponiveis = useMemo(() => promocoes.filter((item) => !item.jaUsou && item.valida), [promocoes])
  const usadas = useMemo(() => cupons, [cupons])

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
        <div className="gendaz-loading">
          <Loader size={20} />
          Carregando promoções...
        </div>
      </section>
    )
  }

  return (
    <section className="gendaz-page gendaz-promocoes">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Promoções</span>
        <h1>Cupons e ofertas do seu atendimento</h1>
        <p>Aqui ficam os cupons próprios do Meu Gendaz. Nada vem da área interna.</p>
      </header>

      {erro && <div className="gendaz-erro">{erro}</div>}

      {notificacoes.length > 0 && (
        <article className="gendaz-card gendaz-promocoes__alerta">
          <div className="gendaz-panel__head">
            <BellRing size={18} />
            <h2>Novos cupons disponíveis</h2>
          </div>
          <p>
            Você tem {notificacoes.length} nova{notificacoes.length > 1 ? 's' : ''} promoção(ões)
            disponível(is) para o seu atendimento.
          </p>
          <button className="gendaz-btn gendaz-btn--primary gendaz-btn--small" type="button" onClick={marcarLidas}>
            Marcar como lidas
          </button>
        </article>
      )}

      <div className="gendaz-promocoes__tabs" role="tablist" aria-label="Promoções">
        <button
          type="button"
          role="tab"
          aria-selected={aba === 'DISPONIVEIS'}
          className={aba === 'DISPONIVEIS' ? 'is-active' : ''}
          onClick={() => setAba('DISPONIVEIS')}
        >
          Disponíveis <span>({disponiveis.length})</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={aba === 'USADAS'}
          className={aba === 'USADAS' ? 'is-active' : ''}
          onClick={() => setAba('USADAS')}
        >
          Já usados <span>({usadas.length})</span>
        </button>
      </div>

      <div className="gendaz-promocoes__grade">
        {aba === 'DISPONIVEIS' ? (
          <article className="gendaz-panel gendaz-promocoes__painel">
            <div className="gendaz-panel__head">
              <Tags size={18} />
              <h2>Promoções disponíveis</h2>
            </div>

            {disponiveis.length > 0 ? (
              <div className="gendaz-stack">
                {disponiveis.map((cupom) => (
                  <div className="gendaz-mini-card gendaz-promocoes__item" key={cupom.id}>
                    <div className="gendaz-mini-card__header">
                      <strong>{cupom.codigo}</strong>
                      <span className="gendaz-desconto">
                        {cupom.tipo === 'PERCENTUAL' ? `${cupom.valor}% OFF` : moeda(cupom.valor)}
                      </span>
                    </div>
                    <span>{cupom.descricao}</span>
                    <small className="gendaz-promocoes__meta">
                      <Clock3 size={14} />
                      Válido até {formatarData(cupom.dataFim)}
                    </small>
                    <small className="gendaz-promocoes__meta">
                      <Percent size={14} />
                      {cupom.aplicarTodosServicos ? 'Aplicável a todos os serviços' : 'Aplicável a serviços selecionados'}
                    </small>
                    <div className="gendaz-mini-card__actions">
                      <button
                        className="gendaz-btn gendaz-btn--primary gendaz-btn--small"
                        type="button"
                        onClick={() => {
                          window.location.href = `/meu-gendaz/${slug}/agenda?cupom=${encodeURIComponent(cupom.codigo)}`
                        }}
                      >
                        Usar cupom
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="gendaz-vazio">Nenhuma promoção disponível no momento.</p>
            )}
          </article>
        ) : (
          <article className="gendaz-panel gendaz-promocoes__painel">
            <div className="gendaz-panel__head">
              <Ticket size={18} />
              <h2>Cupons usados</h2>
            </div>

            {usadas.length > 0 ? (
              <div className="gendaz-stack">
                {usadas.map((cupom) => (
                  <div className="gendaz-mini-card gendaz-promocoes__item" key={`${cupom.cupomCodigo}-${cupom.dataUso}`}>
                    <div className="gendaz-mini-card__header">
                      <strong>{cupom.cupomCodigo}</strong>
                      <span className="gendaz-desconto gendaz-desconto--secundario">USADO</span>
                    </div>
                    <span>{cupom.cupomDescricao}</span>
                    <small className="gendaz-promocoes__meta">
                      <CheckCircle2 size={14} />
                      Desconto usado: {moeda(cupom.valorDesconto)}
                    </small>
                    <small className="gendaz-promocoes__meta">
                      <Clock3 size={14} />
                      {formatarData(cupom.dataUso)}
                    </small>
                  </div>
                ))}
              </div>
            ) : (
              <p className="gendaz-vazio">Você ainda não usou nenhum cupom.</p>
            )}
          </article>
        )}
      </div>
    </section>
  )
}
