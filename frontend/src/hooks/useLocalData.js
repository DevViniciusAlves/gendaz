import { useEffect, useRef, useState } from 'react'
import { appApi } from '../api/appApi.js'
import { modoDemo } from '../api/axiosConfig.js'
import { emptyData, getData, setData } from '../services/localStore.js'

const cacheLocal = new Map()
const cacheEmAndamento = new Map()
const CACHE_TTL_MS = 24 * 60 * 60 * 1000
const CACHE_PREFIX = 'agendapro_scope_cache_'
const POLLING_INTERVAL_MS = 30000

function chaveCache(scope) {
  const usuario = JSON.parse(localStorage.getItem('agendapro_usuario') || 'null')
  return `${scope}:${usuario?.empresaId || 'local'}:${usuario?.id || 'anon'}`
}

function salvarCacheSession(cacheKey, payload) {
  try {
    localStorage.setItem(`${CACHE_PREFIX}${cacheKey}`, JSON.stringify({
      time: Date.now(),
      data: payload,
    }))
  } catch {
    return
  }
}

function lerCacheSession(cacheKey) {
  try {
    const raw = localStorage.getItem(`${CACHE_PREFIX}${cacheKey}`)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed?.data || !parsed?.time) return null
    return parsed
  } catch {
    return null
  }
}

function cacheValido(cache) {
  return Boolean(cache && Date.now() - cache.time < CACHE_TTL_MS)
}

function cacheDoEscopo(scope) {
  const cacheKey = chaveCache(scope)
  return cacheLocal.get(cacheKey) || lerCacheSession(cacheKey)
}

function salvarCache(scope, payload) {
  const cacheKey = chaveCache(scope)
  const entry = { data: payload, time: Date.now() }
  cacheLocal.set(cacheKey, entry)
  salvarCacheSession(cacheKey, payload)
  return payload
}

async function carregarComCache(scope, force = false) {
  const cacheKey = chaveCache(scope)
  const cached = cacheDoEscopo(scope)
  if (!force && cacheValido(cached)) {
    cacheLocal.set(cacheKey, cached)
    return cached.data
  }

  if (cacheEmAndamento.has(cacheKey)) {
    return cacheEmAndamento.get(cacheKey)
  }

  const promise = appApi.carregarDados(scope)
    .then((remote) => salvarCache(scope, remote))
    .finally(() => {
      cacheEmAndamento.delete(cacheKey)
    })

  cacheEmAndamento.set(cacheKey, promise)
  return promise
}

export async function prefetchLocalData(scope = 'full') {
  if (modoDemo) return getData()
  return carregarComCache(scope)
}

export function useLocalData(scope = 'full') {
  const cacheInicial = !modoDemo ? cacheDoEscopo(scope) : null
  const [data, setStateData] = useState(() => {
    if (modoDemo) return getData()
    const usuario = JSON.parse(localStorage.getItem('agendapro_usuario') || 'null')
    if (cacheValido(cacheInicial)) {
      cacheLocal.set(chaveCache(scope), cacheInicial)
      return cacheInicial.data
    }
    return emptyData(usuario)
  })
  const loadedOnceRef = useRef(Boolean(modoDemo || cacheValido(cacheInicial)))
  const [loading, setLoading] = useState(!modoDemo && !cacheValido(cacheInicial))
  const [error, setError] = useState(null)

  async function reload(force = false) {
    if (modoDemo) {
      setStateData(getData())
      setLoading(false)
      return
    }

    const cacheKey = chaveCache(scope)
    const cached = cacheDoEscopo(scope)
    if (!force && cacheValido(cached)) {
      cacheLocal.set(cacheKey, cached)
      setStateData(cached.data)
      setError(null)
      setLoading(false)
      loadedOnceRef.current = true
      return
    }

    try {
      if (!loadedOnceRef.current) {
        setLoading(true)
      }
      const remote = await carregarComCache(scope, force)
      setStateData(remote)
      setError(null)
      loadedOnceRef.current = true
    } catch (err) {
      setError(err)
    } finally {
      if (!loadedOnceRef.current) {
        setLoading(false)
      } else {
        setLoading(false)
      }
    }
  }

  useEffect(() => {
    reload()
    function reloadFromEvent() {
      reload(true)
    }
    const timer = setInterval(() => {
      reload(true)
    }, POLLING_INTERVAL_MS)
    window.addEventListener('agendapro:data-changed', reloadFromEvent)
    return () => {
      clearInterval(timer)
      window.removeEventListener('agendapro:data-changed', reloadFromEvent)
    }
  }, [scope])

  function updateData(updater) {
    if (!modoDemo) return

    const current = getData()
    const next = typeof updater === 'function' ? updater(current) : updater
    setData(next)
    setStateData(next)
  }

  return [data, updateData, { loading, error, reload }]
}
