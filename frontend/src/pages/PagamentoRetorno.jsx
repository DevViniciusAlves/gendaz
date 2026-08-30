import { AlertCircle, CheckCircle2, RefreshCw } from 'lucide-react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useState, useEffect, useRef } from 'react'
import Button from '../components/Button.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { appApi } from '../api/appApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import logoWhite from '../assets/logos/gendaz-logo-branco.png'

const statusTexto = {
  PAYMENT_PENDING: 'Aguardando confirmacao',
  PAYMENT_APPROVED: 'Pagamento aprovado',
  PAYMENT_REJECTED: 'Pagamento recusado',
  PAYMENT_CANCELED: 'Pagamento cancelado',
  PAYMENT_EXPIRED: 'Pagamento expirado',
}

export default function PagamentoRetorno({ tipo }) {
  const [searchParams] = useSearchParams()
  const sessionId = searchParams.get('session_id')
  const navigate = useNavigate()
  const { getPagamentoPendente, limparPagamentoPendente } = useAuth()
  const [pendente, setPendente] = useState(() => getPagamentoPendente())
  const [status, setStatus] = useState(null)
  const [mensagem, setMensagem] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)
  const carregadoRef = useRef(false)

  const pagamento = pendente?.pagamentoPlano
  const aprovado = status === 'APPROVED' || pagamento?.status === 'PAYMENT_APPROVED'
  const cancelado = tipo === 'cancelado' || status === 'CANCELED'
  const titulo = aprovado ? 'Pagamento aprovado' : cancelado ? 'Pagamento não finalizado' : 'Retorno do pagamento'
  const descrição = aprovado
    ? 'Sua conta Pro foi liberada. Entre novamente para acessar o painel.'
    : cancelado
      ? 'O pagamento não foi concluido. Voce pode voltar para a tela de pagamento e tentar novamente.'
      : 'Recebemos o retorno da Stripe. Confirme o status para liberar a conta quando o pagamento for aprovado.'

  async function consultarStatus(sid = sessionId) {
    setErro('')
    setCarregando(true)
    try {
      if (!sid) {
        setErro('Não encontramos a sessão do pagamento.')
        return
      }

      const resultado = await appApi.verificarPagamentoPublico(sid)

      setStatus(resultado.statusVerificacao)
      setMensagem(resultado.mensagem)

      if (resultado.statusVerificacao === 'APPROVED') {
        limparPagamentoPendente()
      }
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Não foi possível consultar o pagamento.')
    } finally {
      setCarregando(false)
    }
  }

  useEffect(() => {
    if (carregadoRef.current) return
    carregadoRef.current = true
    if (sessionId) {
      consultarStatus(sessionId)
    }
  }, [sessionId])

  return (
    <main className="payment-result-screen">
      <section className="payment-result-card">
        <div className="payment-result-header">
          <img src={logoWhite} alt="gendaz" className="payment-result-logo" />
        </div>

        <span className="payment-result-badge">Plano Pro</span>

        <div className={aprovado ? 'payment-result-icon success' : cancelado ? 'payment-result-icon danger' : 'payment-result-icon'}>
          {aprovado ? <CheckCircle2 size={26} /> : <AlertCircle size={26} />}
        </div>

        <h1 className="payment-result-title">{titulo}</h1>
        <p className="payment-result-description">{mensagem || descrição}</p>

        {pagamento && (
          <div className="payment-result-status">
            <div>
              <small>Status atual</small>
              <strong>{statusTexto[pagamento.status] || pagamento.status}</strong>
            </div>
            <StatusBadge status={pagamento.status || 'PAYMENT_PENDING'} />
          </div>
        )}

        {erro && <p className="payment-result-error">{erro}</p>}

        <div className="payment-result-actions">
          {aprovado ? (
            <Link to="/login" className="btn btn-primary">Entrar na conta</Link>
          ) : (pagamento && (
            <Link to="/pagamento-pendente" className="btn btn-primary">Voltar ao pagamento</Link>
          ))}
          <Link to="/" className="btn btn-secondary">Voltar ao site</Link>
          {!aprovado && (
            <Button type="button" variant="ghost" icon={RefreshCw} onClick={() => consultarStatus()} loading={carregando} loadingText="Ja paguei, verificar">
              Ja paguei, verificar
            </Button>
          )}
        </div>
      </section>
    </main>
  )
}
