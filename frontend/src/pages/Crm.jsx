import { useState } from 'react'
import { Users } from 'lucide-react'
import { useCrmData } from '../hooks/useCrmData.js'
import CrmFilters from './crm/CrmFilters.jsx'
import ClienteCard from './crm/ClienteCard.jsx'
import SendMessageModal from './crm/SendMessageModal.jsx'
import ContactHistoryModal from './crm/ContactHistoryModal.jsx'

export default function Crm() {
  const { clientes, loading, error, filtros, atualizarFiltros, limparFiltros } = useCrmData()
  const [modalEnvio, setModalEnvio] = useState(null)
  const [modalHistorico, setModalHistorico] = useState(null)

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
          <span className="section-kicker">Relacionamento</span>
          <h1>CRM - Relacionamento com Clientes</h1>
          <p>Acompanhe, segmente e automatize contatos com seus clientes.</p>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        <CrmFilters
          filtros={filtros}
          onFiltroChange={atualizarFiltros}
          onLimpar={limparFiltros}
        />

        <div style={{ flex: 1, minWidth: 0 }}>
          {loading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {[1, 2, 3].map((i) => (
                <div
                  key={i}
                  className="animate-pulse"
                  style={{
                    height: 160,
                    background: 'rgba(255,255,255,0.05)',
                    borderRadius: 10,
                  }}
                />
              ))}
            </div>
          ) : error ? (
            <div style={{
              textAlign: 'center',
              padding: 40,
              color: '#EF4444',
              background: 'rgba(255,255,255,0.05)',
              borderRadius: 10,
              fontSize: 13,
            }}>
              Erro ao carregar dados. Tente novamente.
            </div>
          ) : clientes.length === 0 ? (
            <div style={{
              textAlign: 'center',
              padding: 40,
              color: '#9ca3af',
              background: 'rgba(255,255,255,0.05)',
              borderRadius: 10,
            }}>
              <Users size={32} style={{ margin: '0 auto 12px', opacity: 0.5 }} />
              <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 2 }}>Nenhum cliente encontrado</div>
              <div style={{ fontSize: 12 }}>Comece adicionando clientes na aba 'Clientes'</div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {clientes.map((cliente) => (
                <ClienteCard
                  key={cliente.id}
                  cliente={cliente}
                  onEnviarMensagem={handleEnviarMensagem}
                  onVerHistorico={handleVerHistorico}
                />
              ))}
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
