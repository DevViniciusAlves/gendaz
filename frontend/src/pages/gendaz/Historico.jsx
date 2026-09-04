import { useContext, useState, useEffect, useCallback } from 'react'
import { Loader, ChevronLeft, ChevronRight } from 'lucide-react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'

function formatarMoeda(valor) {
  return valor != null && valor !== ''
    ? Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
    : null
}

export default function Historico() {
  const { cliente, carregarHistorico } = useContext(ClienteGendazContext)
  const [agendamentos, setAgendamentos] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [pagina, setPagina] = useState(1)
  const [total, setTotal] = useState(0)
  const [totalPaginas, setTotalPaginas] = useState(1)
  const itensPorPagina = 5

  const buscar = useCallback(async (p) => {
    try {
      setCarregando(true)
      setErro(null)
      const data = await carregarHistorico(p, itensPorPagina)
      const lista = data?.agendamentos || data?.historico || (Array.isArray(data) ? data : [])
      setAgendamentos(lista)
      setTotal(data?.total || lista.length || 0)
      setTotalPaginas(data?.totalPaginas || Math.ceil((data?.total || lista.length || 1) / itensPorPagina))
    } catch (err) {
      console.error('[Historico] erro')
      if (err.response?.status === 401) {
        window.dispatchEvent(new CustomEvent('meu-gendaz:logout'))
        return
      }
      setErro(null)
      setAgendamentos([])
    } finally {
      setCarregando(false)
    }
  }, [carregarHistorico, itensPorPagina])

  useEffect(() => {
    buscar(pagina)
  }, [pagina, buscar])

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
        <p>Servicos, profissionais, valores pagos e observações.</p>
      </header>

      {erro && <div className="gendaz-erro">{erro}</div>}

      {agendamentos.length > 0 ? (
        <>
          <div className="gendaz-table">
            {agendamentos.map((item) => {
              const status = String(item.status || 'FINALIZADO').toLowerCase()
              const servico = item.servicoNome || item.servico || item.servico?.nome || '-----'
              const profissional = item.profissionalNome || item.profissional || item.profissional?.nome || '-----'
              const data = item.data ? new Date(`${item.data}T12:00:00`).toLocaleDateString('pt-BR') : '-----'
              const hora = item.horaInicio || item.hora || '-----'
              const valorFonte = item.valorFinal ?? item.valor ?? item.valorServico ?? item.servicoValor ?? item.servico?.valor ?? null
              const valor = formatarMoeda(valorFonte) ?? '-----'
              const valorOriginal = item.valorOriginal ?? item.valorServico ?? item.servicoValor ?? item.servico?.valor ?? null
              const desconto = item.valorDesconto
              const cupomCodigo = item.cupomCodigo
              const temDesconto = cupomCodigo && desconto != null && Number(desconto) > 0
              const observação = [item.observacoes, item.observações, item.observação]
                .find((texto) => texto && String(texto).trim().toLowerCase() !== 'criado pelo painel.')

              return (
                <article key={item.id} className="gendaz-card gendaz-card--historico-item">
                  <div className="gendaz-historico-grid">
                    <div className="gendaz-historico-field">
                      <span>Serviço</span>
                      <strong>{servico}</strong>
                    </div>
                    <div className="gendaz-historico-field">
                      <span>Profissional</span>
                      <strong>{profissional}</strong>
                    </div>
                    <div className="gendaz-historico-field gendaz-historico-field--datahora">
                      <div>
                        <span>Data</span>
                        <strong>{data}</strong>
                      </div>
                      <div>
                        <span>Horário</span>
                        <strong>{hora}</strong>
                      </div>
                    </div>
                    <div className="gendaz-historico-field gendaz-historico-field--status">
                      <span>Status</span>
                      <strong className={`gendaz-status gendaz-status--${status}`}>
                        {item.status || 'Finalizado'}
                      </strong>
                    </div>
                    <div className="gendaz-historico-field gendaz-historico-field--valor">
                      <span>Valor</span>
                      {temDesconto ? (
                        <div className="gendaz-historico-valor-breakdown">
                          <strong>{formatarMoeda(valorOriginal)}</strong>
                          <span className="gendaz-historico-cupom">
                            Cupom {cupomCodigo}
                            <span>-{formatarMoeda(desconto)}</span>
                          </span>
                          <strong className="gendaz-historico-total">{formatarMoeda(item.valorFinal ?? item.valor)}</strong>
                        </div>
                      ) : (
                        <strong>{valor}</strong>
                      )}
                    </div>
                  </div>
                  {observação && (
                    <p className="gendaz-historico-observação">{observação}</p>
                  )}
                </article>
              )
            })}
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
            <h3>Sem historico</h3>
            <p>Voce ainda não possui atendimentos registrados.</p>
          </div>
        </div>
      )}
    </section>
  )
}
