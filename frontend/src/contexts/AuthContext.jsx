import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { appApi } from '../api/appApi.js'
import { adminApi } from '../api/adminApi.js'
import { clearLocalData, updateCurrentUser } from '../services/localStore.js'
import { setSessionUser } from '../api/axiosConfig.js'
import { useSessionWebSocket } from '../hooks/useSessionWebSocket.js'

const AuthContext = createContext(null)
let pendingPaymentMemory = null
let adminUsuarioMemory = null
let adminSessionTokenMemory = null
let impersonationMemory = null

function limparSessaoUsuario() {
  setSessionUser(null)
}

function lerImpersonationPersistida() {
  return impersonationMemory
}

function salvarImpersonationPersistida(impersonation) {
  impersonationMemory = impersonation || null
}

function limparSessaoAdmin() {
  adminUsuarioMemory = null
  adminSessionTokenMemory = null
}

function emitirMudancaSessao() {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new Event('agendapro:session-changed'))
}

function resolverPlano(usuario, fallbackAtual) {
  return usuario?.plano
    || usuario?.assinatura?.planoNome
    || usuario?.assinatura?.plano?.nome
    || fallbackAtual?.plano
    || null
}

function salvarUsuarioSessao(usuario) {
  setSessionUser(usuario)
  emitirMudancaSessao()
  return usuario
}

function normalizarUsuarioSessao(usuarioBase, fallbackAtual) {
  const usuario = usuarioBase || fallbackAtual
  if (!usuario) return null
  return {
    ...usuario,
    plano: resolverPlano(usuario, fallbackAtual),
    assinatura: usuario?.assinatura || fallbackAtual?.assinatura || null,
    statusConta: usuario?.statusConta || fallbackAtual?.statusConta || 'ACTIVE',
    motivoInatividade: usuario?.motivoInatividade || fallbackAtual?.motivoInatividade || null,
  }
}

function erroTemporarioAutenticacao(error) {
  const status = error?.response?.status
  if (!status) return true
  if (status === 0) return true
  return ![401, 403].includes(status)
}

function contaInativa(usuarioBase) {
  return usuarioBase?.statusConta === 'ACCOUNT_INACTIVE'
}

function mensagemAcessoOutroDispositivo(error) {
  const mensagem = String(error?.response?.data?.mensagem || error?.response?.data?.message || '').toLowerCase()
  return mensagem.includes('outro dispositivo')
    || mensagem.includes('acessada em outro dispositivo')
    || mensagem.includes('acesso em outro dispositivo')
}

function isMeuGendazPath() {
  if (typeof window === 'undefined') return false
  return window.location.pathname.startsWith('/meu-gendaz/')
}

function isAdminPath() {
  if (typeof window === 'undefined') return false
  return window.location.pathname.startsWith('/admin')
}

