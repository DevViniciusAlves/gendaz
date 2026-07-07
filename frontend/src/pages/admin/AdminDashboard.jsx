import { Ban, CheckCircle2, Eye, LogOut, Pencil, Power, RefreshCw, Search, ShieldCheck, XCircle } from 'lucide-react'
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

function moeda(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
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
  const { adminUsuario, adminLogout, iniciarImpersonacao } = useAuth()
  const [aba, setAba] = useState('Dashboard')
  const [dashboard, setDashboard] = useState(null)
  const [usuarios, setUsuarios] = useState([])
  const [pagamentos, setPagamentos] = useState([])
  const [pagamentosModeracao, setPagamentosModeracao] = useState([])
  const [logs, setLogs] = useState([])
  const [chamados, setChamados] = useState([])
  const [config, setConfig] = useState(null)
  const [modal, setModal] = useState(null)
  const [motivo, setMotivo] = useState('')
  const [transacaoId, setTransacaoId] = useState('')
  const [empresaEdicao, setEmpresaEdicao] = useState({ nomeFantasia: '', documento: '', telefone: '', email: '' })
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
    const [dashboardData, usuariosData, pagamentosData, chamadosData, logsData, configData] = await Promise.all([
      adminApi.dashboard(),
      adminApi.usuarios(),
      adminApi.pagamentos(),
      adminApi.chamados(),
      adminApi.logs(),
      adminApi.configuracoes(),
    ])
    setDashboard(dashboardData)
    setUsuarios(usuariosData)
    setPagamentos(pagamentosData)
    setPagamentosModeracao(pagamentosData.filter((item) => ['PAYMENT_PENDING', 'PAYMENT_APPROVED'].includes(item.status)))
    setChamados(chamadosData)
    setLogs(logsData)
    setConfig(configData)
  }

  useEffect(() => {
    if (!adminUsuario) {
      navigate('/admin/login')
      return
    }
    carregarAdmin().catch(() => {
      adminLogout()
      navigate('/admin/login')
    })
  }, [adminUsuario, adminLogout, navigate])

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
      navigate('/sistema/dashboard', { replace: true })
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
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} disabled={recarregando === 'Dashboard'}>
                  {recarregando === 'Dashboard' ? 'Recarregando...' : 'Recarregar'}
                </Button>
              </div>
            </div>
            <div className="admin-metrics">
              {metricas.map(([label, value]) => (
                <article key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </article>
              ))}
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
            <div className="admin-filters">
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
            <Table columns={['Empresa', 'Responsavel', 'E-mail', 'Telefone', 'Plano', 'Valor', 'Gateway', 'Status', 'Empresa', 'Vencimento', 'Pagamento', 'External ID', 'Referencia', 'Detalhes', 'Acoes']}>
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
                  <td>{item.externalPaymentId}</td>
                  <td>{item.paymentReference || '-'}</td>
                  <td>{item.detalhes || '-'}</td>
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
