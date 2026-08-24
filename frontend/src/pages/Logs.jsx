import { useEffect, useState } from 'react'
import { Search, Loader } from 'lucide-react'
import Table from '../components/Table.jsx'
import Pagination from '../components/Pagination.jsx'
import { logsApi } from '../api/logsApi.js'

const ITENS_POR_PAGINA = 20

function formatarDataHora(iso) {
  if (!iso) return '-----'
  const data = new Date(iso)
  if (Number.isNaN(data.getTime())) return '-----'
  return data.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function Logs() {
  const [itens, setItens] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [termo, setTermo] = useState('')
  const [pagina, setPagina] = useState(0)
  const [totalPaginas, setTotalPaginas] = useState(1)
  const [totalItens, setTotalItens] = useState(0)

  useEffect(() => {
    let cancelado = false
    async function buscar() {
      setCarregando(true)
      setErro(null)
      try {
        const data = await logsApi.listar({
          termo: termo.trim() || undefined,
          page: pagina,
          size: ITENS_POR_PAGINA,
        })
        if (cancelado) return
        setItens(data.content || [])
        setTotalPaginas(data.totalPages || 1)
        setTotalItens(data.totalElements || 0)
      } catch (err) {
        if (cancelado) return
        if (err.response?.status === 401) {
          window.dispatchEvent(new CustomEvent('meu-gendaz:logout'))
          return
        }
        setErro('Nao foi possivel carregar os logs. Tente novamente.')
        setItens([])
      } finally {
        if (!cancelado) setCarregando(false)
      }
    }
    const tempo = setTimeout(buscar, termo !== undefined ? 300 : 0)
    return () => {
      cancelado = true
      clearTimeout(tempo)
    }
  }, [termo, pagina])

  function aoMudarPagina(paginaUmBase) {
    setPagina(paginaUmBase - 1)
  }

  return (
    <section className="page reports-page">
      <div className="page-title">
        <span className="section-kicker">Auditoria</span>
        <h1>Logs de atividade</h1>
        <p>Registro de todas as acoes realizadas na sua empresa. Somente leitura.</p>
      </div>

      <div className="panel report-filters">
        <label className="field report-filter-field" style={{ flex: 1 }}>
          <span>Buscar</span>
          <span className="search-input">
            <Search size={16} />
            <input
              maxLength={80}
              placeholder="Buscar por usuário ou ação"
              value={termo}
              onChange={(e) => {
                setPagina(0)
                setTermo(e.target.value)
              }}
            />
          </span>
          <small className="field-hint">Filtra por nome do usuário ou descrição da ação.</small>
        </label>
      </div>

      {erro && <div className="gendaz-erro">{erro}</div>}

      <section className="panel">
        <div className="panel-head">
          <h2>Atividades</h2>
        </div>

        {carregando ? (
          <div className="gendaz-loading">
            <Loader size={20} /> Carregando logs...
          </div>
        ) : (
          <Table
            columns={[
              {
                key: 'nomeUsuario',
                label: 'USUÁRIO',
                render: (row) => (
                  <div className="name-cell">
                    <div className="avatar">{(row.nomeUsuario || 'SG').substring(0, 2).toUpperCase()}</div>
                    <div className="name-cell-info">
                      <strong>{row.nomeUsuario || 'Sistema'}</strong>
                    </div>
                  </div>
                ),
              },
              { key: 'acao', label: 'AÇÃO' },
              {
                key: 'dataHora',
                label: 'DATA / HORA',
                render: (row) => <span className="report-center-cell">{formatarDataHora(row.dataHora)}</span>,
              },
            ]}
            rows={itens}
            empty="Nenhuma atividade registrada ainda."
          />
        )}

        <Pagination
          page={pagina + 1}
          totalPages={totalPaginas}
          totalItems={totalItens}
          pageSize={ITENS_POR_PAGINA}
          onPageChange={aoMudarPagina}
        />
      </section>
    </section>
  )
}
