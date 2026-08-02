import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { appApi } from '../api/appApi.js'
import { adminApi } from '../api/adminApi.js'
import { clearLocalData, updateCurrentUser } from '../services/localStore.js'
import { getSessionUser, setSessionUser } from '../api/axiosConfig.js'

const AuthContext = createContext(null)
let pendingPaymentMemory = null
let adminUsuarioMemory = null
let impersonationMemory = null

function limparSessaoUsuario() {
  setSessionUser(null)
}

function limparSessaoAdmin() {
  adminUsuarioMemory = null
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

function emitirToast(type, message) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('agendapro:toast', {
    detail: { type, message },
  }))
}

export function AuthProvider({ children }) {
  const [authLoading, setAuthLoading] = useState(true)
  const [usuario, setUsuario] = useState(() => getSessionUser())
  const [impersonation, setImpersonation] = useState(() => impersonationMemory)
  const [adminUsuario, setAdminUsuario] = useState(() => adminUsuarioMemory)
  const refreshEmAndamentoRef = useRef(null)
  const ultimaRenovacaoBemSucedidaRef = useRef(0)

  async function renovarAoRetomarAba({ ignorarThrottle = false } = {}) {
    if (!usuario?.id || usuario?.perfil === 'SUPER_ADMIN' || adminUsuario) return true
    if (contaInativa(usuario)) return true
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
        const mensagem = String(error?.response?.data?.mensagem || error?.response?.data?.message || '').toLowerCase()
        if (statusConta === 'ACCOUNT_INACTIVE'
          || mensagem.includes('conta inativa')
          || mensagem.includes('periodo gratuito terminou')
          || mensagem.includes('mensalidade')) {
          const atualizado = normalizarUsuarioSessao(
            {
              ...usuario,
              statusConta: 'ACCOUNT_INACTIVE',
            },
            usuario,
          )
          salvarUsuarioSessao(atualizado)
          setUsuario(atualizado)
          throw new Error('Sua conta encontra-se inativa. Regularize a mensalidade para continuar usando o gendaz.')
        }
        if (error?.response?.status === 401 && mensagemAcessoOutroDispositivo(error)) {
          emitirToast('warning', 'Sua conta foi acessada em outro dispositivo, mas esta sessÃ£o continua ativa.')
          console.warn('[auth-debug] renovacao ao retomar aba detectou outro dispositivo, mantendo sessao local', {
            status: error.response?.status,
            mensagem,
          })
          return true
        }
        if (error?.response?.status === 401) {
          window.dispatchEvent(new Event('agendeasy:session-expired'))
          throw new Error('Sua sessÃ£o expirou. Por favor, recarregue a página.')
        }
        if (!erroTemporarioAutenticacao(error)) {
          console.warn('[auth-debug] renovacao ao retomar aba falhou com erro fatal', {
            status: error.response?.status,
            mensagem: error.response?.data?.mensagem || error.response?.data?.message || error.message,
          })
        } else {
          console.warn('[auth-debug] renovacao ao retomar aba ignorou erro temporario', {
            status: error.response?.status,
            mensagem: error.response?.data?.mensagem || error.response?.data?.message || error.message,
          })
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
      if (!adminUsuario) {
        try {
          const adminRefresh = await adminApi.refresh()
          if (adminRefresh?.admin?.perfil === 'SUPER_ADMIN') {
            adminUsuarioMemory = adminRefresh.admin
            if (mounted) setAdminUsuario(adminRefresh.admin)
            if (mounted) setAuthLoading(false)
            return
          }
        } catch {
          // cai para o fluxo normal
        }
      }
      if (adminUsuario) {
        if (mounted) setAuthLoading(false)
        return
      }
      if (usuario?.perfil === 'SUPER_ADMIN') {
        if (mounted) setAuthLoading(false)
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
        const mensagem = String(error.response?.data?.mensagem || error.response?.data?.message || '').toLowerCase()
        const contaMarcadaInativa = statusConta === 'ACCOUNT_INACTIVE'
          || mensagem.includes('conta inativa')
          || mensagem.includes('periodo gratuito terminou')
          || mensagem.includes('mensalidade')
        const falhaFatal = status === 401
          || mensagem.includes('sessao foi encerrada')
          || mensagem.includes('sessão foi encerrada')
          || mensagem.includes('usuario autenticado invalido')
          || mensagem.includes('usuário autenticado inválido')
          || mensagem.includes('sessao expirada')
          || mensagem.includes('sessão expirada')
        if (contaMarcadaInativa) {
          const atualizado = normalizarUsuarioSessao(
            {
              ...usuario,
              statusConta: 'ACCOUNT_INACTIVE',
            },
            usuario,
          )
          salvarUsuarioSessao(atualizado)
          setUsuario(atualizado)
        } else if (status === 401 && mensagemAcessoOutroDispositivo(error)) {
          emitirToast('warning', 'Sua conta foi acessada em outro dispositivo, mas esta sessão continua ativa.')
          console.warn('[auth-debug] refresh inicial detectou outro dispositivo, mantendo sessao local', {
            status,
            mensagem,
          })
        } else if (falhaFatal) {
          limparSessaoUsuario()
          clearLocalData()
          setUsuario(null)
        } else {
          console.warn('[auth-debug] refresh inicial ignorou erro temporario', {
            status,
            mensagem,
          })
        }
      } finally {
        if (mounted) setAuthLoading(false)
      }
    }

    validarSessaoInicial()
    return () => {
      mounted = false
    }
  }, [adminUsuario])

  useEffect(() => {
    function marcarContaIndisponivel() {
      if (!usuario) return
      const atualizado = { ...usuario, statusConta: 'ACCOUNT_INACTIVE' }
      salvarUsuarioSessao(atualizado)
      setUsuario(atualizado)
    }
    function encerrarSessaoRemota() {
      logout('refresh_failed')
    }
    async function renovarAoRetomarAba() {
      if (!usuario?.id || usuario?.perfil === 'SUPER_ADMIN' || adminUsuario) return
      if (contaInativa(usuario)) return
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
              }
            : null,
          usuario,
        )
        salvarUsuarioSessao(updated)
        setUsuario(updated)
        ultimaRenovacaoBemSucedidaRef.current = Date.now()
      } catch (error) {
        const statusConta = error?.response?.data?.statusConta
        const mensagem = String(error?.response?.data?.mensagem || error?.response?.data?.message || '').toLowerCase()
        if (statusConta === 'ACCOUNT_INACTIVE'
          || mensagem.includes('conta inativa')
          || mensagem.includes('periodo gratuito terminou')
          || mensagem.includes('mensalidade')) {
          const atualizado = normalizarUsuarioSessao(
            {
              ...usuario,
              statusConta: 'ACCOUNT_INACTIVE',
            },
            usuario,
          )
          salvarUsuarioSessao(atualizado)
          setUsuario(atualizado)
          return
        }
        if (error?.response?.status === 401 && mensagemAcessoOutroDispositivo(error)) {
          emitirToast('warning', 'Sua conta foi acessada em outro dispositivo, mas esta sessão continua ativa.')
          console.warn('[auth-debug] renovacao ao retomar aba detectou outro dispositivo, mantendo sessao local', {
            status: error.response?.status,
            mensagem,
          })
          return
        }
        if (!erroTemporarioAutenticacao(error)) {
          console.warn('[auth-debug] renovacao ao retomar aba falhou com erro fatal', {
            status: error.response?.status,
            mensagem: error.response?.data?.mensagem || error.response?.data?.message || error.message,
          })
        } else {
          console.warn('[auth-debug] renovacao ao retomar aba ignorou erro temporario', {
            status: error.response?.status,
            mensagem: error.response?.data?.mensagem || error.response?.data?.message || error.message,
          })
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

  function logout(motivo = 'manual') {
    console.log('[auth-debug] logout executado', { motivo })
    appApi.logout().catch(() => {})
    limparSessaoUsuario()
    clearLocalData()
    setUsuario(null)
  }

  async function adminLogin(email, senha) {
    const response = await adminApi.login(email, senha)
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
    setImpersonation(impersonationData)
  }

  async function encerrarImpersonacao() {
    impersonationMemory = null
    setImpersonation(null)
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
  }), [usuario, authLoading, adminUsuario, impersonation])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}



