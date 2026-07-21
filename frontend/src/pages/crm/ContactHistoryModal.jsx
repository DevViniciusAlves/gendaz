import { useEffect, useState } from 'react'
import Modal from '../../components/Modal.jsx'
import Button from '../../components/Button.jsx'
import { buscarHistoricoContatos } from '../../api/crmApi.js'

function formatarData(data) {
  if (!data) return '-'
  const d = new Date(data)
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ' ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

export default function ContactHistoryModal({ open, onClose, cliente }) {
  const [contatos, setContatos] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (open && cliente?.id) {
      setLoading(true)
      buscarHistoricoContatos(cliente.id)
        .then((data) => setContatos(data || []))
        .catch(() => setContatos([]))
        .finally(() => setLoading(false))
    } else {
      setContatos([])
    }
  }, [open, cliente?.id])

  if (!open || !cliente) return null

  return (
    <div className="modal-backdrop" role="presentation" onClick={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <section className="modal" style={{ minWidth: 560, maxWidth: 700, background: 'var(--surface-solid, var(--surface-strong, var(--surface)))', color: 'var(--text)' }}>
        <div className="modal-header" style={{ background: 'var(--surface-solid, var(--surface-strong, var(--surface)))', borderBottom: '1px solid var(--line)' }}>
          <h2 style={{ color: 'var(--text)' }}>Historico de Contatos: {cliente.nome}</h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Fechar modal">
            <span style={{ fontSize: 18, lineHeight: 1 }}>×</span>
          </button>
        </div>

        <div style={{ padding: '0 24px 24px', background: 'var(--surface-solid, var(--surface-strong, var(--surface)))' }}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: 32, color: 'var(--muted)' }}>Carregando...</div>
          ) : contatos.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 32, color: 'var(--muted)' }}>Nenhum contato registrado.</div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--line)' }}>
                    <th style={{ textAlign: 'left', padding: '10px 8px', color: 'var(--muted)', fontWeight: 700 }}>Data</th>
                    <th style={{ textAlign: 'left', padding: '10px 8px', color: 'var(--muted)', fontWeight: 700 }}>Tipo</th>
                    <th style={{ textAlign: 'left', padding: '10px 8px', color: 'var(--muted)', fontWeight: 700 }}>Assunto</th>
                    <th style={{ textAlign: 'left', padding: '10px 8px', color: 'var(--muted)', fontWeight: 700 }}>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {contatos.map((c) => (
                    <tr key={c.id} style={{ borderBottom: '1px solid var(--surface-soft)' }}>
                      <td style={{ padding: '10px 8px', color: 'var(--text)' }}>{formatarData(c.dataCriacao)}</td>
                      <td style={{ padding: '10px 8px', color: 'var(--text)' }}>{c.tipo}</td>
                      <td style={{ padding: '10px 8px', color: 'var(--text)' }}>{c.assunto || c.template}</td>
                      <td style={{ padding: '10px 8px' }}>
                        <span style={{ color: c.status === 'aberto' ? 'var(--text)' : 'var(--muted)' }}>
                          {c.status === 'aberto' ? 'Abriu ✅' : 'Nao abriu ❌'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
            <Button variant="secondary" onClick={onClose}>Fechar</Button>
          </div>
        </div>
      </section>
    </div>
  )
}
