import { Ban, BarChart2, CheckCircle2, Eye, LogOut, Pencil, Power, RefreshCw, Search, ShieldCheck, XCircle } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { adminApi } from '../../api/adminApi.js'
import AnimatedBackground from '../../components/AnimatedBackground.jsx'
import Button from '../../components/Button.jsx'
import Modal from '../../components/Modal.jsx'
import StatusBadge from '../../components/StatusBadge.jsx'
import Table from '../../components/Table.jsx'
import { useAuth } from '../../contexts/AuthContext.jsx'

const abas = ['Dashboard', 'Usuarios', 'Pagamentos', 'Aprovar Pagamentos', 'Chamados', 'Logs', 'Configuracoes']
const STATUS_PAGAMENTO_CONFIRMADO = new Set([
  'PAGO',
  'PAGA',
  'CONFIRMADO',
  'CONFIRMADA',
  'APROVADO',
  'APPROVED',
  'PAID',
  'PAYMENT_APPROVED',
  'PURCHASE_APPROVED',
])

function todayIso() {
  const hoje = new Date()
  const offset = hoje.getTimezoneOffset() * 60000
  return new Date(hoje.getTime() - offset).toISOString().slice(0, 10)
}

function statusNormalizado(valor) {
  return String(valor || '').toUpperCase()
}

function moeda(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function pagamentoConfirmado(status) {
  return STATUS_PAGAMENTO_CONFIRMADO.has(statusNormalizado(status))
}

function extrairDataPagamento(pagamento) {
  return String(pagamento?.dataPagamento || pagamento?.dataCriacao || pagamento?.data || pagamento?.createdAt || '').slice(0, 10)
}

function diasDoMesAtual() {
  const hoje = new Date(`${todayIso()}T12:00:00`)
  return new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0).getDate()
}

function buildReceitaMes(pagamentos) {
  const hoje = new Date(`${todayIso()}T12:00:00`)
  const dias = diasDoMesAtual()
  const mapaReceita = {}

  pagamentos.forEach((p) => {
    if (!pagamentoConfirmado(p.status)) return
    const dia = extrairDataPagamento(p)
    if (!dia || !dia.startsWith(`${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}`)) return
    mapaReceita[dia] = (mapaReceita[dia] || 0) + Number(p.valor || 0)
  })

  const inicioMes = new Date(hoje.getFullYear(), hoje.getMonth(), 1, 12, 0, 0, 0)
  const resultado = []
  for (let i = 0; i < dias; i++) {
    const data = new Date(inicioMes)
    data.setDate(data.getDate() + i)
    const iso = data.toISOString().slice(0, 10)
    const label = data.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
    resultado.push({ iso, label, valor: mapaReceita[iso] || 0 })
  }
  return resultado
}

function suavizarPontos(pontos) {
  if (pontos.length < 2) return ''
  const curvas = [`M ${pontos[0].x} ${pontos[0].y}`]
  for (let i = 0; i < pontos.length - 1; i++) {
    const atual = pontos[i]
    const proximo = pontos[i + 1]
    const pontoMeio = (atual.x + proximo.x) / 2
    curvas.push(`C ${pontoMeio} ${atual.y}, ${pontoMeio} ${proximo.y}, ${proximo.x} ${proximo.y}`)
  }
  return curvas.join(' ')
}

function GraficoLinha({ dados }) {
  const [tooltip, setTooltip] = useState(null)
  const temDados = dados.some((d) => d.valor > 0)
  const width = 760
  const height = 240
  const pLeft = 24
  const pRight = 18
  const pTop = 16
  const pBottom = 28
  const chartW = width - pLeft - pRight
  const chartH = height - pTop - pBottom
  const maxValor = Math.max(...dados.map((d) => d.valor), 1)
  const gridFracs = [0, 0.25, 0.5, 0.75, 1]

  const pontos = dados.map((d, index) => {
    const x = pLeft + (chartW * (dados.length > 1 ? index / (dados.length - 1) : 0))
    const y = pTop + chartH - ((d.valor || 0) / maxValor) * chartH
    return { ...d, x, y }
  })

  const linha = suavizarPontos(pontos)
  const area = `M ${pontos[0]?.x || pLeft} ${pTop + chartH} ${linha} L ${pontos[pontos.length - 1]?.x || pLeft} ${pTop + chartH} Z`

  if (!temDados) {
    return (
      <div className="admin-bar-chart-empty">
        <BarChart2 size={40} color="var(--color-primary)" />
        <p>Nenhum pagamento confirmado neste periodo.</p>
        <small>Os valores vao aparecer conforme os pagamentos forem entrando.</small>
      </div>
    )
  }

  return (
    <div className="admin-area-chart-shell">
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Grafico administrativo de receita mensal" style={{ width: '100%', height: '100%', overflow: 'visible' }}>
        {gridFracs.map((frac) => {
          const y = pTop + chartH * (1 - frac)
          const val = maxValor * frac
          return (
            <g key={frac}>
              <line x1={pLeft} y1={y} x2={width - pRight} y2={y} stroke="rgba(255,255,255,0.08)" strokeWidth={1} />
              <text x={pLeft - 6} y={y + 4} textAnchor="end" fontSize={10} fill="rgba(255,255,255,0.55)">
                {moeda(val)}
              </text>
            </g>
          )
        })}
        <defs>
          <linearGradient id="adminAreaFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--color-primary)" stopOpacity="0.72" />
            <stop offset="100%" stopColor="var(--color-primary)" stopOpacity="0.10" />
          </linearGradient>
        </defs>
        <path d={area} fill="url(#adminAreaFill)" opacity={0.9} />
        <path d={linha} fill="none" stroke="var(--color-primary)" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
        {pontos.map((p, index) => {
          const hasValue = p.valor > 0
          const isHovered = tooltip?.index === index
          return (
            <g key={p.iso}>
              <circle
                cx={p.x}
                cy={p.y}
                r={isHovered ? 5.5 : 4}
                fill={isHovered ? 'var(--color-primary)' : 'rgba(255,255,255,0.92)'}
                stroke="var(--color-primary)"
                strokeWidth={1.8}
                opacity={hasValue ? 1 : 0.45}
                onMouseEnter={() => {
                  if (!hasValue) return
                  setTooltip({ index, x: p.x, y: p.y, valor: p.valor, label: p.label })
                }}
                onMouseLeave={() => setTooltip(null)}
                style={{ cursor: hasValue ? 'pointer' : 'default' }}
              />
              {index % 5 === 0 && (
                <text x={p.x} y={height - 8} textAnchor="middle" fontSize={10} fill="rgba(255,255,255,0.55)">
                  {p.label}
                </text>
              )}
            </g>
          )
        })}
        {tooltip && (() => {
          const tx = Math.min(Math.max(tooltip.x, pLeft + 48), width - pRight - 48)
          const ty = Math.max(tooltip.y - 54, pTop)
          return (
            <g style={{ pointerEvents: 'none' }}>
              <rect x={tx - 52} y={ty} width={104} height={38} rx={8} fill="#111111" opacity={0.96} />
              <text x={tx} y={ty + 14} textAnchor="middle" fontSize={10} fill="rgba(255,255,255,0.62)">{tooltip.label}</text>
              <text x={tx} y={ty + 29} textAnchor="middle" fontSize={12} fill="#ffffff" fontWeight={700}>
                {moeda(tooltip.valor)}
              </text>
            </g>
          )
        })()}
      </svg>
    </div>
  )
}

