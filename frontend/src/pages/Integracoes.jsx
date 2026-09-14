import { MessageCircle } from 'lucide-react'
import WhatsAppIntegracoes from './integracoes/WhatsAppIntegracoes.jsx'

export default function Integracoes() {
  return (
    <section className="page settings-page">
      <div className="page-title">
        <span className="section-kicker">Integrações</span>
        <h1>Integrações</h1>
        <p>Conecte e gerencie os serviços integrados ao seu negócio.</p>
      </div>

      <WhatsAppIntegracoes />
    </section>
  )
}
