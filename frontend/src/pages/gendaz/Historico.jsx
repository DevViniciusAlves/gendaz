import { useContext, useState, useEffect, useCallback } from 'react'
import { Loader, ChevronLeft, ChevronRight, Calendar, Clock } from 'lucide-react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'

export default function Historico() {
  const { cliente, carregarHistorico } = useContext(ClienteGendazContext)
  const [agendamentos, setAgendamentos] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [pagina, setPagina] = useState(1)
  const [total, setTotal] = useState(0)
  const [totalPaginas, setTotalPaginas] = useState(1)

  const buscar = useCallback(async (p) => {
    try {
      setCarregando(true)
      setErro(null)
      const data = await carregarHistorico(p, 10)
      const lista = data?.agendamentos || data?.historico || (Array.isArray(data) ? data : [])
      setAgendamentos(lista)
      setTotal(data?.total || lista.length || 0)
      setTotalPaginas(data?.totalPaginas || Math.ceil((data?.total || lista.length || 1) / 10))
    } catch (err) {
      console.error('[Historico] erro:', err)
      if (err.response?.status === 401) {
        window.dispatchEvent(new CustomEvent('meu-gendaz:logout'))
        return
      }
      setErro(null)
      setAgendamentos([])
    } finally {
      setCarregando(false)
    }
  }, [carregarHistorico])

  useEffect(() => {
    if (cliente) buscar(pagina)
  }, [cliente, pagina, buscar])

  if (carregando) {
    return (
      <section className="gendaz-page">
        <div className="gendaz-loading"><Loader size={20} /> Carregando historico...</div>
      </section>
    )
  }

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Historico</span>
        <h1>Atendimentos passados</h1>
        <p>Servicos, profissionais, valores pagos e observacoes.</p>
      </header>

      {erro && <div className="gendaz-erro">{erro}</div>}

      {agendamentos.length > 0 ? (
        <>
          <div className="gendaz-table">
            {agendamentos.map((item) => (
              <article key={item.id} className="gendaz-table__row">
                <div>
                  <strong>{item.servicoNome || item.servico || item.servico?.nome || 'Servico'}</strong>
                  <small>{item.profissionalNome || item.profissional || item.profissional?.nome || 'Profissional'}</small>
                </div>
                <div>
                  <span className="gendaz-info-row-inline">
                    <Calendar size={14} />
                    {item.data ? new Date(`${item.data}T12:00:00`).toLocaleDateString('pt-BR') : '—'}
                    {(item.horaInicio || item.hora) && <><Clock size={14} /> {item.horaInicio || item.hora}</>}
                  </span>
                </div>
                <div>
                  <span className={`gendaz-status gendaz-status--${(item.status || '').toLowerCase()}`}>{item.status || 'Finalizado'}</span>
                </div>
                {item.valor && <div><strong>R$ {Number(item.valor).toFixed(2)}</strong></div>}
                {(item.observacoes || item.observacao) && <small>{item.observacoes || item.observacao}</small>}
              </article>
            ))}
          </div>

          {totalPaginas > 1 && (
            <div className="gendaz-pagination">
              <button className="gendaz-btn" onClick={() => setPagina((p) => Math.max(1, p - 1))} disabled={pagina === 1}>
                <ChevronLeft size={16} /> Anterior
              </button>
              <span>Pagina {pagina} de {totalPaginas} ({total} atendimentos)</span>
              <button className="gendaz-btn" onClick={() => setPagina((p) => Math.min(totalPaginas, p + 1))} disabled={pagina === totalPaginas}>
                Proxima <ChevronRight size={16} />
              </button>
            </div>
          )}
        </>
      ) : (
        <div className="gendaz-card gendaz-card--empty">
          <div className="gendaz-empty-state">
            <Calendar size={48} />
            <h3>Sem historico</h3>
            <p>Voce ainda nao possui atendimentos registrados.</p>
          </div>
        </div>
      )}
    </section>
  )
}
