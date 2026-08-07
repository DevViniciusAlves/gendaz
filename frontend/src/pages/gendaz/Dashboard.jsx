import { useContext, useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import { Calendar, Clock, Gift, MessageCircle, Plus, ChevronRight, Sparkles, BellRing, Wallet, LifeBuoy, Phone } from 'lucide-react'

export default function Dashboard() {
  const navigate = useNavigate()
  const { cliente, dashboard, agendamentos, carregarHistorico, carregando, erro } = useContext(ClienteGendazContext)
  const [ultimosAtendimentos, setUltimosAtendimentos] = useState([])
  const [carregandoAtendimentos, setCarregandoAtendimentos] = useState(false)

  useEffect(() => {
    const buscar = async () => {
      try {
        setCarregandoAtendimentos(true)
        const data = await carregarHistorico(1, 20)
        const lista = data?.agendamentos || data || []
        const finalizados = Array.isArray(lista)
          ? lista
              .filter((item) => String(item?.status || '').toUpperCase() === 'FINALIZADO')
              .sort((a, b) => {
                const dataA = a?.data ? new Date(`${a.data}T12:00:00`).getTime() : 0
                const dataB = b?.data ? new Date(`${b.data}T12:00:00`).getTime() : 0
                if (dataA !== dataB) return dataB - dataA
                const horaA = a?.horaInicio || a?.hora || ''
                const horaB = b?.horaInicio || b?.hora || ''
                return String(horaB).localeCompare(String(horaA))
              })
              .slice(0, 2)
          : []
        setUltimosAtendimentos(finalizados)
      } catch {
        /* silencioso */
      } finally {
        setCarregandoAtendimentos(false)
      }
    }
    if (cliente) buscar()
  }, [cliente, carregarHistorico])

  if (carregando) return <div className="gendaz-loading">Carregando dashboard...</div>
  if (erro) return <div className="gendaz-erro">{erro}</div>

  const nomeEmpresa = cliente?.empresaNome || cliente?.empresa?.nome || cliente?.empresa?.nomeFantasia || cliente?.empresaNomeFantasia || 'sua empresa'
  const proximo = dashboard?.proximoAgendamento || (agendamentos && agendamentos.length > 0 ? agendamentos[0] : null)
  const promos = dashboard?.promocoes || []
  const notifs = dashboard?.notificacoes || []
  const textoPadrao = '-----'
  const totalGasto = (dashboard?.totalGasto !== undefined && dashboard?.totalGasto !== null) ? Number(dashboard.totalGasto) : 0
  const servicoMaisEscolhido = dashboard?.servicoMaisEscolhido || textoPadrao

  const servicoProximo = proximo?.servicoNome || proximo?.servico || textoPadrao
  const profissionalProximo = proximo?.profissionalNome || proximo?.profissional || textoPadrao
  const dataProximo = proximo?.data ? new Date(`${proximo.data}T12:00:00`).toLocaleDateString('pt-BR') : textoPadrao
  const horaProximo = proximo?.horaInicio || proximo?.hora || textoPadrao
  const totalGastoFormatado = totalGasto > 0
    ? totalGasto.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
    : 'R$ 0,00'

  const nomeLojaContato = nomeEmpresa

  return (
    <section className="gendaz-page gendaz-dashboard">
      <header className="gendaz-page__header gendaz-page__header--hero">
        <span className="gendaz-kicker">Dashboard</span>
        <h1>Bom te ver novamente, {nomeEmpresa} está te esperando.</h1>
        <p>Acompanhe sua agenda, conversas e beneficios em um unico espaco.</p>
      </header>

      {/* PROXIMO AGENDAMENTO */}
      {proximo ? (
        <article className="gendaz-card gendaz-card--highlight gendaz-card--agendamento">
          <div className="gendaz-agenda-proximo__title">
            <strong>{servicoProximo}</strong>
          </div>

          <div className="gendaz-card__top">
            <div className="gendaz-card__icon-title">
              <Calendar size={18} />
              <span>Proximo agendamento</span>
            </div>
          </div>

          <div className="gendaz-agenda-proximo">
            <div className="gendaz-agenda-proximo__linha">
              <span>Profissional</span>
              <strong>{profissionalProximo}</strong>
            </div>
            <div className="gendaz-agenda-proximo__linha gendaz-agenda-proximo__linha--datahora">
              <div>
                <span>Data</span>
                <strong>{dataProximo}</strong>
              </div>
              <div>
                <span>Horário</span>
                <strong>{horaProximo}</strong>
              </div>
            </div>
          </div>

          <div className="gendaz-card__actions">
            <button className="gendaz-btn gendaz-btn--secondary" onClick={() => navigate('agenda')}>
              Reagendar
            </button>
            <button className="gendaz-btn gendaz-btn--danger" onClick={() => navigate('agenda')}>
              Cancelar
            </button>
          </div>
        </article>
      ) : (
        <article className="gendaz-card gendaz-card--empty">
          <div className="gendaz-empty-state">
            <Calendar size={48} />
            <h3>Sem agendamentos proximos</h3>
            <p>Voce nao possui agendamentos no momento.</p>
            <button className="gendaz-btn gendaz-btn--primary" onClick={() => navigate('agenda')}>
              <Plus size={16} /> Agendar Agora
            </button>
          </div>
        </article>
      )}

      {/* GRID 2 COLUNAS */}
      <div className="gendaz-dashboard-grid">
        <div className="gendaz-dashboard-col">
          {/* RESUMO FINANCEIRO */}
          <article className="gendaz-card gendaz-card--resumo-financeiro">
            <div className="gendaz-card__top">
              <div className="gendaz-card__icon-title">
                <Wallet size={18} />
                <span>Resumo financeiro</span>
              </div>
            </div>
            <div className="gendaz-resumo-financeiro">
              <div className="gendaz-resumo-financeiro__item gendaz-resumo-financeiro__item--total-gasto">
                <span>Total gasto</span>
                <strong>{totalGastoFormatado}</strong>
              </div>
              <div className="gendaz-resumo-financeiro__item gendaz-resumo-financeiro__item--servico-mais-escolhido">
                <span>Serviço mais escolhido</span>
                <strong>{servicoMaisEscolhido}</strong>
              </div>
            </div>
          </article>

          {/* PROMOCOES */}
          {promos.length > 0 && (
            <article className="gendaz-card gendaz-card--promocoes">
              <div className="gendaz-card__top">
                <div className="gendaz-card__icon-title">
                  <Gift size={18} />
                  <span>Promocoes disponiveis</span>
                </div>
              </div>
              <div className="gendaz-stack">
                {promos.map((promo) => (
                  <div key={promo.id} className="gendaz-mini-card">
                    <div className="gendaz-mini-card__header">
                      <strong>{promo.codigo}</strong>
                      <span className="gendaz-desconto">
                        {promo.tipo === 'PERCENTUAL' ? `${promo.valor}% OFF` : `R$ ${Number(promo.valor).toFixed(2)}`}
                      </span>
                    </div>
                    <span>{promo.descricao}</span>
                    <small>
                      {promo.aplicarTodosServicos
                        ? 'Aplicável a todos os serviços'
                        : 'Aplicável a serviços selecionados'}
                      {promo.dataFim ? ` · Válido até ${new Date(promo.dataFim).toLocaleDateString('pt-BR')}` : ''}
                    </small>
                  </div>
                ))}
              </div>
              <button className="gendaz-btn gendaz-btn--ghost" onClick={() => navigate('beneficios')}>
                Ver todos os beneficios <ChevronRight size={16} />
              </button>
            </article>
          )}

          {/* CONTATO RAPIDO */}
          <article className="gendaz-card gendaz-card--contato">
            <div className="gendaz-card__top">
              <div className="gendaz-card__icon-title">
                <MessageCircle size={18} />
                <span>Contato rapido</span>
              </div>
            </div>
            <div className="gendaz-botoes-contato">
              <button className="gendaz-btn-contato" onClick={() => navigate('ia')}>
                <Sparkles size={18} />
                <span>gendazIA</span>
              </button>
              <button
                className="gendaz-btn-contato"
                onClick={() => navigate('suporte')}
              >
                <MessageCircle size={18} />
                <span>Falar com {nomeLojaContato}</span>
              </button>
              <button className="gendaz-btn-contato" onClick={() => navigate('suporte')}>
                <LifeBuoy size={18} />
                <span>Suporte</span>
              </button>
              <button className="gendaz-btn-contato" onClick={() => navigate('agenda')}>
                <Phone size={18} />
                <span>Agendar</span>
              </button>
            </div>
          </article>

        </div>

        <div className="gendaz-dashboard-col">
          {/* HISTORICO */}
          <article className="gendaz-card gendaz-card--historico">
            <div className="gendaz-card__top">
              <div className="gendaz-card__icon-title">
                <Clock size={18} />
                <span>Ultimos atendimentos</span>
              </div>
              <button className="gendaz-link-mais" onClick={() => navigate('historico')}>
                Ver mais <ChevronRight size={14} />
              </button>
            </div>

            {carregandoAtendimentos ? (
              <p className="gendaz-vazio">Carregando...</p>
            ) : ultimosAtendimentos.length > 0 ? (
              <div className="gendaz-stack">
                {ultimosAtendimentos.map((at, idx) => (
                  <div key={idx} className="gendaz-mini-card gendaz-mini-card--historico">
                    <div className="gendaz-mini-card__data">
                      <strong>{at.data ? new Date(`${at.data}T12:00:00`).toLocaleDateString('pt-BR') : '—'}</strong>
                    </div>
                    <div className="gendaz-mini-card__info">
                      <p className="gendaz-mini-card__servico">{at.servicoNome || at.servico || 'Servico'}</p>
                      <p className="gendaz-mini-card__profissional">Com {at.profissionalNome || at.profissional || 'Profissional'}</p>
                    </div>
                    {at.valor && (
                      <div className="gendaz-mini-card__valor">
                        R$ {Number(at.valor).toFixed(2)}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="gendaz-vazio">Sem atendimentos registrados</p>
            )}
          </article>
        </div>
      </div>

      {/* NOTIFICACOES */}
      {notifs.length > 0 && (
        <article className="gendaz-card">
          <div className="gendaz-card__top">
            <div className="gendaz-card__icon-title">
              <BellRing size={18} />
              <span>Notificacoes recentes</span>
            </div>
          </div>
          <ul className="gendaz-list">
            {notifs.slice(0, 5).map((notif) => (
              <li key={notif.promocaoId}>
                {notif.cupomDescricao || 'Nova promoção disponível'}
                {notif.dataEnvio && <small> — {new Date(notif.dataEnvio).toLocaleDateString('pt-BR')}</small>}
              </li>
            ))}
          </ul>
        </article>
      )}

      {/* BANNER ACAO RAPIDA */}
      {!proximo && (
        <article className="gendaz-card gendaz-card--acao-rapida">
          <div className="gendaz-acao-content">
            <div>
              <h3>Agende Agora</h3>
              <p>Clique abaixo para fazer seu primeiro agendamento</p>
            </div>
            <button className="gendaz-btn gendaz-btn--primary gendaz-btn--lg" onClick={() => navigate('agenda')}>
              <Plus size={18} /> Novo Agendamento
            </button>
          </div>
        </article>
      )}
    </section>
  )
}
