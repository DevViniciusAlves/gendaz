import { useCallback, useEffect, useRef, useState } from 'react'
import { buscarClientesAdmin } from '../api/adminCrmApi.js'

export function useAdminCrmData() {
  const [clientes, setClientes] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [filtros, setFiltros] = useState({
    segment: 'todos',
    search: '',
    orderBy: 'recente',
    period: 30,
  })
  const debounceRef = useRef(null)

  const carregar = useCallback(async (filtrosParam) => {
    setLoading(true)
    setError(null)
    try {
      const data = await buscarClientesAdmin(filtrosParam || filtros)
      setClientes(data.empresas || [])
    } catch (err) {
      setError(err)
      setClientes([])
    } finally {
      setLoading(false)
    }
  }, [filtros])

  useEffect(() => {
    carregar(filtros)
  }, [])

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      carregar(filtros)
    }, 300)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [filtros.segment, filtros.search, filtros.orderBy, filtros.period])

  const atualizarFiltros = useCallback((novosFiltros) => {
    setFiltros((prev) => ({ ...prev, ...novosFiltros }))
  }, [])

  const limparFiltros = useCallback(() => {
    setFiltros({ segment: 'todos', search: '', orderBy: 'recente', period: 30 })
  }, [])

  return { clientes, loading, error, filtros, atualizarFiltros, limparFiltros, recarregar: carregar }
}
