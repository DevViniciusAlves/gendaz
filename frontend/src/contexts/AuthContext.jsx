import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { appApi } from '../api/appApi.js'
import { adminApi } from '../api/adminApi.js'
import { clearLocalData, PLANOS, updateCurrentUser } from '../services/localStore.js'

const AuthContext = createContext(null)
const PENDING_PAYMENT_KEY = 'agendeasy_pagamento_pendente'
const IMPERSONATION_KEY = 'agendeasy_admin_impersonation'

function limparSessaoUsuario() {
  localStorage.removeItem('agendapro_usuario')
}

function limparSessaoAdmin() {
  localStorage.removeItem('agendeasy_admin_user')
}

function salvarUsuarioSessao(usuario) {
  localStorage.setItem('agendapro_usuario', JSON.stringify(usuario))
  return usuario
}

function normalizarUsuarioSessao(usuarioBase, fallbackAtual) {
  const usuario = usuarioBase || fallbackAtual
  if (!usuario) return null
  return {
    ...usuario,
    plano: usuario?.plano || fallbackAtual?.plano || 'BASICO',
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

export function AuthProvider({ children }) {
  const [authLoading, setAuthLoading] = useState(() => Boolean(localStorage.getItem('agendapro_usuario')))
  const [usuario, setUsuario] = useState(() => {
    const saved = localStorage.getItem('agendapro_usuario')
    if (!saved) return null

    const parsed = JSON.parse(saved)
    if (parsed?.perfil === 'SUPER_ADMIN' || !PLANOS[parsed?.plano]) {
      limparSessaoUsuario()
      return null
    }

    return parsed
  })
  const [impersonation, setImpersonation] = useState(() => JSON.parse(localStorage.getItem(IMPERSONATION_KEY) || 'null'))
  const [adminUsuario, setAdminUsuario] = useState(() => {
    return JSON.parse(localStorage.getItem('agendeasy_admin_user') || 'null')
  })
  const refreshEmAndamentoRef = useRef(null)
  const ultimaRenovacaoBemSucedidaRef = useRef(0)

  useEffect(() => {
    let mounted = true

    async function validarSessaoInicial() {
      if (!usuario?.id || usuario?.perfil === 'SUPER_ADMIN' || adminUsuario) {
        if (mounted) setAuthLoading(false)
        return
      }
      if (contaInativa(usuario)) {
        if (mounted) setAuthLoading(false)
        return
      }
      try {
        const refresh = await appApi.refreshSession()
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
          || mensagem.includes('sessÃ£o foi encerrada')
          || mensagem.includes('usuario autenticado invalido')
          || mensagem.includes('usuÃ¡rio autenticado invÃ¡lido')
          || mensagem.includes('sessao expirada')
          || mensagem.includes('sessÃ£o expirada')
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
  }, [adminUsuario, usuario?.id, usuario?.perfil])

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
        const refresh = await appApi.refreshSession()
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
      localStorage.removeItem(PENDING_PAYMENT_KEY)
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
      localStorage.setItem(PENDING_PAYMENT_KEY, JSON.stringify(pending))
      return { pendingPayment: true, ...pending }
    }
    if (response.statusConta === 'ACCOUNT_INACTIVE') {
      const userInativo = {
        ...response.usuario,
        plano: response.assinatura?.planoNome || response.usuario?.plano || 'BASICO',
        assinatura: response.assinatura,
        statusConta: 'ACCOUNT_INACTIVE',
      }
      clearLocalData()
      localStorage.removeItem(PENDING_PAYMENT_KEY)
      salvarUsuarioSessao(userInativo)
      setUsuario(userInativo)
      return userInativo
    }
    const user = response.usuario
    const plano = response.assinatura?.planoNome || user.plano || 'BASICO'
    const usuarioComPlano = { ...user, plano, assinatura: response.assinatura, statusConta: response.statusConta || 'ACTIVE' }
    clearLocalData()
    localStorage.removeItem(PENDING_PAYMENT_KEY)
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
      localStorage.setItem(PENDING_PAYMENT_KEY, JSON.stringify(pending))
      setUsuario(null)
      return { pendingPayment: true, ...pending }
    }
    const user = response.usuario
    const usuarioComPlano = {
      ...user,
      plano: response.assinatura?.planoNome || 'BASICO',
      assinatura: response.assinatura,
      pagamentoPlano: response.pagamentoPlano,
      statusConta: response.statusConta || 'ACTIVE',
    }
    clearLocalData()
    localStorage.removeItem(PENDING_PAYMENT_KEY)
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
    const updated = updateCurrentUser({
      plano: assinatura?.planoNome || usuario.plano,
      assinatura,
      statusConta,
    })
    if (updated) setUsuario(updated)
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
    return response.admin
  }

  function adminLogout() {
    adminApi.logout()
    limparSessaoAdmin()
    setAdminUsuario(null)
  }

  function iniciarImpersonacao(payload) {
    const adminAtual = JSON.parse(localStorage.getItem('agendeasy_admin_user') || 'null')
    const impersonationData = { ...payload, admin: adminAtual }
    const usuarioImpersonado = {
      id: payload?.usuarioId || payload?.empresaId,
      nome: payload?.usuarioNome || adminAtual?.nome || 'Super Admin',
      email: payload?.usuarioEmail || adminAtual?.email,
      perfil: 'DONO',
      plano: payload?.plano || payload?.planoNome || 'BASICO',
      empresaId: payload.empresaId,
      empresaNome: payload.empresa,
      impersonadoPorAdmin: true,
    }
    clearLocalData()
    localStorage.setItem(IMPERSONATION_KEY, JSON.stringify(impersonationData))
    localStorage.setItem('agendapro_usuario', JSON.stringify(usuarioImpersonado))
    setImpersonation(impersonationData)
    setUsuario(usuarioImpersonado)
  }

  async function encerrarImpersonacao() {
    if (impersonation?.sessionId) {
      try {
        await adminApi.encerrarImpersonacao(impersonation.sessionId)
      } catch {
        // ignora erro da API — limpeza deve acontecer mesmo assim
      }
    }
    localStorage.removeItem(IMPERSONATION_KEY)
    limparSessaoUsuario()
    clearLocalData()
    setImpersonation(null)
    setUsuario(null)
  }

  function getPagamentoPendente() {
    return JSON.parse(localStorage.getItem(PENDING_PAYMENT_KEY) || 'null')
  }

  function limparPagamentoPendente() {
    localStorage.removeItem(PENDING_PAYMENT_KEY)
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

