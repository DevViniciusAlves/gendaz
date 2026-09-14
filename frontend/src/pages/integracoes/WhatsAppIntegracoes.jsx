import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { MessageCircle } from 'lucide-react'
import Button from '../../components/Button.jsx'
import StatusBadge from '../../components/StatusBadge.jsx'
import { buscarResumoWhatsapp } from '../../api/whatsappApi.js'
import WhatsAppModal from './WhatsAppModal.jsx'
import './whatsapp.css'

export default function WhatsAppIntegracoes() {
  const [resumo, setResumo] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [modalAberto, setModalAberto] = useState(false)

  const carregar = useCallback(async () => {
    setCarregando(true)
    setErro('')
    try {
      const dados = await buscarResumoWhatsapp()
      setResumo(dados)
    } catch (err) {
      setErro(err?.response?.data?.mensagem || 'Não foi possível consultar a integração agora. Tente novamente em alguns instantes.')
    } finally {
      setCarregando(false)
    }
  }, [])

  useEffect(() => {
    carregar()
  }, [carregar])

  return (
    <section className="panel settings-form-panel">
      <div className="panel-head settings-form-head">
        <div>
          <span className="section-kicker">Integrações</span>
          <h2>WhatsApp</h2>
          <p>Lembretes automáticos e ações de CRM pelo WhatsApp.</p>
        </div>
        <MessageCircle size={22} color="var(--primary)" />
      </div>

      {carregando && <p className="wpp-muted">Carregando integração...</p>}
      {!carregando && erro && (
        <>
          <p className="wpp-muted">Temporariamente indisponível</p>
          <p className="form-error">{erro}</p>
          <Button variant="secondary" type="button" onClick={carregar}>Tentar novamente</Button>
        </>
      )}
      {!carregando && !erro && resumo && (
        <div className="wpp-card-body">
          <div className="wpp-card-status">
            <StatusBadge status={resumo.conexao?.estado || 'UNAVAILABLE'} />
            {resumo.disponivelNoPlano ? (
              <span className="wpp-muted">Lembretes automáticos e ações de CRM pelo WhatsApp.</span>
            ) : (
              <span className="wpp-muted">Não disponível no seu plano</span>
            )}
          </div>
          {resumo.disponivelNoPlano ? (
            <Button type="button" onClick={() => setModalAberto(true)}>Configurar</Button>
          ) : (
            <Link to="/sistema/planos" className="btn btn-secondary">Ver planos</Link>
          )}
        </div>
      )}

      <WhatsAppModal
        open={modalAberto}
        resumo={resumo}
        onClose={() => setModalAberto(false)}
        onResumoAtualizado={setResumo}
      />
    </section>
  )
}
