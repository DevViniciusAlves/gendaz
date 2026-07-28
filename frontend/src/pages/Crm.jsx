import { useEffect, useMemo, useState } from 'react'
import { Users } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { useCrmData } from '../hooks/useCrmData.js'
import CrmFilters from './crm/CrmFilters.jsx'
import ClienteCard from './crm/ClienteCard.jsx'
import SendMessageModal from './crm/SendMessageModal.jsx'
import ContactHistoryModal from './crm/ContactHistoryModal.jsx'
import Pagination from '../components/Pagination.jsx'
import './crm/crm.css'

const CLIENTES_POR_PAGINA = 6

export default function Crm() {
  const { clientes, loading, error, filtros, atualizarFiltros, limparFiltros } = useCrmData()
  const [searchParams] = useSearchParams()
  const [modalEnvio, setModalEnvio] = useState(null)
  const [modalHistorico, setModalHistorico] = useState(null)
  const [pagina, setPagina] = useState(1)
  const segmentParam = searchParams.get('segment')

  useEffect(() => {
    if (!segmentParam) return
    atualizarFiltros({ segment: segmentParam })
    setPagina(1)
  }, [segmentParam, atualizarFiltros])
  const totalPaginas = Math.max(1, Math.ceil(clientes.length / CLIENTES_POR_PAGINA))
  const paginaAtual = Math.min(pagina, totalPaginas)
  const clientesPaginados = useMemo(() => {
    const inicio = (paginaAtual - 1) * CLIENTES_POR_PAGINA
    return clientes.slice(inicio, inicio + CLIENTES_POR_PAGINA)
  }, [clientes, paginaAtual])

  function handleEnviarMensagem(cliente, template) {
    setModalEnvio({ cliente, template })
  }

  function handleVerHistorico(cliente) {
    setModalHistorico(cliente)
  }

  return (
    <section className="page">
      <div className="page-title">
        <div>
          <span className="section-kicker" style={{ color: 'var(--text)', letterSpacing: '0.12em' }}>Relacionamento</span>
          <h1>CRM - Relacionamento com Clientes</h1>
          <p>Acompanhe, segmente e automatize contatos com seus clientes.</p>
        </div>
      </div>

      <div className="crm-layout">
        <CrmFilters
          filtros={filtros}
          onFiltroChange={atualizarFiltros}
          onLimpar={limparFiltros}
        />

        <div className="crm-content">
          {loading ? (
            <div className="crm-cards-grid">
              {[1, 2, 3].map((i) => (
                <div
                  key={i}
                  className="animate-pulse"
                  style={{ minHeight: 220, background: 'var(--surface-soft)', borderRadius: 12 }}
                />
              ))}
            </div>
          ) : error ? (
            <div style={{
              textAlign: 'center',
              padding: 40,
              color: 'var(--text)',
              background: 'var(--surface-soft)',
              borderRadius: 10,
              fontSize: 13,
            }}>
              Erro ao carregar dados. Tente novamente.
            </div>
          ) : clientes.length === 0 ? (
            <div style={{
              textAlign: 'center',
              padding: 40,
              color: 'var(--muted)',
              background: 'var(--surface-soft)',
              borderRadius: 10,
            }}>
              <Users size={32} style={{ margin: '0 auto 12px', opacity: 0.5 }} />
              <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 2 }}>Nenhum cliente encontrado</div>
              <div style={{ fontSize: 12 }}>Comece adicionando clientes na aba 'Clientes'</div>
            </div>
          ) : (
            <div className="crm-results">
              <div className="crm-cards-grid">
              {clientesPaginados.map((cliente) => (
                <ClienteCard
                  key={cliente.id}
                  cliente={cliente}
                  onEnviarMensagem={handleEnviarMensagem}
                  onVerHistorico={handleVerHistorico}
                />
              ))}
              </div>
              <Pagination
                page={paginaAtual}
                totalPages={totalPaginas}
                totalItems={clientes.length}
                pageSize={CLIENTES_POR_PAGINA}
                onPageChange={setPagina}
              />
            </div>
          )}
        </div>
      </div>

      <SendMessageModal
        open={Boolean(modalEnvio)}
        onClose={() => setModalEnvio(null)}
        cliente={modalEnvio?.cliente}
        template={modalEnvio?.template}
      />

      <ContactHistoryModal
        open={Boolean(modalHistorico)}
        onClose={() => setModalHistorico(null)}
        cliente={modalHistorico}
      />
    </section>
  )
}
