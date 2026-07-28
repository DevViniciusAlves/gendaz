import api, { modoDemo } from './axiosConfig.js'
import { emptyData, getData } from '../services/localStore.js'

function empresaIdAtual() {
  const usuario = JSON.parse(localStorage.getItem('agendapro_usuario') || 'null')
  return usuario?.empresaId || null
}

function usuarioAtual() {
  return JSON.parse(localStorage.getItem('agendapro_usuario') || 'null')
}

function usuarioHeaders() {
  const usuario = usuarioAtual()
  return usuario?.id ? { 'X-Usuario-Id': usuario.id } : {}
}

function emitirToast(type, message) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('agendapro:toast', {
    detail: { type, message },
  }))
}

async function comNotificacao(acao, textos = {}) {
  const {
    loading = 'Processando... aguarde',
    success = 'Operação concluída com sucesso.',
    error = 'Não foi possível concluir a operação.',
  } = textos

  emitirToast('loading', loading)
  try {
    const resultado = await acao()
    emitirToast('success', success)
    return resultado
  } catch (err) {
    const mensagem = err?.response?.data?.mensagem || err?.message || error
    emitirToast('error', mensagem)
    throw err
  }
}

function moedaNumero(valor) {
  return Number(valor || 0)
}

function horaCurta(valor) {
  return String(valor || '').slice(0, 5)
}

function periodoAtual() {
  const agora = new Date()
  return {
    mes: String(agora.getMonth() + 1).padStart(2, '0'),
    ano: agora.getFullYear(),
  }
}

function enriquecerClientes(clientes, pagamentos) {
  return clientes.map((cliente) => ({
    ...cliente,
    totalGasto: pagamentos
      .filter((pagamento) => pagamento.clienteId === cliente.id && pagamento.status === 'PAGO')
      .reduce((total, pagamento) => total + moedaNumero(pagamento.valor), 0),
  }))
}

function enriquecerServicos(servicos, agendamentos) {
  return servicos.map((servico) => ({
    ...servico,
    valor: moedaNumero(servico.valor),
    vendas: agendamentos.filter((agendamento) => agendamento.servicoId === servico.id).length,
  }))
}

function normalizarAgendamentos(agendamentos) {
  return agendamentos.map((item) => ({
    ...item,
    horaInicio: horaCurta(item.horaInicio),
    horaFim: horaCurta(item.horaFim),
  }))
}

function normalizarPagamentos(pagamentos) {
  return pagamentos.map((item) => ({ ...item, valor: moedaNumero(item.valor) }))
}

function normalizarResumoDashboard(resumo) {
  if (!resumo) return null
  const receitaPorDia = Array.isArray(resumo.receitaPorDia)
    ? resumo.receitaPorDia.map((item) => ({
        ...item,
        valor: moedaNumero(item.valor),
      }))
    : []
  const servicosMaisAgendados = Array.isArray(resumo.servicosMaisAgendados)
    ? resumo.servicosMaisAgendados.map((item) => ({
        ...item,
        quantidade: Number(item.quantidade || 0),
        valor: moedaNumero(item.valor),
      }))
    : []
  const proximosAgendamentos = Array.isArray(resumo.proximosAgendamentos)
    ? resumo.proximosAgendamentos.map((item) => ({ ...item }))
    : []
  const ultimosAgendamentos = Array.isArray(resumo.ultimosAgendamentos)
    ? resumo.ultimosAgendamentos.map((item) => ({ ...item }))
    : []
  const pagamentosPendentes = Array.isArray(resumo.pagamentosPendentes)
    ? resumo.pagamentosPendentes.map((item) => ({ ...item, valor: moedaNumero(item.valor) }))
    : []
  const pagamentosRecentes = Array.isArray(resumo.pagamentosRecentes)
    ? resumo.pagamentosRecentes.map((item) => ({ ...item, valor: moedaNumero(item.valor) }))
    : []

  return {
    ...resumo,
    agendamentosHoje: Number(resumo.agendamentosHoje || 0),
    conversasAbertas: Number(resumo.conversasAbertas || 0),
    clientesCadastrados: Number(resumo.clientesCadastrados || 0),
    servicosAtivos: Number(resumo.servicosAtivos || 0),
    receitaConfirmada: moedaNumero(resumo.receitaConfirmada),
    pendenteCobranca: moedaNumero(resumo.pendenteCobranca),
    receitaPorDia,
    servicosMaisAgendados,
    proximosAgendamentos,
    ultimosAgendamentos,
    pagamentosPendentes,
    pagamentosRecentes,
  }
}

