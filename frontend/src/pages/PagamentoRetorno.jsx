import { AlertCircle, CheckCircle2, RefreshCw } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { useState } from 'react'
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
  const location = useLocation()
  const { getPagamentoPendente, limparPagamentoPendente } = useAuth()
  const [pendente, setPendente] = useState(() => getPagamentoPendente())
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  const pagamento = pendente?.pagamentoPlano
  const aprovado = pagamento?.status === 'PAYMENT_APPROVED'
  const cancelado = tipo === 'cancelado'
  const titulo = aprovado ? 'Pagamento aprovado' : cancelado ? 'Pagamento nao finalizado' : 'Retorno do pagamento'
  const descricao = aprovado
    ? 'Sua conta Pro foi liberada. Entre novamente para acessar o painel.'
    : cancelado
      ? 'O pagamento nao foi concluido. Voce pode voltar para a tela de pagamento e tentar novamente.'
      : 'Recebemos o retorno da Cakto. Confirme o status para liberar a conta quando o pagamento for aprovado.'

  async function consultarStatus() {
    if (!pagamento?.empresaId || !pagamento?.id) {
      setErro('Nao encontramos um pagamento pendente neste navegador. Volte para a tela de pagamento.')
      return
    }
    setErro('')
    setCarregando(true)
    try {
      const resultado = await appApi.verificarPagamentoPlano(pagamento.empresaId, pagamento.id)
      const novoPendente = {
        ...pendente,
        pagamentoPlano: resultado.pagamento,
        assinatura: resultado.assinatura || pendente?.assinatura,
        mensagem: resultado.mensagem,
        statusConta: resultado.statusVerificacao,
      }
      setPendente(novoPendente)
      if (resultado.statusVerificacao === 'APPROVED') {
        limparPagamentoPendente()
      } else if (resultado.mensagem) {
        setErro(resultado.mensagem)
      }
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel consultar o pagamento.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <main className="login-screen">
      <section className="login-panel payment-wait-panel">
        <img src={logoWhite} alt="gendaz" className="payment-wait-logo" />
        <span className="section-kicker">Plano Pro</span>
        <div className={aprovado ? 'payment-return-icon success' : cancelado ? 'payment-return-icon danger' : 'payment-return-icon'}>
          {aprovado ? <CheckCircle2 size={28} /> : <AlertCircle size={28} />}
        </div>
        <h1>{titulo}</h1>
        <p>{descricao}</p>

        {pagamento && (
          <div className="payment-wait-card">
            <div>
              <small>Status atual</small>
              <strong>{statusTexto[pagamento.status] || pagamento.status}</strong>
            </div>
            <StatusBadge status={pagamento.status || 'PAYMENT_PENDING'} />
          </div>
        )}

        {location.search && <small className="payment-return-note">Retorno recebido da Cakto.</small>}
        {erro && <p className="form-error">{erro}</p>}

        <div className="payment-wait-actions">
          {!aprovado && (
            <Button type="button" onClick={consultarStatus} disabled={carregando}>
              <RefreshCw size={16} /> {carregando ? 'Verificando...' : 'Ja paguei, verificar'}
            </Button>
          )}
          {aprovado ? <Link to="/login" className="btn btn-primary">Entrar na conta</Link> : <Link to="/pagamento-pendente" className="btn btn-secondary">Voltar ao pagamento</Link>}
          <Link to="/" className="btn btn-secondary">Voltar ao site</Link>
        </div>
      </section>
    </main>
  )
}