function acaoModalTitulo(modal) {
  if (modal?.tipo === 'pagamento-aprovar') return `Aprovar pagamento de ${modal?.empresa || ''}`
  if (modal?.tipo === 'pagamento-desaprovar') return `Reverter pagamento de ${modal?.empresa || ''}`
  if (modal?.tipo === 'pagamento-detalhes') return `Detalhes do pagamento de ${modal?.empresa || ''}`
  if (modal?.tipo === 'empresa-ativar') return `Ativar conta de ${modal?.empresa || ''}`
  if (modal?.tipo === 'empresa-desativar') return `Desativar conta de ${modal?.empresa || ''}`
  if (modal?.tipo === 'empresa-editar') return `Editar empresa ${modal?.empresa || ''}`
  if (modal?.tipo === 'chamado-status') return `Atualizar chamado de ${modal?.assunto || modal?.empresa || ''}`
  return `Acessar conta de ${modal?.empresa || ''}`
}

function mensagemErroApi(error, fallback) {
  return error.response?.data?.mensagem
    || Object.values(error.response?.data?.campos || {})[0]
    || error.response?.data?.message
    || fallback
}

function contemTermo(item, termo, campos) {
  const normalizado = termo.trim().toLowerCase()
  if (!normalizado) return true
  return campos.some((campo) => String(item[campo] || '').toLowerCase().includes(normalizado))
}

function formatarDataHora(valor) {
  if (!valor) return '-'
  const texto = String(valor)
  const temTimezone = /([zZ]|[+-]\d{2}:?\d{2})$/.test(texto)
  const data = new Date(temTimezone ? texto : `${texto}Z`)
  if (Number.isNaN(data.getTime())) return '-'
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(data)
}

function rotuloPlano(valor) {
  const plano = String(valor || '').trim().toUpperCase()
  if (plano === 'BASICO') return 'Básico'
  if (plano === 'PRO') return 'Pro'
  return 'Plano não identificado'
}

