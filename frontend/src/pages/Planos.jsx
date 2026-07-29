import { Check, Copy, ExternalLink, RefreshCw, ShieldCheck } from 'lucide-react'
import QRCode from 'qrcode'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import ScrollReveal from '../components/ScrollReveal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { appApi } from '../api/appApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { checkoutAtivo, checkoutExpirado } from '../utils/checkoutUtils.js'

const planosBase = [
  {
    codigo: 'BASICO',
    nome: 'Plano Basico',
    subtitulo: 'Agenda simples',
    extra: '7 dias gratis',
    descricao: 'Para organizar conversas, agenda, clientes e servicos no mesmo painel.',
    beneficios: ['Agenda com atendimento organizado', 'Cadastro de clientes pelo painel', 'Cadastro de até 4 serviços', 'Confirmação de consulta automatizada', 'Cancelamento e remarcação automatizados'],
    indicadoPara: ['Clinicas pequenas', 'Atendimento individual', 'Rotina de agenda e conversas'],
    naoInclui: ['Profissionais', 'Financeiro', 'Pagamentos', 'Relatorios'],
    cta: 'Comecar no Basico',
    precoFallback: 39.00,
  },
  {
    codigo: 'PRO',
    nome: 'Plano Pro',
    subtitulo: 'Gestão com financeiro simples',
    descricao: 'Para acompanhar agenda, profissionais, pagamentos e indicadores em um só lugar.',
    beneficios: ['Tudo que o Básico oferece', 'Até 3 usuários por conta', 'CRM automatizado para relacionamento', 'Insights para apoiar a gestão', 'Profissionais ilimitados', 'Serviços ilimitados'],
    indicadoPara: ['Equipes de atendimento', 'Serviços com cobrança recorrente', 'Operação com acompanhamento diário'],
    cta: 'Assinar Pro',
    precoFallback: 89.00,
    destaque: true,
  },
]

const statusPagamentoTexto = {
  PAYMENT_PENDING: 'Aguardando pagamento',
  PAYMENT_APPROVED: 'Pagamento aprovado',
  PAYMENT_REJECTED: 'Pagamento recusado',
  PAYMENT_CANCELED: 'Pagamento cancelado',
  PAYMENT_EXPIRED: 'Pagamento expirado',
}

function formatarPreco(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
  }) + '/mes'
}

async function gerarQrCodeDataUrl(texto) {
  if (!texto) return null
  return QRCode.toDataURL(texto, {
    errorCorrectionLevel: 'M',
    margin: 1,
    width: 320,
    color: {
      dark: '#111111',
      light: '#FFFFFF',
    },
  })
}