export function AuthProvider({ children }) {
  const [authLoading, setAuthLoading] = useState(true)
  const [usuario, setUsuario] = useState(null)
  const [impersonation, setImpersonation] = useState(() => impersonationMemory || lerImpersonationPersistida())
  const [adminUsuario, setAdminUsuario] = useState(() => adminUsuarioMemory)
  const [sessionExpired, setSessionExpired] = useState(false)
  const refreshEmAndamentoRef = useRef(null)
  const transicaoSessaoRef = useRef(false)
  
  useSessionWebSocket(() => {
    if (isMeuGendazPath()) return
    if (transicaoSessaoRef.current) return
    if (!usuario) return
    console.warn('[auth-debug] websocket invalidacao de sessao recebida')
    logout('session_invalidated')
    setSessionExpired(true)
  }, { enabled: !isMeuGendazPath() })

  const validacaoInicialEmAndamentoRef = useRef(false)
  const ultimaRenovacaoBemSucedidaRef = useRef(0)

  function forcarLogoutPorFalhaRefresh(motivo) {
    console.error(`[auth-seguranca] Falha crítica no refresh: ${motivo}. Forçando logout total.`)
    limparSessaoUsuario()
    clearLocalData()
    setUsuario(null)
    setSessionExpired(true)
    window.dispatchEvent(new Event('agendeasy:session-expired'))
  }

  async function renovarAoRetomarAba({ ignorarThrottle = false } = {}) {
    if (transicaoSessaoRef.current) return true
    if (!usuario?.id || usuario?.perfil === 'SUPER_ADMIN' || adminUsuario) return true
    if (contaInativa(usuario)) return true
    if (validacaoInicialEmAndamentoRef.current) return true
    const agora = Date.now()
    if (!ignorarThrottle && agora - ultimaRenovacaoBemSucedidaRef.current < 10_000) return true
    if (refreshEmAndamentoRef.current) {
      return refreshEmAndamentoRef.current
    }

    const promessa = (async () => {
      try {
        const refresh = await appApi.refreshSession({ skipUsuarioHeader: true })
        const updated = normalizarUsuarioSessao(
          refresh?.usuario
            ? {
                ...refresh.usuario,
                assinatura: refresh.assinatura,
                statusConta: refresh.statusConta,
                motivoInatividade: refresh.motivoInatividade,
              }
            : null,
          usuario,
        )
        salvarUsuarioSessao(updated)
        setUsuario(updated)
        ultimaRenovacaoBemSucedidaRef.current = Date.now()
        return true
} catch (error) {
        const statusConta = error?.response?.data?.statusConta
        const motivoInatividade = error?.response?.data?.motivoInatividade
        const mensagem = String(error?.response?.data?.mensagem || error?.response?.data?.message || '').toLowerCase()
        if (statusConta === 'ACCOUNT_INACTIVE'
          || mensagem.includes('conta inativa')
          || mensagem.includes('periodo gratuito terminou')
          || mensagem.includes('mensalidade')) {
          const motivoSuspensaoAdmin = mensagem.includes('indisponível')
            || mensagem.includes('indisponivel')
            || mensagem.includes('suspensa')
          const atualizado = normalizarUsuarioSessao(
            {
              ...usuario,
              statusConta: 'ACCOUNT_INACTIVE',
              motivoInatividade: motivoInatividade || (motivoSuspensaoAdmin ? 'ADMIN_SUSPENSAO' : 'PAGAMENTO_PENDENTE'),
            },
            usuario,
          )
          salvarUsuarioSessao(atualizado)
          setUsuario(atualizado)
          throw new Error('Sua conta encontra-se inativa. Regularize a mensalidade para continuar usando o gendaz.')
        }
        if (error?.response?.status === 401 && mensagemAcessoOutroDispositivo(error)) {
          forcarLogoutPorFalhaRefresh('acesso_em_outro_dispositivo')
          return false
        }
        if (error?.response?.status === 401) {
          forcarLogoutPorFalhaRefresh('sessao_expirada_401')
          return false
        }
        if (!erroTemporarioAutenticacao(error)) {
          forcarLogoutPorFalhaRefresh('erro_fatal_nao_tratado')
        } else {
          console.warn('[auth-debug] renovacao ao retomar aba ignorou erro temporario')
        }
        throw error
      } finally {
        refreshEmAndamentoRef.current = null
      }
    })()

    refreshEmAndamentoRef.current = promessa
    return promessa
  }

  useEffect(() => {
    let mounted = true

    async function validarSessaoInicial() {
      validacaoInicialEmAndamentoRef.current = true
      if (transicaoSessaoRef.current) {
        if (mounted) setAuthLoading(false)
        validacaoInicialEmAndamentoRef.current = false
        return
      }
      if (isMeuGendazPath()) {
        if (mounted) setAuthLoading(false)
        validacaoInicialEmAndamentoRef.current = false
        return
      }
      if (isAdminPath() && !adminUsuario) {
        try {
          const adminRefresh = await adminApi.refresh()
          if (adminRefresh?.admin?.perfil === 'SUPER_ADMIN') {
            adminUsuarioMemory = adminRefresh.admin
            if (mounted) setAdminUsuario(adminRefresh.admin)
            if (mounted) setAuthLoading(false)
            validacaoInicialEmAndamentoRef.current = false
            return
          }
        } catch {
          // cai para o fluxo normal
        }
      }
      if (adminUsuario) {
        if (mounted) setAuthLoading(false)
        validacaoInicialEmAndamentoRef.current = false
        return
      }
      if (usuario?.perfil === 'SUPER_ADMIN') {
        if (mounted) setAuthLoading(false)
        validacaoInicialEmAndamentoRef.current = false
        return
      }
      try {
        const refresh = await appApi.refreshSession({ skipUsuarioHeader: true })
        if (!mounted) return
        const updated = normalizarUsuarioSessao(
          refresh?.usuario
            ? {
                ...refresh.usuario,
                assinatura: refresh.assinatura,
                statusConta: refresh.statusConta,
                motivoInatividade: refresh.motivoInatividade,
              }
            : null,
          usuario,
        )
        salvarUsuarioSessao(updated)
        setUsuario(updated)
        ultimaRenovacaoBemSucedidaRef.current = Date.now()
} catch (error) {
        if (!mounted) return
        const status = error.response?.status
        const statusConta = error.response?.data?.statusConta
        const motivoInatividade = error.response?.data?.motivoInatividade
        const mensagem = String(error.response?.data?.mensagem || error.response?.data?.message || '').toLowerCase()
        const contaMarcadaInativa = statusConta === 'ACCOUNT_INACTIVE'
          || mensagem.includes('conta inativa')
          || mensagem.includes('periodo gratuito terminou')
          || mensagem.includes('mensalidade')
          || mensagem.includes('indisponível')
          || mensagem.includes('indisponivel')
          || mensagem.includes('suspensa')
        const falhaFatal = status === 401
          || mensagem.includes('sessao foi encerrada')
          || mensagem.includes('sessão foi encerrada')
          || mensagem.includes('usuario autenticado invalido')
          || mensagem.includes('usuário autenticado inválido')
          || mensagem.includes('sessao expirada')
          || mensagem.includes('sessão expirada')
        if (contaMarcadaInativa) {
          const motivoSuspensaoAdmin = mensagem.includes('indisponível')
            || mensagem.includes('indisponivel')
            || mensagem.includes('suspensa')
          const atualizado = normalizarUsuarioSessao(
            {
              ...usuario,
              statusConta: 'ACCOUNT_INACTIVE',
              motivoInatividade: motivoInatividade || (motivoSuspensaoAdmin ? 'ADMIN_SUSPENSAO' : 'PAGAMENTO_PENDENTE'),
            },
            usuario,
          )
          salvarUsuarioSessao(atualizado)
          setUsuario(atualizado)
        } else if (status === 401 && mensagemAcessoOutroDispositivo(error)) {
          forcarLogoutPorFalhaRefresh('acesso_em_outro_dispositivo')
        } else if (falhaFatal) {
          forcarLogoutPorFalhaRefresh('falha_fatal_validacao_inicial')
        } else {
          console.warn('[auth-debug] refresh inicial ignorou erro temporario')
        }
      } finally {
        validacaoInicialEmAndamentoRef.current = false
        if (mounted) setAuthLoading(false)
      }
    }

    validarSessaoInicial()
    return () => {
      mounted = false
    }
  }, [adminUsuario])

  useEffect(() => {
    function marcarContaIndisponivel(event) {
      if (!usuario) return
      const motivoInatividade = event?.detail?.motivoInatividade || usuario?.motivoInatividade || 'PAGAMENTO_PENDENTE'
      if (usuario?.statusConta === 'ACCOUNT_INACTIVE' && usuario?.motivoInatividade === motivoInatividade) {
        return
      }
      const atualizado = { ...usuario, statusConta: 'ACCOUNT_INACTIVE', motivoInatividade }
      salvarUsuarioSessao(atualizado)
      setUsuario(atualizado)
    }
    function encerrarSessaoRemota() {
      logout('refresh_failed')
    }
    async function renovarAoRetomarAba() {
      if (isMeuGendazPath()) return
      if (transicaoSessaoRef.current) return
      if (!usuario?.id || usuario?.perfil === 'SUPER_ADMIN' || adminUsuario) return
      if (contaInativa(usuario)) return
      if (validacaoInicialEmAndamentoRef.current) return
      const agora = Date.now()
      if (agora - ultimaRenovacaoBemSucedidaRef.current < 10_000) return
      if (refreshEmAndamentoRef.current) {
        await refreshEmAndamentoRef.current
        return
      }
      const promessa = (async () => {
      try {
        const refresh = await appApi.refreshSession({ skipUsuarioHeader: true })
        const updated = normalizarUsuarioSessao(
          refresh?.usuario
            ? {
                ...refresh.usuario,
                assinatura: refresh.assinatura,
                statusConta: refresh.statusConta,
                motivoInatividade: refresh.motivoInatividade,
              }
            : null,
          usuario,
        )
        salvarUsuarioSessao(updated)
        setUsuario(updated)
        ultimaRenovacaoBemSucedidaRef.current = Date.now()
        // Ao retornar para a aba, após renovar a sessão, sinaliza uma atualização
        // única dos dados das telas abertas (sem polling contínuo).
        window.dispatchEvent(new Event('agendapro:data-changed'))
} catch (error) {
        const statusConta = error?.response?.data?.statusConta
        const motivoInatividade = error?.response?.data?.motivoInatividade
        const mensagem = String(error?.response?.data?.mensagem || error?.response?.data?.message || '').toLowerCase()
        if (statusConta === 'ACCOUNT_INACTIVE'
          || mensagem.includes('conta inativa')
          || mensagem.includes('periodo gratuito terminou')
          || mensagem.includes('mensalidade')
          || mensagem.includes('indisponível')
          || mensagem.includes('indisponivel')
          || mensagem.includes('suspensa')) {
          const motivoSuspensaoAdmin = mensagem.includes('indisponível')
            || mensagem.includes('indisponivel')
            || mensagem.includes('suspensa')
          const atualizado = normalizarUsuarioSessao(
            {
              ...usuario,
              statusConta: 'ACCOUNT_INACTIVE',
              motivoInatividade: motivoInatividade || (motivoSuspensaoAdmin ? 'ADMIN_SUSPENSAO' : 'PAGAMENTO_PENDENTE'),
            },
            usuario,
          )
          salvarUsuarioSessao(atualizado)
          setUsuario(atualizado)
          return
        }
        if (error?.response?.status === 401 && mensagemAcessoOutroDispositivo(error)) {
          forcarLogoutPorFalhaRefresh('acesso_em_outro_dispositivo')
          return
        }
        if (error?.response?.status === 401) {
          forcarLogoutPorFalhaRefresh('sessao_expirada_401')
          return
        }
        if (!erroTemporarioAutenticacao(error)) {
          forcarLogoutPorFalhaRefresh('erro_fatal_nao_tratado')
        } else {
          console.warn('[auth-debug] renovacao ao retomar aba ignorou erro temporario')
        }
      } finally {
        refreshEmAndamentoRef.current = null
      }
      })()
      refreshEmAndamentoRef.current = promessa
      await promessa
    }
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') void renovarAoRetomarAba()
    }
    const onFocus = () => {
      void renovarAoRetomarAba()
    }
    window.addEventListener('agendeasy:account-unavailable', marcarContaIndisponivel)
    window.addEventListener('agendeasy:account-inactive', marcarContaIndisponivel)
    window.addEventListener('agendeasy:session-expired', encerrarSessaoRemota)
    document.addEventListener('visibilitychange', onVisibilityChange)
    window.addEventListener('focus', onFocus)
    return () => {
      window.removeEventListener('agendeasy:account-unavailable', marcarContaIndisponivel)
      window.removeEventListener('agendeasy:account-inactive', marcarContaIndisponivel)
      window.removeEventListener('agendeasy:session-expired', encerrarSessaoRemota)
      document.removeEventListener('visibilitychange', onVisibilityChange)
      window.removeEventListener('focus', onFocus)
    }
  }, [adminUsuario, usuario])

  async function login(email, senha, { allowAdmin = false } = {}) {
    transicaoSessaoRef.current = false
    setSessionExpired(false)
    const response = await appApi.login(email, senha)

    if (response.usuario?.perfil === 'SUPER_ADMIN') {
      if (!allowAdmin) {
        throw new Error('Acesso administrativo deve ser feito pela tela de login do admin.')
      }
      clearLocalData()
      limparSessaoUsuario()
      pendingPaymentMemory = null
      setUsuario(null)
      const adminResponse = await adminApi.login(email, senha)
      setAdminUsuario(adminResponse.admin)
      return { adminAccess: true, admin: adminResponse.admin }
    }
if (response.statusConta === 'ACCOUNT_PENDING_PAYMENT' || response.statusConta === 'PAYMENT_REQUIRED') {
      const pending = {
        email,
        usuario: response.usuario,
        assinatura: response.assinatura,
        pagamentoPlano: response.pagamentoPlano,
        mensagem: response.mensagem,
        statusConta: response.statusConta,
        motivoInatividade: response.motivoInatividade || 'PAGAMENTO_PENDENTE',
      }
      pendingPaymentMemory = pending
      return { pendingPayment: true, ...pending }
    }
    if (response.statusConta === 'ACCOUNT_INACTIVE') {
      const userInativo = {
        ...response.usuario,
        plano: response.assinatura?.planoNome || response.usuario?.plano || null,
        assinatura: response.assinatura,
        statusConta: 'ACCOUNT_INACTIVE',
        motivoInatividade: response.motivoInatividade || 'PAGAMENTO_PENDENTE',
      }
      clearLocalData()
      pendingPaymentMemory = null
      salvarUsuarioSessao(userInativo)
      setUsuario(userInativo)
      return userInativo
    }
    const user = response.usuario
    const plano = response.assinatura?.planoNome || user.plano || null
    const usuarioComPlano = { ...user, plano, assinatura: response.assinatura, statusConta: response.statusConta || 'ACTIVE' }
    clearLocalData()
    salvarUsuarioSessao(usuarioComPlano)
    setUsuario(usuarioComPlano)
    return usuarioComPlano
  }

  async function criarConta(payload) {
    transicaoSessaoRef.current = false
    setSessionExpired(false)
    const response = await appApi.criarConta(payload)
    if (response.statusConta === 'ACCOUNT_PENDING_PAYMENT' || response.statusConta === 'PAYMENT_REQUIRED') {
      const pending = {
        email: payload.email,
        usuario: response.usuario,
        assinatura: response.assinatura,
        pagamentoPlano: response.pagamentoPlano,
        mensagem: response.mensagem,
        statusConta: response.statusConta,
      }
      clearLocalData()
      limparSessaoUsuario()
      pendingPaymentMemory = pending
      setUsuario(null)
      return { pendingPayment: true, ...pending }
    }
    const user = response.usuario
    const usuarioComPlano = {
      ...user,
      plano: response.assinatura?.planoNome || user.plano || null,
      assinatura: response.assinatura,
      pagamentoPlano: response.pagamentoPlano,
      statusConta: response.statusConta || 'ACTIVE',
    }
    clearLocalData()
    pendingPaymentMemory = null
    salvarUsuarioSessao(usuarioComPlano)
    setUsuario(usuarioComPlano)
    return usuarioComPlano
  }

  async function atualizarPlanoAtual() {
    if (!usuario?.empresaId) return null
    const assinatura = await appApi.consultarPlanoAtual(usuario.empresaId)
    const statusConta = assinatura?.status === 'EXPIRADA'
      ? 'ACCOUNT_INACTIVE'
      : assinatura?.status === 'ATIVA' || assinatura?.status === 'TESTE'
        ? 'ACTIVE'
        : usuario.statusConta
    const updated = normalizarUsuarioSessao({
      ...usuario,
      plano: assinatura?.planoNome || usuario.plano,
      assinatura,
      statusConta,
    }, usuario)
    if (updated) {
      salvarUsuarioSessao(updated)
      setUsuario(updated)
    }
    return assinatura
  }

  async function logout(motivo = 'manual') {
    console.log('[auth-debug] logout executado')
    transicaoSessaoRef.current = true
    setSessionExpired(motivo === 'session_invalidated')
    if (!isMeuGendazPath()) {
      await appApi.logout().catch(() => {})
    }
    if (motivo === 'manual') {
      setSessionExpired(false)
    }
    limparSessaoUsuario()
    clearLocalData()
    setUsuario(null)
  }


  async function adminLogin(email, senha) {
    transicaoSessaoRef.current = false
    setSessionExpired(false)
    const response = await adminApi.login(email, senha)
    adminSessionTokenMemory = response.token || null
    setAdminUsuario(response.admin)
    setAuthLoading(false)
    return response.admin
  }

  function adminLogout() {
    adminApi.logout()
    limparSessaoAdmin()
    setAdminUsuario(null)
  }

  function iniciarImpersonacao(payload) {
    const adminAtual = adminUsuarioMemory
    const impersonationData = {
      ...payload,
      admin: adminAtual,
      modoProxy: true,
    }
    impersonationMemory = impersonationData
    salvarImpersonationPersistida(impersonationData)
    setImpersonation(impersonationData)

    const usuarioImpersonado = {
      id: payload?.usuarioId,
      perfil: 'DONO',
      empresaId: payload?.empresaId,
      nome: payload?.usuarioNome || payload?.nome,
      email: payload?.usuarioEmail || payload?.email,
      plano: payload?.plano,
      statusConta: 'ACTIVE',
      impersonadoPorAdmin: true,
    }
    salvarUsuarioSessao(usuarioImpersonado)
    setUsuario(usuarioImpersonado)
  }

  async function encerrarImpersonacao() {
    const contexto = impersonationMemory || lerImpersonationPersistida()
    if (contexto?.sessionId) {
      try {
        await adminApi.encerrarImpersonacao(contexto.sessionId)
      } catch {
        // mantem o encerramento local mesmo se o backend falhar
      }
    }
    limparSessaoUsuario()
    clearLocalData()
    impersonationMemory = null
    salvarImpersonationPersistida(null)
    setImpersonation(null)
    setUsuario(null)
  }

  function getPagamentoPendente() {
    return pendingPaymentMemory
  }

  function limparPagamentoPendente() {
    pendingPaymentMemory = null
  }

  function atualizarUsuario(partial) {
    const updated = updateCurrentUser(partial)
    if (updated) setUsuario(updated)
  }

  const value = useMemo(() => ({
    usuario,
    authLoading,
    sessionExpired,
    login,
    criarConta,
    logout,
    atualizarUsuario,
    atualizarPlanoAtual,
    renovarAoRetomarAba,
    getPagamentoPendente,
    limparPagamentoPendente,
    adminUsuario,
    adminLogin,
    adminLogout,
    impersonation,
    iniciarImpersonacao,
    encerrarImpersonacao,
  }), [usuario, authLoading, adminUsuario, impersonation, sessionExpired])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}



