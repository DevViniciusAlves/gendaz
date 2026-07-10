import { useContext, useState, useEffect, useCallback } from 'react'
import { Loader, ChevronLeft, ChevronRight } from 'lucide-react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'

export default function Historico() {
  const { carregarHistorico } = useContext(ClienteGendazContext)
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
      console.log('[Historico] dados:', data)
      const lista = data?.agendamentos || data?.historico || (Array.isArray(data) ? data : [])
      setAgendamentos(lista)
      setTotal(data?.total || lista.length || 0)
      setTotalPaginas(Math.ceil((data?.total || lista.length || 1) / 10))
    } catch (err) {
      console.error('[Historico] erro:', err)
      setErro(null)
      setAgendamentos([])
    } finally {
      setCarregando(false)
    }
  }, [carregarHistorico])

  useEffect(() => {
    buscar(pagina)
  }, [pagina, buscar])

  if (carregando) {
    return (
      <section className="gendaz-page">
        <div className="gendaz-loading"><Loader size={20} /> Carregando histórico...</div>
      </section>
    )
  }

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Histórico</span>
        <h1>Atendimentos passados</h1>
        <p>Serviços, profissionais, valores pagos e observações organizados de forma limpa.</p>
      </header>

      {erro && <div className="gendaz-erro">{erro}</div>}

      {agendamentos.length > 0 ? (
        <>
          <div className="gendaz-table">
            {agendamentos.map((item) => (
              <article key={item.id} className="gendaz-table__row">
                <div>
                  <strong>{item.servico || item.servicoNome || item.servico?.nome || 'Serviço'}</strong>
                  <small>{item.profissional || item.profissionalNome || item.profissional?.nome || 'Profissional'}</small>
                </div>
                <div>
                  <span>{item.data ? new Date(`${item.data}T12:00:00`).toLocaleDateString('pt-BR') : '—'}</span>
                  {item.hora && <small> • {item.hora}</small>}
                </div>
                <div>
                  <strong>{item.valor != null ? Number(item.valor).toFixed(2) : '—'}</strong>
                </div>
                {item.avaliacao && (
                  <div><small>{'⭐'.repeat(item.avaliacao)}</small></div>
                )}
                {(item.observacoes || item.observacao) && <small>{item.observacoes || item.observacao}</small>}
              </article>
            ))}
          </div>

          {totalPaginas > 1 && (
            <div className="gendaz-pagination">
              <button className="gendaz-btn" onClick={() => setPagina((p) => Math.max(1, p - 1))} disabled={pagina === 1}>
                <ChevronLeft size={16} /> Anterior
              </button>
              <span>Página {pagina} de {totalPaginas} ({total} atendimentos)</span>
              <button className="gendaz-btn" onClick={() => setPagina((p) => Math.min(totalPaginas, p + 1))} disabled={pagina === totalPaginas}>
                Próxima <ChevronRight size={16} />
              </button>
            </div>
          )}
        </>
      ) : (
        <p className="gendaz-vazio">Você ainda não possui atendimentos no histórico.</p>
      )}
    </section>
  )
}