function normalizarStatusPagamento(status) {
  return String(status || '').toUpperCase()
}

function pagamentoConfirmado(status) {
  return ['PAGO', 'PAGA', 'CONFIRMADO', 'CONFIRMADA', 'APROVADO', 'APPROVED', 'PAID', 'PAYMENT_APPROVED', 'PURCHASE_APPROVED']
    .includes(normalizarStatusPagamento(status))
}

function criarBaseLocal(scope, usuario) {
  const data = getData()
  const base = emptyData(usuario)
  if (scope === 'clientes') return { ...base, clientes: data.clientes, empresa: data.empresa }
  if (scope === 'servicos') return { ...base, servicos: data.servicos, empresa: data.empresa }
  if (scope === 'profissionais') return { ...base, profissionais: data.profissionais, empresa: data.empresa }
  if (scope === 'agenda') return { ...base, clientes: data.clientes, servicos: data.servicos, profissionais: data.profissionais, agendamentos: data.agendamentos, empresa: data.empresa }
  if (scope === 'financeiro') return { ...base, clientes: data.clientes, servicos: data.servicos, agendamentos: data.agendamentos, pagamentos: data.pagamentos, financeiro: data.financeiro, empresa: data.empresa }
  if (scope === 'pagamentos') return { ...base, clientes: data.clientes, pagamentos: data.pagamentos, empresa: data.empresa }
  if (scope === 'relatorios') return { ...base, clientes: data.clientes, servicos: data.servicos, agendamentos: data.agendamentos, empresa: data.empresa }
  if (scope === 'dashboard') return { ...base, empresa: data.empresa, clientes: data.clientes, servicos: data.servicos, profissionais: data.profissionais, agendamentos: data.agendamentos, conversas: data.conversas, pagamentos: data.pagamentos, financeiro: data.financeiro, planos: data.planos }
  if (scope === 'configuracoes') return { ...base, empresa: data.empresa }
  if (scope === 'produtos') return { ...base, produtos: data.produtos, empresa: data.empresa }
  if (scope === 'pedidos') return { ...base, pedidos: data.pedidos, produtos: data.produtos, empresa: data.empresa }
  if (scope === 'entregas') return { ...base, entregas: data.entregas, clientes: data.clientes, empresa: data.empresa }
  if (scope === 'notasFiscais') return { ...base, notasFiscais: data.notasFiscais, clientes: data.clientes, empresa: data.empresa }
  if (scope === 'usuarios') return { ...base, equipe: data.equipe, empresa: data.empresa }
  if (scope === 'planos') return { ...base, planos: data.planos, empresa: data.empresa }
  if (scope === 'insights') return { ...base, empresa: data.empresa, clientes: data.clientes, servicos: data.servicos, profissionais: data.profissionais, agendamentos: data.agendamentos, conversas: data.conversas, pagamentos: data.pagamentos, financeiro: data.financeiro, dashboardResumo: data.dashboardResumo, dashboard: data.dashboard, historico: data.historico, mensagens: data.mensagens }
  return data
}