export default function AdminDashboard() {
  const navigate = useNavigate()
  const { adminUsuario, adminLogout, iniciarImpersonacao, impersonation, encerrarImpersonacao } = useAuth()
  const [aba, setAba] = useState('Dashboard')
  const [dashboard, setDashboard] = useState(null)
  const [usuarios, setUsuarios] = useState([])
  const [pagamentos, setPagamentos] = useState([])
  const [pagamentosModeracao, setPagamentosModeracao] = useState([])
  const [logs, setLogs] = useState([])
  const [chamados, setChamados] = useState([])
  const [config, setConfig] = useState(null)
  const [planos, setPlanos] = useState([])
  const [modal, setModal] = useState(null)
  const [motivo, setMotivo] = useState('')
  const [transacaoId, setTransacaoId] = useState('')
  const [empresaEdicao, setEmpresaEdicao] = useState({ nomeFantasia: '', documento: '', telefone: '', email: '' })
  const [assinaturaEdicao, setAssinaturaEdicao] = useState({ planoId: '', diasPlano: 30 })
  const [chamadoEdicao, setChamadoEdicao] = useState({ status: 'EM_ANALISE', resposta: '' })
  const [erro, setErro] = useState('')
  const [aviso, setAviso] = useState('')
  const [carregandoAcao, setCarregandoAcao] = useState(false)
  const [recarregando, setRecarregando] = useState('')
  const [filtroPagamento, setFiltroPagamento] = useState({ status: '', plano: '' })
  const [filtroLog, setFiltroLog] = useState({ tipo: '', severidade: '' })
  const [pesquisaPagamento, setPesquisaPagamento] = useState('')
  const [pesquisaAprovacao, setPesquisaAprovacao] = useState('')
  const [pesquisaChamado, setPesquisaChamado] = useState('')
  const [pesquisaLog, setPesquisaLog] = useState('')
  const motivoValido = motivo.trim().length >= 8
  const motivoRestante = Math.max(0, 8 - motivo.trim().length)

  useEffect(() => {
    if (!aviso) return undefined
    const timer = setTimeout(() => setAviso(''), 3000)
    return () => clearTimeout(timer)
  }, [aviso])

  useEffect(() => {
    if (!erro) return undefined
    const timer = setTimeout(() => setErro(''), 5000)
    return () => clearTimeout(timer)
  }, [erro])

  async function carregarAdmin() {
    const [dashboardData, usuariosData, pagamentosData, chamadosData, logsData, configData, planosData] = await Promise.all([
      adminApi.dashboard(),
      adminApi.usuarios(),
      adminApi.pagamentos(),
      adminApi.chamados(),
      adminApi.logs(),
      adminApi.configuracoes(),
      adminApi.planos(),
    ])
    setDashboard(dashboardData)
    setUsuarios(usuariosData)
    setPagamentos(pagamentosData)
    setPagamentosModeracao(pagamentosData.filter((item) => ['PAYMENT_PENDING', 'PAYMENT_APPROVED'].includes(item.status)))
    setChamados(chamadosData)
    setLogs(logsData)
    setConfig(configData)
    setPlanos(planosData)
  }

  useEffect(() => {
    if (!adminUsuario) {
      navigate('/admin/login')
      return
    }
    carregarAdmin().catch(() => {
      setErro('Nao foi possivel carregar os dados do painel admin agora. Tente recarregar.')
    })
  }, [adminUsuario, navigate])

  useEffect(() => {
    if (!adminUsuario) return
    adminApi.pagamentos(filtroPagamento).then(setPagamentos).catch(() => {})
  }, [adminUsuario, filtroPagamento])

  async function recarregarAbaAtual() {
    if (recarregando) return
    setErro('')
    setAviso('')
    setRecarregando(aba)
    try {
      if (aba === 'Dashboard' || aba === 'Usuarios' || aba === 'Configuracoes') {
        await carregarAdmin()
      } else if (aba === 'Pagamentos') {
        setPagamentos(await adminApi.pagamentos(filtroPagamento))
      } else if (aba === 'Aprovar Pagamentos') {
        const pagamentosData = await adminApi.pagamentos()
        setPagamentosModeracao(pagamentosData.filter((item) => ['PAYMENT_PENDING', 'PAYMENT_APPROVED'].includes(item.status)))
      } else if (aba === 'Chamados') {
        setChamados(await adminApi.chamados())
      } else if (aba === 'Logs') {
        setLogs(await adminApi.logs())
      }
      setAviso('Dados atualizados.')
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel recarregar os dados agora.'))
    } finally {
      setRecarregando('')
    }
  }

  const metricas = useMemo(() => dashboard ? [
    ['Faturamento total', moeda(dashboard.faturamentoTotal)],
    ['Faturamento do mes', moeda(dashboard.faturamentoMes)],
    ['Pagamentos confirmados', dashboard.pagamentosConfirmados],
    ['Pagamentos pendentes', dashboard.pagamentosPendentes],
    ['Assinaturas ativas', dashboard.assinaturasAtivas],
    ['Empresas em teste', dashboard.empresasTesteGratis],
    ['Empresas vencidas', dashboard.empresasVencidas],
    ['Usuarios ativos', dashboard.usuariosAtivos],
    ['Novos cadastros', dashboard.novosCadastros],
  ] : [], [dashboard])

  const contasAtivas = dashboard?.assinaturasAtivas || 0
  const contasCanceladas = dashboard?.empresasVencidas || 0
  const contasTeste = dashboard?.empresasTesteGratis || 0
  const contasAtivasPct = Math.round((contasAtivas / Math.max(contasAtivas + contasCanceladas + contasTeste, 1)) * 100)
  const receitaMensalGrafico = buildReceitaMes(pagamentos)
  const pagamentosConfirmadosLista = pagamentos.filter((item) => pagamentoConfirmado(item.status))
  const pagamentosPendentesLista = pagamentos.filter((item) => statusNormalizado(item.status) === 'PENDENTE')
  const pagamentoMaisRecente = [...pagamentos]
    .sort((a, b) => String(b.dataPagamento || b.dataCriacao || b.data || '').localeCompare(String(a.dataPagamento || a.dataCriacao || a.data || '')))
    .slice(0, 5)
  const planoResumo = useMemo(() => {
    const mapa = {}
    ;(usuarios || []).forEach((item) => {
      const plano = rotuloPlano(item.plano)
      mapa[plano] = (mapa[plano] || 0) + 1
    })
    return Object.entries(mapa).sort((a, b) => b[1] - a[1]).slice(0, 4)
  }, [usuarios])

  const pagamentosFiltrados = useMemo(() => pagamentos.filter((item) => (
    contemTermo(item, pesquisaPagamento, ['empresa', 'responsavel', 'email', 'telefone', 'plano', 'status', 'gateway', 'externalPaymentId', 'paymentReference'])
  )), [pagamentos, pesquisaPagamento])

  const pagamentosModeracaoFiltrados = useMemo(() => pagamentosModeracao.filter((item) => (
    contemTermo(item, pesquisaAprovacao, ['empresa', 'responsavel', 'email', 'telefone', 'plano', 'status', 'statusEmpresa', 'externalPaymentId', 'paymentReference'])
  )), [pagamentosModeracao, pesquisaAprovacao])

  const chamadosFiltrados = useMemo(() => chamados.filter((item) => (
    contemTermo(item, pesquisaChamado, ['assunto', 'empresa', 'usuario', 'status', 'resposta'])
  )), [chamados, pesquisaChamado])

  const logsFiltrados = useMemo(() => logs.filter((item) => {
    const tipoOk = !filtroLog.tipo || item.tipo?.toLowerCase().includes(filtroLog.tipo.toLowerCase())
    const severidadeOk = !filtroLog.severidade || item.severidade === filtroLog.severidade
    const buscaOk = contemTermo(item, pesquisaLog, ['tipo', 'severidade', 'admin', 'usuario', 'empresa', 'descricao', 'motivo'])
    return tipoOk && severidadeOk && buscaOk
  }), [logs, filtroLog, pesquisaLog])

  function abrirModal(item, tipo) {
    setModal({ ...item, tipo })
    setMotivo('')
    setTransacaoId('')
    setEmpresaEdicao({
      nomeFantasia: item?.empresa || '',
      documento: item?.documento || '',
      telefone: item?.telefone || '',
      email: item?.emailEmpresa || item?.email || '',
    })
    setAssinaturaEdicao({
      planoId: item?.planoId || '',
      diasPlano: 30,
    })
    setChamadoEdicao({
      status: item?.status || 'EM_ANALISE',
      resposta: item?.resposta || '',
    })
    setErro('')
    setAviso('')
  }

  function atualizarEmpresaNaTabela(empresaAtualizada) {
    setUsuarios((atuais) => atuais.map((item) => (
      item.empresaId === empresaAtualizada.empresaId ? { ...item, ...empresaAtualizada } : item
    )))
  }

  function sair() {
    adminLogout()
    navigate('/admin/login')
  }

  function validarMotivo() {
    if (!motivoValido) {
      setErro(`Informe um motivo com pelo menos 8 caracteres. Faltam ${motivoRestante} caractere${motivoRestante === 1 ? '' : 's'}.`)
      return false
    }
    setErro('')
    return true
  }

  async function confirmarImpersonacao() {
    if (!modal) return
    setCarregandoAcao(true)
    try {
      const session = await adminApi.impersonar(modal.empresaId, motivo.trim() || null)
      iniciarImpersonacao(session)
      setModal(null)
      setAviso(`Contexto da empresa ${modal.empresa} carregado no painel admin.`)
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel acessar esta conta agora.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  async function aprovarPagamentoManual() {
    if (!modal) return
    setCarregandoAcao(true)
    try {
      await adminApi.aprovarPagamentoManualmente(modal.id)
      await carregarAdmin()
      setAviso('Pagamento aprovado. Conta, assinatura e plano foram sincronizados.')
      setModal(null)
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel aprovar o pagamento manualmente.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  async function desaprovarPagamentoManual() {
    if (!modal || !validarMotivo()) return
    setCarregandoAcao(true)
    try {
      await adminApi.desaprovarPagamentoManualmente(modal.id, {
        motivo: motivo.trim(),
        transacaoId: transacaoId.trim() || null,
      })
      await carregarAdmin()
      setAviso('Pagamento revertido e conta atualizada conforme a assinatura.')
      setModal(null)
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel reverter o pagamento.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  async function atualizarStatusEmpresa() {
    if (!modal || !validarMotivo()) return
    setCarregandoAcao(true)
    try {
      let empresaAtualizada
      if (modal.tipo === 'empresa-ativar') {
        empresaAtualizada = await adminApi.ativarEmpresa(modal.empresaId, motivo.trim())
      } else {
        empresaAtualizada = await adminApi.desativarEmpresa(modal.empresaId, motivo.trim())
      }
      atualizarEmpresaNaTabela(empresaAtualizada)
      setModal(null)
      setAviso(modal.tipo === 'empresa-ativar' ? 'Conta ativada com sucesso.' : 'Conta desativada com sucesso.')
      carregarAdmin().catch(() => {
        setErro('A conta foi atualizada, mas nao foi possivel recarregar a tabela agora.')
      })
    } catch (error) {
      setModal(null)
      setErro(mensagemErroApi(error, modal.tipo === 'empresa-ativar' ? 'Nao foi possivel ativar a conta.' : 'Nao foi possivel desativar a conta.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  async function salvarEmpresaEditada() {
    if (!modal || !validarMotivo()) return
    setCarregandoAcao(true)
    try {
      const payload = {
        nomeFantasia: empresaEdicao.nomeFantasia.trim(),
        documento: String(empresaEdicao.documento || '').replace(/\D/g, ''),
        telefone: String(empresaEdicao.telefone || '').replace(/[^\d()+\-\s]/g, '').trim(),
        email: empresaEdicao.email.trim().toLowerCase(),
        planoId: assinaturaEdicao.planoId ? Number(assinaturaEdicao.planoId) : null,
        diasPlano: assinaturaEdicao.diasPlano ? Number(assinaturaEdicao.diasPlano) : null,
        motivo: motivo.trim(),
      }
      const empresaAtualizada = await adminApi.atualizarEmpresa(modal.empresaId, {
        ...payload,
      })
      atualizarEmpresaNaTabela(empresaAtualizada)
      setModal(null)
      setAviso('Dados da empresa atualizados com sucesso.')
      carregarAdmin().catch(() => {
        setErro('A empresa foi atualizada, mas nao foi possivel recarregar a tabela agora.')
      })
    } catch (error) {
      setModal(null)
      setErro(mensagemErroApi(error, 'Nao foi possivel atualizar os dados da empresa.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  async function salvarChamadoEditado() {
    if (!modal) return
    setCarregandoAcao(true)
    try {
      await adminApi.atualizarChamado(modal.id, {
        status: chamadoEdicao.status,
        resposta: chamadoEdicao.resposta?.trim() || null,
      })
      await carregarAdmin()
      setModal(null)
      setAviso('Chamado atualizado com sucesso.')
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel atualizar o chamado.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  function renderAcoesPagamento(item) {
    return (
      <div className="table-actions">
        <button className="icon-btn" type="button" title="Ver detalhes" onClick={() => abrirModal(item, 'pagamento-detalhes')}>
          <Eye size={16} />
        </button>
        {item.status === 'PAYMENT_PENDING' && (
          <button className="icon-btn" type="button" title="Aprovar manualmente" onClick={() => abrirModal(item, 'pagamento-aprovar')}>
            <CheckCircle2 size={16} />
          </button>
        )}
        {['PAYMENT_PENDING', 'PAYMENT_APPROVED'].includes(item.status) && (
          <button className="icon-btn" type="button" title="Desaprovar pagamento" onClick={() => abrirModal(item, 'pagamento-desaprovar')}>
            <XCircle size={16} />
          </button>
        )}
      </div>
    )
  }

  return (
    <main className="admin-shell">
      <AnimatedBackground />
      <aside className="admin-sidebar">
        <div>
          <ShieldCheck size={22} />
          <strong>gendaz Admin</strong>
          <span>{adminUsuario?.email}</span>
        </div>
        {abas.map((item) => (
          <button key={item} type="button" className={aba === item ? 'active' : ''} onClick={() => setAba(item)}>
            {item}
          </button>
        ))}
        <button type="button" onClick={sair}><LogOut size={16} /> Sair</button>
      </aside>

      <section className="admin-content">
        {impersonation && (
          <div className="impersonation-banner" style={{ margin: '0 0 16px' }}>
            <strong>Contexto ativo: {impersonation.empresa}.</strong>
            <span>Voce continua no painel admin, sem trocar a sessao da empresa.</span>
            <button
              type="button"
              onClick={() => {
                encerrarImpersonacao()
                setAviso('Contexto da empresa encerrado.')
              }}
            >
              Encerrar contexto
            </button>
          </div>
        )}
        {(aviso || (!modal && erro)) && (
          <div className={`admin-toast ${aviso ? 'success' : 'error'}`} role="status">
            <span>{aviso || erro}</span>
            <button type="button" aria-label="Fechar notificacao" onClick={() => { setAviso(''); setErro('') }}>x</button>
          </div>
        )}

        {aba === 'Dashboard' && (
          <>
            <div className="page-title page-title-admin">
              <div>
                <span className="section-kicker">Super Admin</span>
                <h1>Dashboard administrativo</h1>
                <p>Visao tática da saude do Gendaz com contas, pagamentos e fluxo operacional.</p>
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} disabled={recarregando === 'Dashboard'}>
                  {recarregando === 'Dashboard' ? 'Recarregando...' : 'Recarregar'}
                </Button>
              </div>
            </div>
            <div className="admin-strategy-grid">
              <article className="admin-strategy-card">
                <span>Contas ativas</span>
                <strong>{contasAtivas}</strong>
                <small>{contasAtivasPct}% da base atual</small>
              </article>
              <article className="admin-strategy-card">
                <span>Contas canceladas</span>
                <strong>{contasCanceladas}</strong>
                <small>vencidas ou bloqueadas</small>
              </article>
              <article className="admin-strategy-card">
                <span>Contas em teste</span>
                <strong>{contasTeste}</strong>
                <small>periodo gratuito ativo</small>
              </article>
              <article className="admin-strategy-card admin-strategy-card--highlight">
                <span>Total ganho</span>
                <strong>{moeda(dashboard?.faturamentoTotal)}</strong>
                <small>{moeda(dashboard?.faturamentoMes)} neste mes</small>
              </article>
            </div>
            <div className="admin-metrics">
              {metricas.map(([label, value]) => (
                <article key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </article>
              ))}
            </div>
            <div className="admin-panels admin-panels--tactical">
              <section className="admin-tactical-panel">
                <div className="panel-head">
                  <div>
                    <span className="section-kicker">Financeiro</span>
                    <h2>Receita dos pagamentos</h2>
                    <p>Base confirmada por data de pagamento no mes corrente.</p>
                  </div>
                </div>
                <GraficoLinha dados={receitaMensalGrafico} />
              </section>
              <section className="admin-tactical-panel">
                <div className="panel-head">
                  <div>
                    <span className="section-kicker">Operacao</span>
                    <h2>Status geral das contas</h2>
                    <p>Leitura rapida da saude da base Gendaz.</p>
                  </div>
                </div>
                <div className="admin-status-stack">
                  <div className="admin-status-row">
                    <span>Ativas</span>
                    <strong>{contasAtivas}</strong>
                  </div>
                  <div className="admin-status-row">
                    <span>Canceladas</span>
                    <strong>{contasCanceladas}</strong>
                  </div>
                  <div className="admin-status-row">
                    <span>Teste</span>
                    <strong>{contasTeste}</strong>
                  </div>
                  <div className="admin-status-row">
                    <span>Usuarios ativos</span>
                    <strong>{dashboard?.usuariosAtivos || 0}</strong>
                  </div>
                </div>
                <div className="admin-mini-bars">
                  {planoResumo.map(([plano, total]) => (
                    <div key={plano} className="admin-mini-bar">
                      <div>
                        <span>{plano}</span>
                        <strong>{total}</strong>
                      </div>
                      <div className="admin-mini-bar-track">
                        <i style={{ width: `${Math.max(12, (total / Math.max(planoResumo[0]?.[1] || 1, 1)) * 100)}%` }} />
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            </div>
            <div className="admin-panels">
              <section>
                <h2>Receita</h2>
                {(dashboard?.receita || []).map((item) => (
                  <div className="admin-bar" key={item.periodo}>
                    <span>{item.periodo}</span>
                    <strong>{moeda(item.valor)}</strong>
                  </div>
                ))}
              </section>
              <section>
                <h2>Planos</h2>
                {(dashboard?.distribuicaoPlanos || []).map((item) => (
                  <div className="admin-bar" key={item.plano}>
                    <span>{rotuloPlano(item.plano)}</span>
                    <strong>{item.total}</strong>
                  </div>
                ))}
              </section>
              <section>
                <h2>Pagamentos recentes</h2>
                {pagamentoMaisRecente.length === 0 ? (
                  <div className="admin-empty-tactical">Sem pagamentos recentes.</div>
                ) : (
                  pagamentoMaisRecente.map((item) => (
                    <div className="admin-bar" key={`${item.id}-${item.dataPagamento || item.dataCriacao || item.data || ''}`}>
                      <span>{item.empresa || 'Empresa'}</span>
                      <strong>{moeda(item.valor)}</strong>
                    </div>
                  ))
                )}
              </section>
            </div>
            <div className="admin-panels">
              <section>
                <h2>Pagamentos confirmados</h2>
                <div className="admin-bar-row">
                  <span>Confirmados no periodo</span>
                  <strong>{pagamentosConfirmadosLista.length}</strong>
                </div>
                <div className="admin-bar-row">
                  <span>Pendentes</span>
                  <strong>{pagamentosPendentesLista.length}</strong>
                </div>
              </section>
              <section>
                <h2>Resumo pratico</h2>
                <div className="admin-bar-row">
                  <span>Total ganho</span>
                  <strong>{moeda(dashboard?.faturamentoTotal)}</strong>
                </div>
                <div className="admin-bar-row">
                  <span>Faturamento do mes</span>
                  <strong>{moeda(dashboard?.faturamentoMes)}</strong>
                </div>
              </section>
            </div>
          </>
        )}

        {aba === 'Usuarios' && (
          <section className="admin-section">
            <div className="page-title page-title-admin">
              <div>
                <span className="section-kicker">Cadastro e contas</span>
                <h1>Usuarios e empresas</h1>
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} disabled={recarregando === 'Usuarios'}>
                  {recarregando === 'Usuarios' ? 'Recarregando...' : 'Recarregar'}
                </Button>
              </div>
            </div>
            <Table columns={['Empresa', 'Responsavel', 'E-mail', 'Telefone', 'Plano', 'Empresa', 'Assinatura', 'Ultimo pagamento', 'Acoes']}>
              {usuarios.map((item) => (
                <tr key={item.empresaId}>
                  <td>{item.empresa}</td>
                  <td>{item.responsavel}</td>
                  <td>{item.email}</td>
                  <td>{item.telefone}</td>
                  <td>{rotuloPlano(item.plano)}</td>
                  <td><StatusBadge status={item.statusEmpresa} /></td>
                  <td><StatusBadge status={item.statusAssinatura} /></td>
                  <td>{formatarDataHora(item.ultimoPagamento)}</td>
                  <td>
                    <div className="table-actions">
                      <button className="icon-btn" type="button" title="Editar empresa" onClick={() => abrirModal(item, 'empresa-editar')}>
                        <Pencil size={16} />
                      </button>
                      <button className="icon-btn" type="button" title="Acessar conta" onClick={() => abrirModal(item, 'impersonar')}>
                        <Eye size={16} />
                      </button>
                      <button
                        className="icon-btn"
                        type="button"
                        title={item.statusEmpresa === 'ATIVA' ? 'Desativar conta' : 'Ativar conta'}
                        onClick={() => abrirModal(item, item.statusEmpresa === 'ATIVA' ? 'empresa-desativar' : 'empresa-ativar')}
                      >
                        {item.statusEmpresa === 'ATIVA' ? <Ban size={16} /> : <Power size={16} />}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </Table>
          </section>
        )}

        {aba === 'Pagamentos' && (
          <section className="admin-section">
            <h1>Pagamentos</h1>
            <div className="admin-filters admin-filters-payments">
              <label className="search-shell">
                <input
                  maxLength={120}
                  value={pesquisaPagamento}
                  onChange={(event) => setPesquisaPagamento(event.target.value)}
                  placeholder="Pesquisar por empresa, cliente, e-mail ou telefone"
                />
              </label>
              <select value={filtroPagamento.status} onChange={(event) => setFiltroPagamento((atual) => ({ ...atual, status: event.target.value }))}>
                <option value="">Todos os status</option>
                <option value="PAGO">Pagos</option>
                <option value="PENDENTE">Pendentes</option>
                <option value="PAYMENT_PENDING">Aguardando pagamento</option>
                <option value="PAYMENT_APPROVED">Pagamento aprovado</option>
                <option value="CANCELADO">Cancelados</option>
                <option value="PAYMENT_REJECTED">Recusados</option>
                <option value="PAYMENT_EXPIRED">Vencidos</option>
              </select>
              <select value={filtroPagamento.plano} onChange={(event) => setFiltroPagamento((atual) => ({ ...atual, plano: event.target.value }))}>
                <option value="">Todos os planos</option>
                <option value="BASICO">Básico</option>
                <option value="PRO">Pro</option>
              </select>
              <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} disabled={recarregando === 'Pagamentos'}>
                {recarregando === 'Pagamentos' ? 'Recarregando...' : 'Recarregar'}
              </Button>
            </div>
            <Table columns={['Empresa', 'Responsavel', 'E-mail', 'Telefone', 'Plano', 'Valor', 'Gateway', 'Status', 'Empresa', 'Vencimento', 'Pagamento', 'Acoes']}>
              {pagamentosFiltrados.map((item) => (
                <tr key={item.id}>
                  <td>{item.empresa}</td>
                  <td>{item.responsavel || '-'}</td>
                  <td>{item.email || '-'}</td>
                  <td>{item.telefone || '-'}</td>
                  <td>{rotuloPlano(item.plano)}</td>
                  <td>{moeda(item.valor)}</td>
                  <td>{item.gateway}</td>
                  <td><StatusBadge status={item.status} /></td>
                  <td><StatusBadge status={item.statusEmpresa} /></td>
                  <td>{formatarDataHora(item.vencimento)}</td>
                  <td>{formatarDataHora(item.dataPagamento)}</td>
                  <td>{renderAcoesPagamento(item)}</td>
                </tr>
              ))}
            </Table>
          </section>
        )}

        {aba === 'Aprovar Pagamentos' && (
          <section className="admin-section">
            <h1>Aprovar pagamentos</h1>
            <div className="admin-filters">
              <label className="search-shell">
                <input
                  maxLength={120}
                  value={pesquisaAprovacao}
                  onChange={(event) => setPesquisaAprovacao(event.target.value)}
                  placeholder="Pesquisar por empresa, responsavel, e-mail ou telefone"
                />
              </label>
              <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} disabled={recarregando === 'Aprovar Pagamentos'}>
                {recarregando === 'Aprovar Pagamentos' ? 'Recarregando...' : 'Recarregar'}
              </Button>
            </div>
            <Table columns={['Empresa', 'Responsavel', 'E-mail', 'Telefone', 'Plano', 'Valor', 'Status pagamento', 'Status empresa', 'Referencia', 'Provider ID', 'Criado em', 'Acoes']}>
              {pagamentosModeracaoFiltrados.map((item) => (
                <tr key={item.id}>
                  <td>{item.empresa}</td>
                  <td>{item.responsavel || '-'}</td>
                  <td>{item.email || '-'}</td>
                  <td>{item.telefone || '-'}</td>
                  <td>{rotuloPlano(item.plano)}</td>
                  <td>{moeda(item.valor)}</td>
                  <td><StatusBadge status={item.status} /></td>
                  <td><StatusBadge status={item.statusEmpresa} /></td>
                  <td>{item.paymentReference || '-'}</td>
                  <td>{item.externalPaymentId || '-'}</td>
                  <td>{formatarDataHora(item.dataCriacao)}</td>
                  <td>{renderAcoesPagamento(item)}</td>
                </tr>
              ))}
            </Table>
          </section>
        )}

        {aba === 'Logs' && (
          <section className="admin-section">
            <h1>Logs / Auditoria</h1>
            <div className="admin-filters">
              <input
                value={pesquisaLog}
                maxLength={120}
                onChange={(event) => setPesquisaLog(event.target.value)}
                placeholder="Pesquisar por evento, empresa ou usuario"
              />
              <input value={filtroLog.tipo} onChange={(event) => setFiltroLog((atual) => ({ ...atual, tipo: event.target.value }))} placeholder="Filtrar por tipo" />
              <select value={filtroLog.severidade} onChange={(event) => setFiltroLog((atual) => ({ ...atual, severidade: event.target.value }))}>
                <option value="">Todas as severidades</option>
                <option value="INFO">INFO</option>
                <option value="WARNING">WARNING</option>
                <option value="SECURITY">SECURITY</option>
                <option value="ERROR">ERROR</option>
              </select>
              <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} disabled={recarregando === 'Logs'}>
                {recarregando === 'Logs' ? 'Recarregando...' : 'Recarregar'}
              </Button>
            </div>
            <Table columns={['Tipo', 'Severidade', 'Admin', 'Empresa', 'Descricao', 'Motivo', 'Data']}>
              {logsFiltrados.map((item) => (
                <tr key={item.id}>
                  <td>{item.tipo}</td>
                  <td><StatusBadge status={item.severidade} /></td>
                  <td>{item.admin || '-'}</td>
                  <td>{item.empresa || '-'}</td>
                  <td>{item.descricao}</td>
                  <td>{item.motivo || '-'}</td>
                  <td>{formatarDataHora(item.dataCriacao)}</td>
                </tr>
              ))}
            </Table>
          </section>
        )}

        {aba === 'Chamados' && (
          <section className="admin-section">
            <h1>Chamados</h1>
            <div className="admin-filters">
              <label className="search-shell">
                <input
                  maxLength={120}
                  value={pesquisaChamado}
                  onChange={(event) => setPesquisaChamado(event.target.value)}
                  placeholder="Pesquisar por assunto, empresa, usuario ou status"
                />
              </label>
              <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} disabled={recarregando === 'Chamados'}>
                {recarregando === 'Chamados' ? 'Recarregando...' : 'Recarregar'}
              </Button>
            </div>
            <Table columns={['Assunto', 'Empresa', 'Usuario', 'Status', 'Resposta', 'Data', 'Acoes']}>
              {chamadosFiltrados.map((item) => (
                <tr key={item.id}>
                  <td>{item.assunto}</td>
                  <td>{item.empresa}</td>
                  <td>{item.usuario}</td>
                  <td><StatusBadge status={item.status} /></td>
                  <td>{item.resposta || '-'}</td>
                  <td>{formatarDataHora(item.dataCriacao)}</td>
                  <td>
                    <div className="table-actions">
                      <button className="icon-btn" type="button" title="Atualizar chamado" onClick={() => abrirModal(item, 'chamado-status')}>
                        <Pencil size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </Table>
          </section>
        )}

        {aba === 'Configuracoes' && (
          <section className="admin-section admin-config">
            <div className="page-title page-title-admin">
              <div>
                <span className="section-kicker">Admin seguro</span>
                <h1>Configuracoes seguras</h1>
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} disabled={recarregando === 'Configuracoes'}>
                  {recarregando === 'Configuracoes' ? 'Recarregando...' : 'Recarregar'}
                </Button>
              </div>
            </div>
            <div><span>PAYMENT_PROVIDER</span><strong>{config?.paymentProvider}</strong></div>
            <div><span>Frontend</span><strong>{config?.frontendUrl}</strong></div>
            <div><span>API</span><strong>{config?.apiUrl}</strong></div>
            <div><span>Status</span><strong>{config?.statusSistema}</strong></div>
            <div><span>Secrets</span><strong>{config?.secrets}</strong></div>
          </section>
        )}
      </section>

      <Modal title={acaoModalTitulo(modal)} open={Boolean(modal)} onClose={() => setModal(null)}>
        <div className="confirm-box">
          {modal?.tipo === 'pagamento-detalhes' ? (
            <div className="admin-detail-list">
              <p><strong>Empresa:</strong> {modal.empresa}</p>
              <p><strong>Responsavel:</strong> {modal.responsavel || '-'}</p>
              <p><strong>E-mail:</strong> {modal.email || '-'}</p>
              <p><strong>Status:</strong> {modal.status}</p>
              <p><strong>Provider ID:</strong> {modal.externalPaymentId || '-'}</p>
              <p><strong>Referencia:</strong> {modal.paymentReference || '-'}</p>
              <p><strong>External reference:</strong> {modal.detalhes || '-'}</p>
            </div>
          ) : modal?.tipo === 'empresa-editar' ? (
            <>
              <p>Edite os dados basicos da empresa e confirme a alteracao no painel admin.</p>
              <div className="admin-form-grid">
                <label className="field">
                  <span>Nome fantasia</span>
                  <input
                    maxLength={100}
                    value={empresaEdicao.nomeFantasia}
                    onChange={(event) => setEmpresaEdicao((atual) => ({ ...atual, nomeFantasia: event.target.value }))}
                    placeholder="Nome da empresa"
                  />
                </label>
                <label className="field">
                  <span>Documento</span>
                  <input
                    maxLength={14}
                    value={empresaEdicao.documento}
                    onChange={(event) => setEmpresaEdicao((atual) => ({ ...atual, documento: event.target.value.replace(/\D/g, '') }))}
                    placeholder="Somente numeros"
                  />
                </label>
                <label className="field">
                  <span>Telefone</span>
                  <input
                    maxLength={15}
                    value={empresaEdicao.telefone}
                    onChange={(event) => setEmpresaEdicao((atual) => ({ ...atual, telefone: event.target.value.replace(/\D/g, '') }))}
                    placeholder="Somente numeros"
                  />
                </label>
                <label className="field">
                  <span>E-mail</span>
                  <input
                    maxLength={120}
                    value={empresaEdicao.email}
                    onChange={(event) => setEmpresaEdicao((atual) => ({ ...atual, email: event.target.value }))}
                    placeholder="E-mail da empresa"
                  />
                </label>
                <label className="field">
                  <span>Plano da conta</span>
                  <select
                    value={assinaturaEdicao.planoId}
                    onChange={(event) => setAssinaturaEdicao((atual) => ({ ...atual, planoId: event.target.value }))}
                  >
                    <option value="">Manter plano atual</option>
                    {planos.map((plano) => (
                      <option key={plano.id} value={plano.id}>
                        {plano.nome} - {plano.descricao}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span>Dias do plano</span>
                  <input
                    type="number"
                    min={1}
                    max={3650}
                    value={assinaturaEdicao.diasPlano}
                    onChange={(event) => setAssinaturaEdicao((atual) => ({ ...atual, diasPlano: event.target.value }))}
                    placeholder="Ex: 30"
                  />
                  <small className="field-hint">Define por quantos dias o plano vai valer a partir de hoje.</small>
                </label>
              </div>
              <label className="field">
                <span>Motivo obrigatorio</span>
                <textarea
                  value={motivo}
                  minLength={8}
                  maxLength={500}
                  onChange={(event) => {
                    setMotivo(event.target.value)
                    if (erro) setErro('')
                  }}
                  placeholder="Descreva o motivo da alteracao"
                />
                <small className={motivo.length >= 500 || (!motivoValido && motivo.length > 0) ? 'field-hint limit-reached' : 'field-hint'}>
                  {motivo.length >= 500
                    ? 'Limite de caracteres atingido.'
                    : motivoValido
                      ? 'Motivo valido para auditoria.'
                      : `Informe pelo menos 8 caracteres. Faltam ${motivoRestante}.`}
                  <strong>{motivo.length}/500</strong>
                </small>
              </label>
            </>
          ) : modal?.tipo === 'chamado-status' ? (
            <>
              <p>Atualize o status do chamado e registre uma resposta quando necessario.</p>
              <div className="admin-form-grid">
                <label className="field">
                  <span>Status</span>
                  <select
                    value={chamadoEdicao.status}
                    onChange={(event) => setChamadoEdicao((atual) => ({ ...atual, status: event.target.value }))}
                  >
                    <option value="ABERTO">Aberto</option>
                    <option value="PENDENTE">Pendente</option>
                    <option value="EM_ANALISE">Em análise</option>
                    <option value="EM_ANDAMENTO">Em andamento</option>
                    <option value="RESOLVIDO">Resolvido</option>
                    <option value="NAO_RESOLVIDO">Não resolvido</option>
                    <option value="FECHADO">Fechado</option>
                  </select>
                </label>
              </div>
              <label className="field">
                <span>Resposta para o chamado</span>
                <textarea
                  value={chamadoEdicao.resposta}
                  maxLength={1200}
                  onChange={(event) => setChamadoEdicao((atual) => ({ ...atual, resposta: event.target.value }))}
                  placeholder="Registre uma resposta ou observacao para a equipe"
                />
                <small className={chamadoEdicao.resposta.length >= 1200 ? 'field-hint limit-reached' : 'field-hint'}>
                  <strong>{chamadoEdicao.resposta.length}/1200</strong>
                </small>
              </label>
            </>
          ) : (
            <>
              <p>
                {modal?.tipo === 'pagamento-aprovar'
                  ? 'Tem certeza que deseja aprovar este pagamento?'
                  : modal?.tipo === 'pagamento-desaprovar'
                    ? `Confirme a reversao do pagamento de ${modal?.empresa}.`
                    : modal?.tipo === 'empresa-ativar'
                      ? `Confirme a ativacao da conta de ${modal?.empresa}.`
                      : modal?.tipo === 'empresa-desativar'
                        ? `Confirme o bloqueio da conta de ${modal?.empresa}.`
                        : 'Tem certeza que deseja entrar nesta conta?'}
              </p>
              {!['pagamento-aprovar', 'impersonar'].includes(modal?.tipo) && (
                <label className="field">
                  <span>Motivo obrigatorio</span>
                  <textarea
                    value={motivo}
                    minLength={8}
                    maxLength={500}
                    onChange={(event) => {
                      setMotivo(event.target.value)
                      if (erro) setErro('')
                    }}
                    placeholder="Descreva o motivo da acao"
                  />
                  <small className={motivo.length >= 500 || (!motivoValido && motivo.length > 0) ? 'field-hint limit-reached' : 'field-hint'}>
                    {motivo.length >= 500
                      ? 'Limite de caracteres atingido.'
                      : motivoValido
                        ? 'Motivo valido para auditoria.'
                        : `Informe pelo menos 8 caracteres. Faltam ${motivoRestante}.`}
                    <strong>{motivo.length}/500</strong>
                  </small>
                </label>
              )}
              {modal?.tipo === 'pagamento-desaprovar' && (
                <label className="field">
                  <span>ID/transacao da Cakto</span>
                  <input value={transacaoId} onChange={(event) => setTransacaoId(event.target.value)} placeholder="Informe o identificador do pagamento, se houver" />
                </label>
              )}
            </>
          )}
          {erro && <p className="form-error">{erro}</p>}
          <div className="confirm-actions">
            <Button variant="secondary" onClick={() => setModal(null)}>Cancelar</Button>
            {modal?.tipo === 'pagamento-aprovar'
              ? <Button icon={CheckCircle2} disabled={carregandoAcao} onClick={aprovarPagamentoManual}>{carregandoAcao ? 'Aprovando...' : 'Confirmar aprovacao'}</Button>
              : modal?.tipo === 'pagamento-desaprovar'
                ? <Button icon={XCircle} disabled={carregandoAcao || !motivoValido} onClick={desaprovarPagamentoManual}>{carregandoAcao ? 'Revertendo...' : 'Desaprovar pagamento'}</Button>
                : modal?.tipo === 'empresa-ativar' || modal?.tipo === 'empresa-desativar'
                  ? <Button icon={modal?.tipo === 'empresa-ativar' ? Power : Ban} disabled={carregandoAcao || !motivoValido} onClick={atualizarStatusEmpresa}>{carregandoAcao ? 'Salvando...' : 'Confirmar'}</Button>
                  : modal?.tipo === 'empresa-editar'
                    ? <Button icon={Pencil} disabled={carregandoAcao || !motivoValido} onClick={salvarEmpresaEditada}>{carregandoAcao ? 'Salvando...' : 'Salvar alteracoes'}</Button>
                    : modal?.tipo === 'chamado-status'
                      ? <Button icon={CheckCircle2} disabled={carregandoAcao} onClick={salvarChamadoEditado}>{carregandoAcao ? 'Salvando...' : 'Salvar chamado'}</Button>
                      : modal?.tipo === 'pagamento-detalhes'
                        ? null
                        : <Button icon={Search} disabled={carregandoAcao} onClick={confirmarImpersonacao}>{carregandoAcao ? 'Acessando...' : 'Confirmar acesso'}</Button>}
          </div>
        </div>
      </Modal>
    </main>
  )
}
