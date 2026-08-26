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
    <Modal title={`Histórico de Contatos: ${cliente.nome}`} open={open} onClose={onClose}>
      <div style={{ padding: '0 24px 24px', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
        {loading ? (
          <div style={{ textAlign: 'center', padding: 32, color: '#6b7280' }}>Carregando...</div>
        ) : contatos.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 32, color: '#6b7280' }}>Nenhum contato registrado.</div>
        ) : (
          <div style={{ overflowX: 'auto', overflowY: 'auto', maxHeight: 'calc(80vh - 140px)', paddingRight: 4 }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #e5e7eb' }}>
                  <th style={{ textAlign: 'left', padding: '10px 8px', color: '#6b7280', fontWeight: 700 }}>Data</th>
                  <th style={{ textAlign: 'left', padding: '10px 8px', color: '#6b7280', fontWeight: 700 }}>Tipo</th>
                  <th style={{ textAlign: 'left', padding: '10px 8px', color: '#6b7280', fontWeight: 700 }}>Assunto</th>
                  <th style={{ textAlign: 'left', padding: '10px 8px', color: '#6b7280', fontWeight: 700 }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {contatos.map((c) => (
                  <tr key={c.id} style={{ borderBottom: '1px solid #f3f4f6' }}>
                    <td style={{ padding: '10px 8px', color: '#111827' }}>{formatarData(c.dataCriacao)}</td>
                    <td style={{ padding: '10px 8px', color: '#111827' }}>{c.tipo}</td>
                    <td style={{ padding: '10px 8px', color: '#111827' }}>{c.assunto || c.template}</td>
                    <td style={{ padding: '10px 8px' }}>
                      <span style={{ color: c.status === 'aberto' ? '#111827' : '#6b7280' }}>
                        {c.status === 'aberto' ? 'Abriu ' : 'Nao abriu '}
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
    </Modal>
  )
}