export const appApi = {
  empresaId: null,

  async carregarDados(scope = 'full') {
    const empresaId = empresaIdAtual()
    const estaImpersonando = Boolean(localStorage.getItem('agendeasy_admin_impersonation'))
    if (!empresaId && scope !== 'insights') {
      return criarBaseLocal(scope, null)
    }

    if (modoDemo) {
      const usuario = JSON.parse(localStorage.getItem('agendapro_usuario') || 'null')
      const local = criarBaseLocal(scope, usuario)
      return { ...local, __remote: true }
    }

    const [empresaResumo, assinaturaAtual] = empresaId
      ? await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/pagamentos/planos/empresa/${empresaId}/atual`)
            .then((response) => response.data)
            .catch((error) => {
              if (error.response?.status === 404) return null
              throw error
            }),
        ])
      : [null, null]

    if (!estaImpersonando && (assinaturaAtual?.status === 'EXPIRADA' || ['INATIVA', 'BLOQUEADA', 'PENDENTE_PAGAMENTO'].includes(empresaResumo?.status))) {
      window.dispatchEvent(new Event('agendeasy:account-inactive'))
      throw new Error('Sua conta encontra-se inativa. Regularize a mensalidade para continuar usando o gendaz.')
    }

    const loaders = {
      full: async () => {
        const periodo = periodoAtual()
        const [empresa, clientesBase, servicosBase, profissionais, agendamentosBase, conversas, pagamentosBase, planos, financeiro] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/clientes/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/servicos/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/profissionais/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/agendamentos/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/conversas/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/pagamentos/empresa/${empresaId}`).then((response) => response.data),
          api.get('/planos').then((response) => response.data),
          api.get(`/financeiro/resumo?empresaId=${empresaId}&mes=${periodo.mes}&ano=${periodo.ano}`).then((response) => response.data),
        ])
        const mensagensPorConversa = await Promise.all(
          conversas.map((conversa) => api.get(`/mensagens/conversa/${conversa.id}`).then((response) => response.data)),
        )
        return {
          empresa,
          clientesBase,
          servicosBase,
          profissionais,
          agendamentosBase,
          conversas,
          pagamentosBase,
          planos,
          financeiro,
          mensagens: mensagensPorConversa.flat(),
        }
      },
      dashboard: async () => {
        const [empresa, clientesBase, servicosBase, profissionais, agendamentosBase, conversas, pagamentosBase, resumo] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/clientes/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/servicos/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/profissionais/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/agendamentos/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/conversas/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/pagamentos/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/dashboard/resumo?empresaId=${empresaId}`).then((response) => response.data),
        ])
        const dashboardResumo = normalizarResumoDashboard(resumo)
        console.log('[dashboard-debug] dados recebidos', dashboardResumo)
        return {
          empresa,
          clientesBase,
          servicosBase,
          profissionais,
          agendamentosBase,
          conversas,
          pagamentosBase,
          planos: [],
          financeiro: {
            totalRecebidoMes: dashboardResumo?.receitaConfirmada || 0,
            totalPendente: dashboardResumo?.pendenteCobranca || 0,
            consultasRealizadas: dashboardResumo?.agendamentosHoje || 0,
            clientesMaisConsultas: [],
            servicosMaisVendidos: dashboardResumo?.servicosMaisAgendados || [],
            pagamentosRecentes: dashboardResumo?.pagamentosRecentes || [],
          },
          dashboardResumo,
          mensagens: [],
        }
      },
      insights: async () => {
        const queryResumo = empresaId ? `?empresaId=${empresaId}&periodo=30` : '?periodo=30'
        const queryHistorico = empresaId ? `?empresaId=${empresaId}` : ''
        const [dashboard, historico] = await Promise.all([
          api.get(`/insights/resumo${queryResumo}`).then((response) => response.data),
          api.get(`/insights/historico${queryHistorico}`).then((response) => response.data).catch(() => []),
        ])
        return {
          empresa: empresaResumo,
          dashboardResumo: dashboard,
          dashboard,
          historico,
          mensagens: [],
        }
      },
      clientes: async () => {
        const [empresa, clientesBase, pagamentosBase] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/clientes/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/pagamentos/empresa/${empresaId}`).then((response) => response.data),
        ])
        return { empresa, clientesBase, pagamentosBase }
      },
      servicos: async () => {
        const [empresa, servicosBase] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/servicos/empresa/${empresaId}`).then((response) => response.data),
        ])
        return { empresa, servicosBase }
      },
      profissionais: async () => {
        const [empresa, profissionais] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/profissionais/empresa/${empresaId}`).then((response) => response.data),
        ])
        return { empresa, profissionais }
      },
      agenda: async () => {
        const [empresa, clientesBase, servicosBase, profissionais, agendamentosBase] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/clientes/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/servicos/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/profissionais/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/agendamentos/empresa/${empresaId}`).then((response) => response.data),
        ])
        return { empresa, clientesBase, servicosBase, profissionais, agendamentosBase }
      },
      financeiro: async () => {
        const periodo = periodoAtual()
        const [empresa, pagamentosBase, financeiro] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/pagamentos/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/financeiro/resumo?empresaId=${empresaId}&mes=${periodo.mes}&ano=${periodo.ano}`).then((response) => response.data),
        ])
        return { empresa, clientesBase: [], servicosBase: [], agendamentosBase: [], pagamentosBase, financeiro }
      },
      relatorios: async () => {
        const [empresa, clientesBase, servicosBase, agendamentosBase] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/clientes/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/servicos/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/agendamentos/empresa/${empresaId}`).then((response) => response.data),
        ])
        return { empresa, clientesBase, servicosBase, agendamentosBase }
      },
      configuracoes: async () => {
        const empresa = await api.get(`/empresas/${empresaId}`).then((response) => response.data)
        return { empresa }
      },
      pagamentos: async () => {
        const [empresa, clientesBase, pagamentosBase] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/clientes/empresa/${empresaId}`).then((response) => response.data),
          api.get(`/pagamentos/empresa/${empresaId}`).then((response) => response.data),
        ])
        return { empresa, clientesBase, pagamentosBase }
      },
      entregas: async () => {
        const [empresa, clientesBase] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/clientes/empresa/${empresaId}`).then((response) => response.data),
        ])
        return { empresa, clientesBase }
      },
      notasFiscais: async () => {
        const [empresa, clientesBase] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/clientes/empresa/${empresaId}`).then((response) => response.data),
        ])
        return { empresa, clientesBase }
      },
      pedidos: async () => {
        const [empresa, produtos] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get(`/produtos/empresa/${empresaId}`).then((response) => response.data).catch(() => []),
        ])
        return { empresa, produtos }
      },
      produtos: async () => {
        const empresa = await api.get(`/empresas/${empresaId}`).then((response) => response.data)
        return { empresa, produtos: getData().produtos }
      },
      usuarios: async () => {
        const empresa = await api.get(`/empresas/${empresaId}`).then((response) => response.data)
        return { empresa, equipe: getData().equipe }
      },
      planos: async () => {
        const [empresa, planos] = await Promise.all([
          api.get(`/empresas/${empresaId}`).then((response) => response.data),
          api.get('/planos').then((response) => response.data),
        ])
        return { empresa, planos }
      },
    }

    const loaded = await (loaders[scope] || loaders.full)()

    const empresa = loaded.empresa
    if (!estaImpersonando && empresa?.status && empresa.status !== 'ATIVA') {
      window.dispatchEvent(new Event('agendeasy:account-inactive'))
      throw new Error('Sua conta encontra-se inativa. Regularize a mensalidade para continuar usando o gendaz.')
    }

    const agendamentos = normalizarAgendamentos(loaded.agendamentosBase || [])
    const pagamentos = normalizarPagamentos(loaded.pagamentosBase || [])
    const clientesBase = loaded.clientesBase || []
    const servicosBase = loaded.servicosBase || []
    const profissionais = loaded.profissionais || []
    const conversas = loaded.conversas || []
    const planos = loaded.planos || []
    const financeiro = loaded.financeiro || {
      totalRecebidoMes: 0,
      totalPendente: 0,
      consultasRealizadas: 0,
      clientesMaisConsultas: [],
      servicosMaisVendidos: [],
    }
    const mensagens = loaded.mensagens || []

    return {
      __remote: true,
      empresa: {
        ...empresa,
      },
      clientes: enriquecerClientes(clientesBase, pagamentos),
      servicos: enriquecerServicos(servicosBase, agendamentos),
      profissionais,
      agendamentos,
      conversas,
      mensagens,
      pagamentos,
      planos,
      financeiro,
      notasFiscais: [],
      entregas: [],
      produtos: [],
      pedidos: [],
      equipe: [],
    }
  },

  async login(email, senha) {
    const response = await api.post('/auth/login', { email, senha })
    return response.data
  },

  solicitarCodigoMeuGendaz(email) {
    return api.post('/meu-gendaz/auth/solicitar-codigo', { email }).then((response) => response.data)
  },

  validarCodigoMeuGendaz(email, codigo) {
    return api.post('/meu-gendaz/auth/validar-codigo', { email, codigo }).then((response) => response.data)
  },

  async criarConta(payload) {
    const response = await api.post('/auth/criar-conta', payload)
    return response.data
  },

  async refreshSession() {
    const response = await api.post('/auth/refresh')
    return response.data
  },

  async carregarMensagensConversa(conversaId) {
    if (modoDemo) {
      const data = getData()
      return data.mensagens.filter((mensagem) => mensagem.conversaId === conversaId)
    }
    return api.get(`/mensagens/conversa/${conversaId}`).then((response) => response.data)
  },

  logout() {
    return api.post('/auth/logout', null, {
      headers: usuarioHeaders(),
    }).then((response) => response.data)
  },

  atualizarEmpresa(id, payload) {
    return comNotificacao(() => api.put(`/empresas/${id}`, payload).then((response) => response.data), {
      loading: 'Editando empresa... aguarde',
      success: 'Empresa atualizada com sucesso.',
      error: 'Não foi possível atualizar a empresa.',
    })
  },

  atualizarUsuario(id, payload) {
    return comNotificacao(() => api.put(`/usuarios/${id}`, payload).then((response) => response.data), {
      loading: 'Editando usuário... aguarde',
      success: 'Usuário atualizado com sucesso.',
      error: 'Não foi possível atualizar o usuário.',
    })
  },

  criarCliente(payload) {
    return comNotificacao(() => api.post('/clientes', { ...payload, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Criando cliente... aguarde',
      success: 'Cliente criado com sucesso.',
      error: 'Não foi possível criar o cliente.',
    })
  },

  atualizarCliente(id, payload) {
    return comNotificacao(() => api.put(`/clientes/${id}`, { ...payload, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Editando cliente... aguarde',
      success: 'Cliente atualizado com sucesso.',
      error: 'Não foi possível atualizar o cliente.',
    })
  },

  excluirCliente(id) {
    return comNotificacao(() => api.delete(`/clientes/${id}`, { params: { empresaId: empresaIdAtual() } }).then((response) => response.data), {
      loading: 'Excluindo cliente... aguarde',
      success: 'Cliente excluído com sucesso.',
      error: 'Não foi possível excluir o cliente.',
    })
  },

  excluirClientesEmMassa(ids) {
    return comNotificacao(() => api.post('/clientes/acoes-em-massa', { ids, acao: 'EXCLUIR', empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Excluindo clientes... aguarde',
      success: 'Clientes excluídos com sucesso.',
      error: 'Não foi possível excluir os clientes.',
    })
  },

  criarServico(payload) {
    return comNotificacao(() => api.post('/servicos', { ...payload, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Criando serviço... aguarde',
      success: 'Serviço criado com sucesso.',
      error: 'Não foi possível criar o serviço.',
    })
  },

  atualizarServico(id, payload) {
    return comNotificacao(() => api.put(`/servicos/${id}`, { ...payload, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Editando serviço... aguarde',
      success: 'Serviço atualizado com sucesso.',
      error: 'Não foi possível atualizar o serviço.',
    })
  },

  alterarStatusServico(id, statusAtual) {
    const acao = statusAtual === 'ATIVO' ? 'desativar' : 'ativar'
    return api.patch(`/servicos/${id}/${acao}`).then((response) => response.data)
  },

  excluirServico(id) {
    return comNotificacao(() => api.delete(`/servicos/${id}`, { params: { empresaId: empresaIdAtual() } }).then((response) => response.data), {
      loading: 'Excluindo serviço... aguarde',
      success: 'Serviço excluído com sucesso.',
      error: 'Não foi possível excluir o serviço.',
    })
  },

  criarProfissional(payload) {
    return comNotificacao(() => api.post('/profissionais', { ...payload, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Criando profissional... aguarde',
      success: 'Profissional criado com sucesso.',
      error: 'Não foi possível criar o profissional.',
    })
  },

  alterarStatusProfissional(id, statusAtual) {
    const acao = statusAtual === 'ATIVO' ? 'desativar' : 'ativar'
    return api.patch(`/profissionais/${id}/${acao}`).then((response) => response.data)
  },

  atualizarProfissional(id, payload) {
    return comNotificacao(() => api.put(`/profissionais/${id}`, { ...payload, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Editando profissional... aguarde',
      success: 'Profissional atualizado com sucesso.',
      error: 'Não foi possível atualizar o profissional.',
    })
  },

  excluirProfissional(id) {
    return comNotificacao(() => api.delete(`/profissionais/${id}`, { params: { empresaId: empresaIdAtual() } }).then((response) => response.data), {
      loading: 'Excluindo profissional... aguarde',
      success: 'Profissional excluído com sucesso.',
      error: 'Não foi possível excluir o profissional.',
    })
  },

  criarAgendamento(payload) {
    const { status, ...body } = payload
    return comNotificacao(() => api.post('/agendamentos', { ...body, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Criando agendamento... aguarde',
      success: 'Agendamento criado com sucesso.',
      error: 'Não foi possível criar o agendamento.',
    })
  },

  atualizarAgendamento(id, payload) {
    return comNotificacao(() => api.put(`/agendamentos/${id}`, { ...payload, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Editando agendamento... aguarde',
      success: 'Agendamento atualizado com sucesso.',
      error: 'Não foi possível atualizar o agendamento.',
    })
  },

  finalizarAgendamento(id) {
    return comNotificacao(() => api.patch(`/agendamentos/${id}/finalizar`).then((response) => response.data), {
      loading: 'Finalizando agendamento... aguarde',
      success: 'Agendamento finalizado com sucesso.',
      error: 'Não foi possível finalizar o agendamento.',
    })
  },

  iniciarAgendamento(id) {
    return comNotificacao(() => api.patch(`/agendamentos/${id}/iniciar`).then((response) => response.data), {
      loading: 'Iniciando atendimento... aguarde',
      success: 'Atendimento iniciado com sucesso.',
      error: 'Não foi possível iniciar o atendimento.',
    })
  },

  pausarAgendamento(id) {
    return comNotificacao(() => api.patch(`/agendamentos/${id}/pausar`).then((response) => response.data), {
      loading: 'Pausando atendimento... aguarde',
      success: 'Atendimento pausado com sucesso.',
      error: 'Não foi possível pausar o atendimento.',
    })
  },

  cancelarAgendamento(id) {
    return comNotificacao(() => api.patch(`/agendamentos/${id}/cancelar`, null, { params: { empresaId: empresaIdAtual() } }).then((response) => response.data), {
      loading: 'Cancelando agendamento... aguarde',
      success: 'Agendamento cancelado com sucesso.',
      error: 'Não foi possível cancelar o agendamento.',
    })
  },

  excluirAgendamento(id) {
    return comNotificacao(() => api.delete(`/agendamentos/${id}`, { params: { empresaId: empresaIdAtual() } }).then((response) => response.data), {
      loading: 'Excluindo agendamento... aguarde',
      success: 'Agendamento excluído com sucesso.',
      error: 'Não foi possível excluir o agendamento.',
    })
  },

  acaoEmMassaAgendamentos(ids, acao) {
    return comNotificacao(() => api.post('/agendamentos/acoes-em-massa', { ids, acao, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Processando agendamentos... aguarde',
      success: 'Agendamentos processados com sucesso.',
      error: 'Não foi possível processar os agendamentos.',
    })
  },

  confirmarAgendamento(id) {
    return comNotificacao(() => api.patch(`/agendamentos/${id}/confirmar`).then((response) => response.data), {
      loading: 'Confirmando agendamento... aguarde',
      success: 'Agendamento confirmado com sucesso.',
      error: 'Não foi possível confirmar o agendamento.',
    })
  },

  criarPagamento(payload) {
    return comNotificacao(() => api.post('/pagamentos', { ...payload, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Criando pagamento... aguarde',
      success: 'Pagamento criado com sucesso.',
      error: 'Não foi possível criar o pagamento.',
    })
  },

  marcarPagamentoPago(id) {
    return comNotificacao(() => api.patch(`/pagamentos/${id}/marcar-pago`).then((response) => response.data), {
      loading: 'Marcando pagamento como pago... aguarde',
      success: 'Pagamento marcado como pago.',
      error: 'Não foi possível atualizar o pagamento.',
    })
  },

  atualizarStatusPagamento(id, status) {
    return comNotificacao(() => api.patch(`/pagamentos/${id}/status`, { status }).then((response) => response.data), {
      loading: 'Atualizando pagamento... aguarde',
      success: 'Pagamento atualizado com sucesso.',
      error: 'Não foi possível atualizar o pagamento.',
    })
  },

  acaoEmMassaPagamentos(ids, acao) {
    return comNotificacao(() => api.post('/pagamentos/acoes-em-massa', { ids, acao, empresaId: empresaIdAtual() }).then((response) => response.data), {
      loading: 'Processando pagamentos... aguarde',
      success: 'Pagamentos processados com sucesso.',
      error: 'Não foi possível processar os pagamentos.',
    })
  },

  contarPagamentosPendentes(empresaId = empresaIdAtual()) {
    return api.get('/pagamentos/pendentes/contagem', { params: { empresaId } }).then((response) => response.data.count || 0)
  },

  iniciarPagamentoPlano(payload, options = {}) {
    return api.post('/pagamentos/planos/pro/iniciar', payload, options).then((response) => response.data)
  },

  iniciarPagamentoPro(payload) {
    return appApi.iniciarPagamentoPlano(payload)
  },

  listarPagamentosPlano(empresaId = empresaIdAtual(), options = {}) {
    return api.get(`/pagamentos/planos/empresa/${empresaId}`, options).then((response) => response.data)
  },

  consultarPagamentoPlano(empresaId, pagamentoId) {
    return api.get(`/pagamentos/planos/empresa/${empresaId}/${pagamentoId}`).then((response) => response.data)
  },

  verificarPagamentoPlano(empresaId, pagamentoId, options = {}) {
    return api.get(`/pagamentos/planos/empresa/${empresaId}/${pagamentoId}/verificar`, options).then((response) => response.data)
  },

  consultarPlanoAtual(empresaId = empresaIdAtual(), options = {}) {
    return api.get(`/pagamentos/planos/empresa/${empresaId}/atual`, options).then((response) => response.data)
  },

  enviarMensagem(conversaId, conteudo) {
    return api.post('/mensagens/enviar', { conversaId, conteudo }).then((response) => response.data)
  },

  enviarHorarios(payload) {
    return api.post('/mensagens/enviar-horarios-disponiveis', {
      conversaId: payload.conversaId,
      empresaId: empresaIdAtual(),
      profissionalId: payload.profissionalId,
      servicoId: payload.servicoId,
      data: payload.data,
    }).then((response) => response.data)
  },

  finalizarConversa(id) {
    return comNotificacao(() => api.patch(`/conversas/${id}/finalizar`).then((response) => response.data), {
      loading: 'Finalizando conversa... aguarde',
      success: 'Conversa finalizada com sucesso.',
      error: 'Não foi possível finalizar a conversa.',
    })
  },

  solicitarRecuperacaoSenha(email) {
    return api.post('/auth/recuperar-senha', { email }).then((response) => response.data)
  },

  redefinirSenha(token, novaSenha, confirmarNovaSenha) {
    return api.post('/auth/redefinir-senha', { token, novaSenha, confirmarNovaSenha }).then((response) => response.data)
  },

  trocarSenha(senhaAtual, novaSenha, confirmarNovaSenha) {
    return api.put('/auth/trocar-senha', { senhaAtual, novaSenha, confirmarNovaSenha }, {
      headers: usuarioHeaders(),
    }).then((response) => response.data)
  },

  criarChamado(payload) {
    return comNotificacao(() => api.post('/chamados', payload, {
      headers: usuarioHeaders(),
    }).then((response) => response.data), {
      loading: 'Criando chamado... aguarde',
      success: 'Chamado criado com sucesso.',
      error: 'Não foi possível criar o chamado.',
    })
  },

  listarChamadosEmpresa(empresaId) {
    return api.get(`/chamados/empresa/${empresaId}`, {
      headers: usuarioHeaders(),
    }).then((response) => response.data)
  },

  primeirosPassos() {
    return api.get('/dashboard/primeiros-passos', {
      headers: usuarioHeaders(),
    }).then((response) => response.data)
  },

  obterLinkAgendamento() {
    return api.get('/configuracoes/agendamento/link', {
      headers: usuarioHeaders(),
    }).then((response) => response.data)
  },

  atualizarLinkAgendamento(slug) {
    return comNotificacao(() => api.put('/configuracoes/agendamento/link', { slug }, {
      headers: usuarioHeaders(),
    }).then((response) => response.data), {
      loading: 'Salvando link... aguarde',
      success: 'Link de agendamento salvo com sucesso.',
      error: 'Não foi possível atualizar o link de agendamento.',
    })
  },

  carregarBooking(slugOuEmpresaId) {
    return api.get(`/public/agendamento/${slugOuEmpresaId}`).then((response) => response.data)
  },

  listarHorariosBooking(slugOuEmpresaId, profissionalId, servicoId, data) {
    const params = { servicoId, data }
    if (profissionalId !== null && profissionalId !== undefined && profissionalId !== '') {
      params.profissionalId = profissionalId
    }
    return api.get(`/public/agendamento/${slugOuEmpresaId}/disponibilidade`, {
      params,
    }).then((response) => response.data)
  },

  criarAgendamentoPublico(slugOuEmpresaId, payload) {
    return comNotificacao(() => api.post(`/public/agendamento/${slugOuEmpresaId}/confirmar`, payload).then((response) => response.data), {
      loading: 'Confirmando agendamento... aguarde',
      success: 'Agendamento confirmado com sucesso.',
      error: 'Não foi possível confirmar o agendamento.',
    })
  },

  listarHorariosAtendimento() {
    return api.get('/configuracoes/horario-atendimento', {
      headers: usuarioHeaders(),
    }).then((response) => response.data)
  },

  salvarHorariosAtendimento(horarios) {
    return comNotificacao(() => api.put('/configuracoes/horario-atendimento', { horarios }, {
      headers: usuarioHeaders(),
    }).then((response) => response.data), {
      loading: 'Salvando horários... aguarde',
      success: 'Horários salvos com sucesso.',
      error: 'Não foi possível salvar os horários.',
    })
  },
}