export default function Planos() {
  const navigate = useNavigate()
  const [data] = useLocalData('planos')
  const { usuario, atualizarPlanoAtual, atualizarUsuario } = useAuth()
  const [metodoPagamento, setMetodoPagamento] = useState('PIX')
  const [pagamentoPlano, setPagamentoPlano] = useState(() => usuario?.pagamentoPlano || null)
  const [carregando, setCarregando] = useState(false)
  const [copiado, setCopiado] = useState(false)
  const [erro, setErro] = useState('')
  const [qrDataUrl, setQrDataUrl] = useState('')

  const planos = useMemo(() => planosBase.map((plano) => {
    const planoApi = data.planos?.find((item) => String(item.nome).toUpperCase() === plano.codigo)
    return {
      ...plano,
      preco: formatarPreco(planoApi?.valorMensal ?? plano.precoFallback),
    }
  }), [data.planos])
  const checkoutAtivoPlano = checkoutAtivo(pagamentoPlano)
  const checkoutExpiradoPlano = checkoutExpirado(pagamentoPlano)

  useEffect(() => {
    if (!usuario?.empresaId) return
    atualizarPlanoAtual().catch(() => null)
    appApi.listarPagamentosPlano(usuario.empresaId)
      .then((pagamentos) => setPagamentoPlano(pagamentos?.[0] || usuario?.pagamentoPlano || null))
      .catch(() => null)
  }, [usuario?.empresaId])

  useEffect(() => {
    let ativo = true
    async function gerarQr() {
      const copia = pagamentoPlano?.pixCopiaECola
      if (!copia || pagamentoPlano?.pixQrCodeBase64) {
        setQrDataUrl('')
        return
      }
      const url = await gerarQrCodeDataUrl(copia)
      if (ativo) setQrDataUrl(url || '')
    }
    gerarQr().catch(() => null)
    return () => {
      ativo = false
    }
  }, [pagamentoPlano?.pixCopiaECola, pagamentoPlano?.pixQrCodeBase64])

  async function iniciarPagamentoPro() {
    if (!usuario) {
      navigate('/criar-conta?plano=PRO')
      return
    }
    if (usuario.plano === 'PRO') return

    setErro('')
    setCarregando(true)
    try {
      const pagamento = await appApi.iniciarPagamentoPro({
        empresaId: usuario.empresaId,
        metodoPagamento: metodoPagamento === 'CREDIT_CARD' ? 'CREDIT_CARD' : 'PIX_AUTO',
        plano: 'PRO',
        customerName: usuario.nome,
        customerEmail: usuario.email,
        customerPhone: usuario.telefone,
        customerDocType: usuario.documento ? 'cpf' : '',
        customerDocNumber: usuario.documento || '',
        antifraudProfilingAttemptReference: usuario.id ? `agendeasy-${usuario.id}` : '',
      })
      setPagamentoPlano(pagamento)
      atualizarUsuario({ pagamentoPlano: pagamento })
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel iniciar o pagamento.')
    } finally {
      setCarregando(false)
    }
  }

  async function iniciarPagamentoBasico() {
    if (!usuario) {
      navigate('/criar-conta?plano=BASICO')
      return
    }
    if (usuario.plano === 'BASICO') return

    setErro('')
    setCarregando(true)
    try {
      const pagamento = await appApi.iniciarPagamentoPlano({
        empresaId: usuario.empresaId,
        metodoPagamento: metodoPagamento === 'CREDIT_CARD' ? 'CREDIT_CARD' : 'PIX_AUTO',
        plano: 'BASICO',
        customerName: usuario.nome,
        customerEmail: usuario.email,
        customerPhone: usuario.telefone,
        customerDocType: usuario.documento ? 'cpf' : '',
        customerDocNumber: usuario.documento || '',
        antifraudProfilingAttemptReference: usuario.id ? `agendeasy-${usuario.id}` : '',
      })
      setPagamentoPlano(pagamento)
      atualizarUsuario({ pagamentoPlano: pagamento })
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel iniciar o pagamento.')
    } finally {
      setCarregando(false)
    }
  }

  async function atualizarStatusPagamento() {
    if (!usuario?.empresaId || !pagamentoPlano?.id) return
    setErro('')
    setCarregando(true)
    try {
      const resultado = await appApi.verificarPagamentoPlano(usuario.empresaId, pagamentoPlano.id)
      const pagamento = resultado.pagamento
      setPagamentoPlano(pagamento)
      atualizarUsuario({ pagamentoPlano: pagamento })
      if (resultado.statusVerificacao === 'APPROVED') {
        await atualizarPlanoAtual()
      } else if (resultado.mensagem) {
        setErro(resultado.mensagem)
      }
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel consultar o pagamento.')
    } finally {
      setCarregando(false)
    }
  }

  async function copiarPix() {
    if (!pagamentoPlano?.pixCopiaECola) return
    await navigator.clipboard.writeText(pagamentoPlano.pixCopiaECola)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2500)
  }

  function handlePlanClick(plano) {
    if (plano.codigo === 'PRO') {
      iniciarPagamentoPro()
      return
    }
    if (!usuario) {
      navigate(`/criar-conta?plano=${encodeURIComponent(plano.codigo)}`)
      return
    }
    if (plano.codigo === 'BASICO') {
      iniciarPagamentoBasico()
      return
    }
  }

  return (
    <section className="page">
      <div className="page-title plans-page-title">
        <span className="section-kicker">Comercial</span>
        <h1>Planos do atendimento</h1>
        <p>Escolha o plano que melhor se encaixa na rotina do seu atendimento.</p>
      </div>

      {usuario && (
        <div className="panel plans-page-title plan-payment-panel">
          <div className="panel-head">
            <div>
              <span className="section-kicker">Plano atual</span>
              <h2>{usuario.plano === 'PRO' ? 'Plano Pro ativo' : 'Plano Basico ativo'}</h2>
            </div>
          </div>

          {pagamentoPlano && (
            <div className="plan-payment-status">
              <div>
                <span>Status do pagamento</span>
                <strong>{statusPagamentoTexto[pagamentoPlano.status] || pagamentoPlano.status}</strong>
              </div>
              <StatusBadge status={pagamentoPlano.status} />
              {['PIX_AUTO', 'PIX'].includes(pagamentoPlano.metodoPagamento) && (
                <div className="plan-pix-box">
                  {pagamentoPlano.pixQrCodeBase64 ? (
                    <img className="pix-qr-image large" src={`data:image/png;base64,${pagamentoPlano.pixQrCodeBase64}`} alt="QR Code PIX" />
                  ) : qrDataUrl ? (
                    <img className="pix-qr-image large" src={qrDataUrl} alt="QR Code PIX" />
                  ) : null}
                  <small>{pagamentoPlano.pixCopiaECola || 'PIX ainda nao retornou o codigo copia e cola. Tente gerar novamente.'}</small>
                  {pagamentoPlano.dataExpiracao && <small>Vencimento: {new Date(pagamentoPlano.dataExpiracao).toLocaleString('pt-BR')}</small>}
                  {pagamentoPlano.pixCopiaECola && (
                    <button type="button" className="btn btn-secondary" onClick={copiarPix}>
                      <Copy size={16} /> {copiado ? 'Codigo copiado' : 'Copiar codigo PIX'}
                    </button>
                  )}
                  <small>Apos a aprovacao, sua conta Pro pode levar ate 15 minutos para ser liberada.</small>
                </div>
              )}
              {pagamentoPlano.metodoPagamento === 'CREDIT_CARD' && (
                <small>Finalize no checkout seguro da Cakto. Apos a aprovacao, sua conta Pro pode levar ate 15 minutos para ser liberada.</small>
              )}
              {checkoutAtivoPlano && (
                <a href={pagamentoPlano.checkoutUrl} target="_blank" rel="noreferrer" className="btn btn-primary"><ExternalLink size={16} /> Abrir checkout seguro</a>
              )}
              {pagamentoPlano?.checkoutUrl && checkoutExpiradoPlano && (
                <small className="plan-checkout-expired-note">Checkout expirado. Gere um novo pagamento para continuar.</small>
              )}
              <button type="button" className="btn btn-secondary" onClick={atualizarStatusPagamento} disabled={carregando}>
                <RefreshCw size={16} /> Ja paguei, verificar
              </button>
            </div>
          )}

          {erro && <p className="form-error">{erro}</p>}
        </div>
      )}

      <div className="plans-grid detailed plans-centered-grid">
        {planos.map((plano, index) => (
          <ScrollReveal className={plano.destaque ? 'plan-card highlight plan-card-sale' : 'plan-card plan-card-sale'} delay={index * 100} key={plano.nome}>
            {plano.destaque && <span className="recommended-badge">Mais recomendado</span>}
            <div className="plan-card-body">
              <div className="plan-head">
                <div>
                  <h2>{plano.nome}</h2>
                  <p className="plan-subtitle">{plano.subtitulo}</p>
                </div>
                {plano.destaque && <ShieldCheck size={20} />}
              </div>

              <div className="plan-price-block">
                {plano.extra && <span className="plan-price-extra">{plano.extra}</span>}
                <strong className="plan-price">{plano.preco}</strong>
              </div>

              <p className="plan-description">{plano.descricao}</p>

              <div className="plan-section">
                <h3>BenefÃ­cios</h3>
                <div className="plan-list">
                  {plano.beneficios.map((item) => (
                    <strong key={item} className={item === 'Tudo do BÃ¡sico' ? 'plan-list-tudo-basico' : ''}>
                      <Check size={16} />{item}
                    </strong>
                  ))}
                </div>
              </div>

              <div className="plan-section">
                <h3>Indicado para</h3>
                <div className="plan-list plan-list-muted">
                  {plano.indicadoPara.map((item) => <small key={item}>{item}</small>)}
                </div>
              </div>

              {plano.naoInclui && (
                <div className="plan-unavailable">
                  <span>O que nao inclui</span>
                  {plano.naoInclui.map((item) => <small key={item}>{item}</small>)}
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={() => handlePlanClick(plano)}
              className={plano.destaque ? 'btn btn-primary plan-action-link' : 'btn btn-secondary plan-action-link'}
              disabled={carregando || (usuario?.plano === plano.codigo)}
            >
              {usuario?.plano === plano.codigo ? 'Plano atual' : carregando ? 'Iniciando...' : plano.cta}
            </button>
          </ScrollReveal>
        ))}
      </div>
    </section>
  )
}

